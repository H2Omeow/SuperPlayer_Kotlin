#include <jni.h>
#include <android/log.h>
#include <vector>
#include <mutex>
#include "audio-dsp.h"

#define LOG_TAG "NekoAudioFX"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace neko_dsp;

// 全局状态
static std::mutex g_mutex;
static bool g_initialized = false;
static int g_sampleRate = 44100;

// 10 段均衡器（每声道独立）
static std::vector<Biquad> g_eqBandsL(10);
static std::vector<Biquad> g_eqBandsR(10);
static std::vector<float> g_eqGains(10, 0.f);  // dB

// 低音增强（低架式滤波器）
static Biquad g_bassL, g_bassR;
static int g_bassBoost = 0;  // 0-100

// 3D 环绕（M/S 处理 + 立体声加宽）
static int g_virtualizer = 0;  // 0-100

// Schroeder 混响（简化版 - 4个 comb + 2个 allpass）
struct ReverbState {
    std::vector<float> combBuffers[4];
    std::vector<float> allpassBuffers[2];
    size_t combIdx[4] = {0};
    size_t allpassIdx[2] = {0};
    float combFeedback[4] = {0.84f, 0.8f, 0.76f, 0.72f};
    float allpassGain = 0.7f;
};
static ReverbState g_reverbL, g_reverbR;
static int g_reverbWet = 0;  // 0-100

// 响度增益
static int g_loudnessGain = 0;  // 0-100

extern "C" {

JNIEXPORT jboolean JNICALL
Java_top_nekoh2o_player_audio_NativeAudioEffectsController_nativeInit(
        JNIEnv* env, jobject, jint sampleRate) {
    std::lock_guard<std::mutex> lock(g_mutex);

    g_sampleRate = sampleRate;

    // 初始化 10 段 EQ 频率
    const float frequencies[] = {32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
    for (int i = 0; i < 10; ++i) {
        g_eqBandsL[i].setPeakingEQ(sampleRate, frequencies[i], 1.0f, 0.f);
        g_eqBandsR[i].setPeakingEQ(sampleRate, frequencies[i], 1.0f, 0.f);
    }

    // 初始化混响延迟线（典型 Schroeder 延迟时间）
    const int combDelays[] = {1557, 1617, 1491, 1422};  // samples at 44.1kHz
    const int allpassDelays[] = {225, 556};

    for (int i = 0; i < 4; ++i) {
        g_reverbL.combBuffers[i].resize(combDelays[i], 0.f);
        g_reverbR.combBuffers[i].resize(combDelays[i], 0.f);
    }
    for (int i = 0; i < 2; ++i) {
        g_reverbL.allpassBuffers[i].resize(allpassDelays[i], 0.f);
        g_reverbR.allpassBuffers[i].resize(allpassDelays[i], 0.f);
    }

    g_initialized = true;
    LOGI("Native 音效引擎初始化成功，采样率 %d Hz", sampleRate);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_top_nekoh2o_player_audio_NativeAudioEffectsController_nativeSetEqBands(
        JNIEnv* env, jobject, jfloatArray bands) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_initialized) return;

    jfloat* bandsArray = env->GetFloatArrayElements(bands, nullptr);
    jsize len = env->GetArrayLength(bands);

    const float frequencies[] = {32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
    for (int i = 0; i < std::min((int)len, 10); ++i) {
        g_eqGains[i] = bandsArray[i];
        g_eqBandsL[i].setPeakingEQ(g_sampleRate, frequencies[i], 1.0f, bandsArray[i]);
        g_eqBandsR[i].setPeakingEQ(g_sampleRate, frequencies[i], 1.0f, bandsArray[i]);
    }

    env->ReleaseFloatArrayElements(bands, bandsArray, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_top_nekoh2o_player_audio_NativeAudioEffectsController_nativeSetBassBoost(
        JNIEnv*, jobject, jint strength) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_bassBoost = strength;

    // 0-100 映射到 0-12dB 增益
    float gainDb = (strength / 100.f) * 12.f;
    g_bassL.setLowShelf(g_sampleRate, 80.f, gainDb);
    g_bassR.setLowShelf(g_sampleRate, 80.f, gainDb);
}

JNIEXPORT void JNICALL
Java_top_nekoh2o_player_audio_NativeAudioEffectsController_nativeSetVirtualizer(
        JNIEnv*, jobject, jint strength) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_virtualizer = strength;
}

JNIEXPORT void JNICALL
Java_top_nekoh2o_player_audio_NativeAudioEffectsController_nativeSetReverbWet(
        JNIEnv*, jobject, jint wet) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_reverbWet = wet;
}

JNIEXPORT void JNICALL
Java_top_nekoh2o_player_audio_NativeAudioEffectsController_nativeSetLoudnessGain(
        JNIEnv*, jobject, jint gain) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_loudnessGain = gain;
}

