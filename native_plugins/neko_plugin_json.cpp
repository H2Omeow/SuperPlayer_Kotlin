#include "neko_plugin_internal.h"

#include <cstdint>
#include <memory>

bool neko::encode_json(JSContext *context, JSValueConst value, bool ok,
                       std::chrono::steady_clock::time_point deadline,
                       std::string &response) {
  size_t length = 0;
  const uint16_t *text = JS_ToCStringLenUTF16(context, &length, value);
  if (!text) return false;
  auto release = [context](const uint16_t *buffer) {
    JS_FreeCStringUTF16(context, buffer);
  };
  std::unique_ptr<const uint16_t, decltype(release)> owned(text, release);
  std::string result = ok ? "{\"ok\":true,\"value\":\"" : "{\"ok\":false,\"error\":\"";
  constexpr char digits[] = "0123456789abcdef";
  for (size_t index = 0; index < length; ++index) {
    if ((index & 4095) == 0 && std::chrono::steady_clock::now() >= deadline)
      return false;
    const uint16_t character = text[index];
    if (character == '"' || character == '\\') {
      result += '\\';
      result += static_cast<char>(character);
    } else if (character >= 0x20 && character <= 0x7e) {
      result += static_cast<char>(character);
    } else {
      result += "\\u";
      result += digits[(character >> 12) & 15];
      result += digits[(character >> 8) & 15];
      result += digits[(character >> 4) & 15];
      result += digits[character & 15];
    }
  }
  result += "\"}";
  response = std::move(result);
  return true;
}
