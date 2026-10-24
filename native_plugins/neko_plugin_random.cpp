#include "neko_plugin_internal.h"

#include <cmath>
#include <cstdint>
#include <vector>

#if defined(_WIN32)
#include <windows.h>
#include <bcrypt.h>
#else
#include <cerrno>
#include <fcntl.h>
#include <unistd.h>
#if defined(__linux__) || defined(__ANDROID__)
#include <linux/random.h>
#include <sys/syscall.h>
#endif
#endif

namespace {

bool secure_random(uint8_t *buffer, size_t length) {
#if defined(_WIN32)
  return BCryptGenRandom(nullptr, buffer, static_cast<ULONG>(length),
                         BCRYPT_USE_SYSTEM_PREFERRED_RNG) == 0;
#else
  size_t offset = 0;
#if (defined(__linux__) || defined(__ANDROID__)) && defined(SYS_getrandom)
  while (offset < length) {
    const long count = syscall(SYS_getrandom, buffer + offset, length - offset,
                               GRND_NONBLOCK);
    if (count > 0) offset += static_cast<size_t>(count);
    else if (count < 0 && errno == EINTR) continue;
    else break;
  }
  if (offset == length) return true;
#endif
  int descriptor;
  do {
    descriptor = open("/dev/urandom", O_RDONLY | O_CLOEXEC);
  } while (descriptor < 0 && errno == EINTR);
  if (descriptor < 0) return false;
  bool success = true;
  while (offset < length) {
    const ssize_t count = read(descriptor, buffer + offset, length - offset);
    if (count > 0) offset += static_cast<size_t>(count);
    else if (count < 0 && errno == EINTR) continue;
    else {
      success = false;
      break;
    }
  }
  close(descriptor);
  return success;
#endif
}

}

JSValue neko::random_hex(JSContext *context, JSValueConst, int argc,
                         JSValueConst *argv) {
  double requested = 0;
  if (argc != 1 || !JS_IsNumber(argv[0]))
    return JS_ThrowTypeError(context, "length must be an integer byte count");
  if (JS_ToFloat64(context, &requested, argv[0]) < 0) return JS_EXCEPTION;
  if (!std::isfinite(requested) || requested < 0 ||
      requested > 65536 || std::floor(requested) != requested)
    return JS_ThrowRangeError(context, "length must be between 0 and 65536 bytes");
  try {
    const size_t length = static_cast<size_t>(requested);
    if (length == 0) return JS_NewString(context, "");
    std::vector<uint8_t> bytes(length);
    if (!secure_random(bytes.data(), length))
      return JS_ThrowInternalError(context, "system secure random source failed");
    constexpr char digits[] = "0123456789abcdef";
    std::string result(length * 2, '\0');
    for (size_t index = 0; index < length; ++index) {
      result[index * 2] = digits[bytes[index] >> 4];
      result[index * 2 + 1] = digits[bytes[index] & 15];
    }
    return JS_NewStringLen(context, result.data(), result.size());
  } catch (...) {
    return JS_ThrowOutOfMemory(context);
  }
}
