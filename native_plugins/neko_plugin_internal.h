#ifndef NEKO_PLUGIN_INTERNAL_H
#define NEKO_PLUGIN_INTERNAL_H

#include "quickjs.h"
#include <chrono>
#include <cstddef>
#include <string>

namespace neko {

JSValue random_hex(JSContext *context, JSValueConst receiver, int argc,
                   JSValueConst *argv);
bool encode_json(JSContext *context, JSValueConst value, bool ok,
                 std::chrono::steady_clock::time_point deadline,
                 std::string &response);
const char *eval_utf8(void *handle, const char *source, size_t length);

}

#endif
