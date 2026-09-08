#include <jni.h>
#include "audio-engine.h"

using neko_dsp::AudioEngine;

namespace {
void throwJava(JNIEnv* env,const char* message) {
    if (env->ExceptionCheck()) return;
    jclass type=env->FindClass("java/lang/IllegalStateException");
    if (type) env->ThrowNew(type,message);
}
AudioEngine& engine(jlong handle) {
    if (!handle) throw std::invalid_argument("Null DSP handle");
    return *reinterpret_cast<AudioEngine*>(handle);
}
}

extern "C" {
JNIEXPORT jlong JNICALL
Java_top_nekoh2o_player_audio_NativeAudioEffectsController_nativeCreate(
        JNIEnv* env,jobject,jint rate,jint channels) {
    try {
        if (rate<8000 || rate>384000 || channels<1 || channels>2)
            throw std::invalid_argument("Unsupported PCM format");
        return reinterpret_cast<jlong>(new AudioEngine(rate,channels));
    } catch (const std::exception& error) { throwJava(env,error.what()); return 0; }
}

JNIEXPORT void JNICALL
Java_top_nekoh2o_player_audio_NativeAudioEffectsController_nativeConfigure(
        JNIEnv* env,jobject,jlong handle,jfloatArray bands,jint bass,jint width,jint wet,
        jint room,jint damping,jint loudness,jint masteringId,jint masteringMix) {
    try {
        if (!bands || env->GetArrayLength(bands)!=10) throw std::invalid_argument("Expected ten EQ gains");
        neko_dsp::EffectSettings settings;
        env->GetFloatArrayRegion(bands,0,10,settings.eq.data());
        if (env->ExceptionCheck()) return;
        settings.bass=bass; settings.width=width; settings.wet=wet;
        settings.room=room; settings.damping=damping; settings.loudness=loudness;
        settings.masteringId=masteringId; settings.masteringMix=masteringMix;
        engine(handle).configure(settings);
    } catch (const std::exception& error) { throwJava(env,error.what()); }
}

JNIEXPORT void JNICALL
Java_top_nekoh2o_player_audio_NativeAudioEffectsController_nativeProcess(
        JNIEnv* env,jobject,jlong handle,jobject buffer,jint count) {
    try {
        if (!buffer) throw std::invalid_argument("Null PCM buffer");
        const auto capacity=env->GetDirectBufferCapacity(buffer);
        auto* samples=static_cast<int16_t*>(env->GetDirectBufferAddress(buffer));
        if (!samples || count<0 || static_cast<jlong>(count)*2>capacity)
            throw std::invalid_argument("Invalid direct PCM buffer");
        engine(handle).process(samples,count);
    } catch (const std::exception& error) { throwJava(env,error.what()); }
}

JNIEXPORT void JNICALL
Java_top_nekoh2o_player_audio_NativeAudioEffectsController_nativeReset(JNIEnv* env,jobject,jlong handle) {
    try { engine(handle).reset(); }
    catch (const std::exception& error) { throwJava(env,error.what()); }
}

JNIEXPORT void JNICALL
Java_top_nekoh2o_player_audio_NativeAudioEffectsController_nativeRelease(JNIEnv*,jobject,jlong handle) {
    delete reinterpret_cast<AudioEngine*>(handle);
}
}
