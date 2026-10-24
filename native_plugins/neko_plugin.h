#ifndef NEKO_PLUGIN_H
#define NEKO_PLUGIN_H

#if defined(_WIN32)
#define NEKO_PLUGIN_API __declspec(dllexport)
#else
#define NEKO_PLUGIN_API __attribute__((visibility("default")))
#endif

#ifdef __cplusplus
extern "C" {
#endif

NEKO_PLUGIN_API void *neko_plugin_create(void);
NEKO_PLUGIN_API const char *neko_plugin_eval(void *handle, const char *source);
NEKO_PLUGIN_API void neko_plugin_close(void *handle);

#ifdef __cplusplus
}
#endif

#endif
