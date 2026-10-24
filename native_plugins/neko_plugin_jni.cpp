#include "neko_plugin.h"
#include "neko_plugin_internal.h"

#include <jni.h>
#include <cstdint>
#include <memory>
#include <string>

namespace {

std::string source_utf8(const jchar *characters, jsize length) {
  std::string result;
  for (jsize index = 0; index < length; ++index) {
    uint32_t character = characters[index];
    if (character >= 0xd800 && character <= 0xdbff && index + 1 < length &&
        characters[index + 1] >= 0xdc00 && characters[index + 1] <= 0xdfff) {
      character = 0x10000 + ((character - 0xd800) << 10) +
                  (characters[++index] - 0xdc00);
    } else if (character >= 0xd800 && character <= 0xdfff) {
      character = 0xfffd;
    }
    if (character < 0x80) {
      result += static_cast<char>(character);
    } else if (character < 0x800) {
      result += static_cast<char>(0xc0 | (character >> 6));
      result += static_cast<char>(0x80 | (character & 0x3f));
    } else if (character < 0x10000) {
      result += static_cast<char>(0xe0 | (character >> 12));
      result += static_cast<char>(0x80 | ((character >> 6) & 0x3f));
      result += static_cast<char>(0x80 | (character & 0x3f));
    } else {
      result += static_cast<char>(0xf0 | (character >> 18));
      result += static_cast<char>(0x80 | ((character >> 12) & 0x3f));
      result += static_cast<char>(0x80 | ((character >> 6) & 0x3f));
      result += static_cast<char>(0x80 | (character & 0x3f));
    }
  }
  return result;
}

}

extern "C" JNIEXPORT jlong JNICALL
Java_top_nekoh2o_player_data_plugins_NativePluginVm_create(JNIEnv *, jobject) {
  return static_cast<jlong>(reinterpret_cast<intptr_t>(neko_plugin_create()));
}

extern "C" JNIEXPORT jstring JNICALL
Java_top_nekoh2o_player_data_plugins_NativePluginVm_eval(
    JNIEnv *environment, jobject, jlong handle, jstring script) {
  if (!script) return environment->NewStringUTF("{\"ok\":false,\"error\":\"source is null\"}");
  const jchar *characters = environment->GetStringChars(script, nullptr);
  if (!characters) return nullptr;
  auto release = [environment, script](const jchar *buffer) {
    environment->ReleaseStringChars(script, buffer);
  };
  std::unique_ptr<const jchar, decltype(release)> owned(characters, release);
  try {
    const std::string source = source_utf8(characters, environment->GetStringLength(script));
    return environment->NewStringUTF(neko::eval_utf8(
        reinterpret_cast<void *>(static_cast<intptr_t>(handle)), source.data(), source.size()));
  } catch (...) {
    return environment->NewStringUTF("{\"ok\":false,\"error\":\"out of memory\"}");
  }
}

extern "C" JNIEXPORT void JNICALL
Java_top_nekoh2o_player_data_plugins_NativePluginVm_close(
    JNIEnv *, jobject, jlong handle) {
  neko_plugin_close(reinterpret_cast<void *>(static_cast<intptr_t>(handle)));
}
