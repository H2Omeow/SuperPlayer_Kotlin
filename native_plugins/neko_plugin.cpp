#include "neko_plugin.h"
#include "neko_plugin_internal.h"

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

namespace {

using Clock = std::chrono::steady_clock;
static_assert(Clock::is_steady, "Plugin deadlines require a monotonic clock");
constexpr size_t kMemoryLimit = 32 * 1024 * 1024;
constexpr size_t kStackLimit = 512 * 1024;
constexpr size_t kJobLimit = 1024;
constexpr char kOutOfMemory[] = "{\"ok\":false,\"error\":\"out of memory\"}";
constexpr char kTimeout[] = "{\"ok\":false,\"error\":\"eval exceeded 750 ms\"}";
constexpr char kJobLimitError[] = "{\"ok\":false,\"error\":\"pending promise jobs exceeded 1024\"}";
constexpr char kPendingError[] = "{\"ok\":false,\"error\":\"VM has pending jobs after a failed eval; close and recreate it\"}";

struct Value {
  JSContext *context;
  JSValue value;

  Value(JSContext *context, JSValue value) : context(context), value(value) {}
  Value(const Value &) = delete;
  Value &operator=(const Value &) = delete;
  ~Value() { JS_FreeValue(context, value); }
};

struct Rejection {
  JSValue promise;
  JSValue reason;
};

struct Vm {
  JSRuntime *runtime = nullptr;
  JSContext *context = nullptr;
  std::mutex mutex;
  std::string response;
  std::vector<Rejection> rejections;
  Clock::time_point deadline;
  const char *terminal_error = nullptr;
  bool active = false;
  bool timed_out = false;
  bool host_oom = false;

  void clear_rejections() {
    for (const Rejection &rejection : rejections) {
      JS_FreeValue(context, rejection.promise);
      JS_FreeValue(context, rejection.reason);
    }
    rejections.clear();
  }

  ~Vm() {
    if (runtime) {
      JS_SetHostPromiseRejectionTracker(runtime, nullptr, nullptr);
      clear_rejections();
      if (context) JS_FreeContext(context);
      JS_FreeRuntime(runtime);
    }
  }
};

int interrupt(JSRuntime *, void *opaque) {
  auto &vm = *static_cast<Vm *>(opaque);
  if (vm.active && Clock::now() >= vm.deadline) vm.timed_out = true;
  return vm.timed_out;
}

void track_rejection(JSContext *context, JSValueConst promise,
                     JSValueConst reason, bool handled, void *opaque) {
  auto &vm = *static_cast<Vm *>(opaque);
  if (handled) {
    auto found = std::find_if(vm.rejections.begin(), vm.rejections.end(),
        [promise](const Rejection &entry) {
          return JS_VALUE_GET_PTR(entry.promise) == JS_VALUE_GET_PTR(promise);
        });
    if (found != vm.rejections.end()) {
      JS_FreeValue(context, found->promise);
      JS_FreeValue(context, found->reason);
      vm.rejections.erase(found);
    }
    return;
  }
  try {
    vm.rejections.push_back({promise, reason});
    JS_DupValue(context, promise);
    JS_DupValue(context, reason);
  } catch (...) {
    vm.host_oom = true;
  }
}

bool encode_response(Vm &vm, JSContext *context, JSValueConst value, bool ok) {
  return neko::encode_json(context, value, ok, vm.deadline, vm.response);
}

const char *capture_exception(Vm &vm, JSContext *context) {
  Value exception(context, JS_GetException(context));
  if (vm.timed_out) return kTimeout;
  if (encode_response(vm, context, exception.value, false)) return vm.response.c_str();
  Value conversion_error(context, JS_GetException(context));
  return vm.timed_out ? kTimeout :
      "{\"ok\":false,\"error\":\"exception could not be converted to a string\"}";
}

const char *drain_jobs(Vm &vm, size_t &jobs) {
  while (JS_IsJobPending(vm.runtime)) {
    if (interrupt(vm.runtime, &vm)) return kTimeout;
    if (jobs >= kJobLimit) return kJobLimitError;
    ++jobs;
    JSContext *job_context = nullptr;
    if (JS_ExecutePendingJob(vm.runtime, &job_context) < 0)
      return capture_exception(vm, job_context ? job_context : vm.context);
  }
  return interrupt(vm.runtime, &vm) ? kTimeout : nullptr;
}

const char *evaluate(Vm &vm, const char *source, size_t length) {
  Value result(vm.context, JS_Eval(vm.context, source, length,
                                 "<plugin>", JS_EVAL_TYPE_GLOBAL));
  if (JS_IsException(result.value)) return capture_exception(vm, vm.context);
  size_t jobs = 0;
  if (const char *error = drain_jobs(vm, jobs)) return error;
  if (!vm.rejections.empty()) {
    if (encode_response(vm, vm.context, vm.rejections.front().reason, false))
      return vm.response.c_str();
    return capture_exception(vm, vm.context);
  }
  if (!encode_response(vm, vm.context, result.value, true))
    return capture_exception(vm, vm.context);
  if (const char *error = drain_jobs(vm, jobs)) return error;
  if (!vm.rejections.empty()) {
    if (!encode_response(vm, vm.context, vm.rejections.front().reason, false))
      return capture_exception(vm, vm.context);
  }
  return vm.host_oom ? kOutOfMemory : vm.response.c_str();
}

}