// 核心处理函数：实时 PCM 处理
JNIEXPORT void JNICALL
Java_top_nekoh2o_player_audio_NativeAudioEffectsController_nativeProcessSamples(
        JNIEnv* env, jobject, jshortArray samples) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_initialized) return;

    jshort* samplesArray = env->GetShortArrayElements(samples, nullptr);
    jsize len = env->GetArrayLength(samples);

    // short → float，按 [L,R,L,R...] 交错处理
    for (int i = 0; i < len; i += 2) {
        float L = samplesArray[i] / 32768.f;
        float R = samplesArray[i+1] / 32768.f;

        // 1. 10段均衡器
        for (int b = 0; b < 10; ++b) {
            L = g_eqBandsL[b].process(L);
            R = g_eqBandsR[b].process(R);
        }

        // 2. 低音增强
        if (g_bassBoost > 0) {
            L = g_bassL.process(L);
            R = g_bassR.process(R);
        }

        // 3. 3D 环绕（M/S 加宽）
        if (g_virtualizer > 0) {
            float width = g_virtualizer / 100.f;
            float mid = (L + R) * 0.5f;
            float side = (L - R) * 0.5f * (1.f + width);
            L = mid + side;
            R = mid - side;
        }

        // 4. 混响（简化：仅 comb 滤波器）
        if (g_reverbWet > 0) {
            float reverbL = 0.f, reverbR = 0.f;
            float wetMix = g_reverbWet / 100.f * 0.5f;

            for (int c = 0; c < 4; ++c) {
                auto& bufL = g_reverbL.combBuffers[c];
                auto& bufR = g_reverbR.combBuffers[c];
                size_t& idxL = g_reverbL.combIdx[c];
                size_t& idxR = g_reverbR.combIdx[c];

                float delayedL = bufL[idxL];
                float delayedR = bufR[idxR];

                bufL[idxL] = L + delayedL * g_reverbL.combFeedback[c];
                bufR[idxR] = R + delayedR * g_reverbR.combFeedback[c];

                reverbL += delayedL;
                reverbR += delayedR;

                idxL = (idxL + 1) % bufL.size();
                idxR = (idxR + 1) % bufR.size();
            }

            L = L * (1.f - wetMix) + reverbL * wetMix * 0.25f;
            R = R * (1.f - wetMix) + reverbR * wetMix * 0.25f;
        }

        // 5. 响度增益
        if (g_loudnessGain > 0) {
            float gain = 1.f + (g_loudnessGain / 100.f) * 0.5f;
            L *= gain;
            R *= gain;
        }

        // 防爆音裁剪 + float → short
        L = clampf(L, -1.f, 1.f);
        R = clampf(R, -1.f, 1.f);

        samplesArray[i] = (jshort)(L * 32767.f);
        samplesArray[i+1] = (jshort)(R * 32767.f);
    }

    env->ReleaseShortArrayElements(samples, samplesArray, 0);
}

JNIEXPORT void JNICALL
Java_top_nekoh2o_player_audio_NativeAudioEffectsController_nativeRelease(
        JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);

    for (auto& band : g_eqBandsL) band.reset();
    for (auto& band : g_eqBandsR) band.reset();
    g_bassL.reset();
    g_bassR.reset();

    // 清空混响延迟线
    for (int i = 0; i < 4; ++i) {
        std::fill(g_reverbL.combBuffers[i].begin(), g_reverbL.combBuffers[i].end(), 0.f);
        std::fill(g_reverbR.combBuffers[i].begin(), g_reverbR.combBuffers[i].end(), 0.f);
        g_reverbL.combIdx[i] = 0;
        g_reverbR.combIdx[i] = 0;
    }

    g_initialized = false;
    LOGI("Native 音效引擎已释放");
}

} // extern "C"
