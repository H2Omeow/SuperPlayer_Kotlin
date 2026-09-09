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

class LinkedPeakLimiter {
    float ceiling = 1.f;
    float release = 0.f;
    float gain = 1.f;

public:
    void configure(float fs, float ceilingDb, float releaseMs) {
        ceiling = dbToGain(ceilingDb);
        release = std::exp(-1.f / std::max(1.f, fs * releaseMs * .001f));
        gain = 1.f;
    }

    void reset() { gain = 1.f; }

    void process(float& left, float& right) {
        const float peak = std::max(std::abs(left), std::abs(right));
        const float target = std::min(1.f, ceiling / std::max(peak, 1e-9f));
        // Without lookahead, attack must be immediate to avoid falling back to hard clipping.
        gain = target < gain ? target : release * gain + (1.f - release) * target;
        left = clampf(left * gain, -ceiling, ceiling);
        right = clampf(right * gain, -ceiling, ceiling);
    }
};

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
        a1 = b1;
        a2 = (1.f - alpha / A) / a0;
    }

    // Low Shelf
    void setLowShelf(float fs, float freq, float gainDb, float q = 0.707f) {
        freq = std::clamp(freq, 10.f, fs * 0.48f);
        float A = std::pow(10.f, gainDb / 40.f);
        float w0 = 2.f * M_PI * freq / fs;
        float cosw0 = std::cos(w0);
        float sinw0 = std::sin(w0);
        float alpha = sinw0 / (2.f * std::max(q, 0.1f));

        float a0 = (A+1) + (A-1)*cosw0 + 2*std::sqrt(A)*alpha;
        b0 = (A*((A+1) - (A-1)*cosw0 + 2*std::sqrt(A)*alpha)) / a0;
        b1 = (2*A*((A-1) - (A+1)*cosw0)) / a0;
        b2 = (A*((A+1) - (A-1)*cosw0 - 2*std::sqrt(A)*alpha)) / a0;
        a1 = (-2*((A-1) + (A+1)*cosw0)) / a0;
        a2 = ((A+1) + (A-1)*cosw0 - 2*std::sqrt(A)*alpha) / a0;
    }

    void setHighShelf(float fs, float freq, float gainDb, float q = 0.707f) {
        freq = std::clamp(freq, 10.f, fs * 0.48f);
        const float a = std::pow(10.f, gainDb / 40.f);
        const float w = 2.f * M_PI * freq / fs, c = std::cos(w);
        const float t = std::sqrt(a) * std::sin(w) / std::max(q, 0.1f);
        const float d = (a+1) - (a-1)*c + t;
        b0 = a*((a+1)+(a-1)*c+t)/d;
        b1 = -2*a*((a-1)+(a+1)*c)/d;
        b2 = a*((a+1)+(a-1)*c-t)/d;
        a1 = 2*((a-1)-(a+1)*c)/d;
        a2 = ((a+1)-(a-1)*c-t)/d;
    }

    void setPass(float fs, float freq, float q, bool high) {
        freq = std::clamp(freq, 10.f, fs * 0.48f);
        const float w = 2.f*M_PI*freq/fs, c = std::cos(w);
        const float alpha = std::sin(w)/(2.f*q), d = 1.f+alpha;
        b0 = (high ? 1.f+c : 1.f-c)/(2.f*d);
        b1 = (high ? -2.f : 2.f)*b0;
        b2 = b0;
        a1 = -2.f*c/d;
        a2 = (1.f-alpha)/d;
    }
};

} // namespace neko_dsp

#endif // AUDIO_DSP_H