void *neko_plugin_create(void) {
  try {
    auto vm = std::make_unique<Vm>();
    vm->runtime = JS_NewRuntime();
    if (!vm->runtime) return nullptr;
    JS_SetMemoryLimit(vm->runtime, kMemoryLimit);
    JS_SetMaxStackSize(vm->runtime, kStackLimit);
    JS_SetCanBlock(vm->runtime, false);
    vm->context = JS_NewContext(vm->runtime);
    if (!vm->context) return nullptr;
    JS_SetInterruptHandler(vm->runtime, interrupt, vm.get());
    JS_SetHostPromiseRejectionTracker(vm->runtime, track_rejection, vm.get());
    Value global(vm->context, JS_GetGlobalObject(vm->context));
    if (JS_SetPropertyStr(vm->context, global.value, "__nekoRandomHex",
          JS_NewCFunction(vm->context, neko::random_hex, "__nekoRandomHex", 1)) < 0)
      return nullptr;
    return vm.release();
  } catch (...) {
    return nullptr;
  }
}

const char *neko_plugin_eval(void *handle, const char *source) {
  return neko::eval_utf8(handle, source, source ? std::strlen(source) : 0);
}

const char *neko::eval_utf8(void *handle, const char *source, size_t length) {
  if (!handle) return "{\"ok\":false,\"error\":\"invalid VM handle\"}";
  auto &vm = *static_cast<Vm *>(handle);
  try {
    std::lock_guard<std::mutex> lock(vm.mutex);
    if (vm.terminal_error) return vm.terminal_error;
    if (!source) return "{\"ok\":false,\"error\":\"source is null\"}";
    vm.clear_rejections();
    vm.deadline = Clock::now() + std::chrono::milliseconds(750);
    vm.timed_out = false;
    vm.host_oom = false;
    vm.active = true;
    JS_UpdateStackTop(vm.runtime);
    const char *response;
    try {
      response = evaluate(vm, source, length);
    } catch (...) {
      response = kOutOfMemory;
    }
    if (interrupt(vm.runtime, &vm)) response = kTimeout;
    vm.active = false;
    if (JS_IsJobPending(vm.runtime)) vm.terminal_error = kPendingError;
    vm.clear_rejections();
    return response;
  } catch (...) {
    return kOutOfMemory;
  }
}

void neko_plugin_close(void *handle) {
  if (!handle) return;
  auto *vm = static_cast<Vm *>(handle);
  {
    std::lock_guard<std::mutex> lock(vm->mutex);
    vm->active = false;
    JS_UpdateStackTop(vm->runtime);
  }
  delete vm;
}
