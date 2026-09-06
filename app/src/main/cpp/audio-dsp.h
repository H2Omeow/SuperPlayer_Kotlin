#ifndef AUDIO_DSP_H
#define AUDIO_DSP_H

#include <cmath>
#include <algorithm>

namespace neko_dsp {

// 数值安全工具
inline float clampf(float v, float lo, float hi) {
    if (std::isnan(v)) return 0.f;
    if (std::isinf(v)) return v < 0.f ? lo : hi;
    return v < lo ? lo : (v > hi ? hi : v);
}

inline float dbToGain(float db) {
    return std::pow(10.f, db / 20.f);
}

// Biquad 滤波器（Transposed Direct Form II）
struct Biquad {
    float b0 = 1.f, b1 = 0.f, b2 = 0.f;
    float a1 = 0.f, a2 = 0.f;
    float z1 = 0.f, z2 = 0.f;

    inline float process(float in) {
        float out = b0 * in + z1;
        z1 = b1 * in - a1 * out + z2;
        z2 = b2 * in - a2 * out;
        return out;
    }

    void reset() { z1 = z2 = 0.f; }

    // RBJ Cookbook Peaking EQ
    void setPeakingEQ(float fs, float freq, float q, float gainDb) {
        freq = std::clamp(freq, 10.f, fs * 0.48f);
        q = std::max(q, 0.1f);

        float A = std::pow(10.f, gainDb / 40.f);
        float w0 = 2.f * M_PI * freq / fs;
        float alpha = std::sin(w0) / (2.f * q);

        float a0 = 1.f + alpha / A;
        b0 = (1.f + alpha * A) / a0;
        b1 = (-2.f * std::cos(w0)) / a0;
        b2 = (1.f - alpha * A) / a0;
        a1 = -b1;
        a2 = (1.f - alpha / A) / a0;
    }

    // Low Shelf
    void setLowShelf(float fs, float freq, float gainDb) {
        freq = std::clamp(freq, 10.f, fs * 0.48f);
        float A = std::pow(10.f, gainDb / 40.f);
        float w0 = 2.f * M_PI * freq / fs;
        float cosw0 = std::cos(w0);
        float sinw0 = std::sin(w0);
        float alpha = sinw0 / (2.f * 0.707f);

        float a0 = (A+1) + (A-1)*cosw0 + 2*std::sqrt(A)*alpha;
        b0 = (A*((A+1) - (A-1)*cosw0 + 2*std::sqrt(A)*alpha)) / a0;
        b1 = (2*A*((A-1) - (A+1)*cosw0)) / a0;
        b2 = (A*((A+1) - (A-1)*cosw0 - 2*std::sqrt(A)*alpha)) / a0;
        a1 = (-2*((A-1) + (A+1)*cosw0)) / a0;
        a2 = ((A+1) + (A-1)*cosw0 - 2*std::sqrt(A)*alpha) / a0;
    }
};

} // namespace neko_dsp

#endif // AUDIO_DSP_H
