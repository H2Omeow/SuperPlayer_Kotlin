#pragma once

#include "audio-dsp.h"
#include "signature-presets.h"
#include <stdexcept>

namespace neko_dsp {
inline float timeCoefficient(float fs, float ms) {
    return std::exp(-1.f / std::max(1.f, fs * ms * .001f));
}

inline float smoothSaturate(float input) {
    return input / std::sqrt(1.f + input * input);
}

class SignatureChain {
    struct Channel {
        Biquad hp[2], lp[2], eq[3], sidechain;
        float dcInput = 0, dcOutput = 0, envelope = 0;
        float colorDcInput = 0, colorDcOutput = 0;
    } ch[2];
    SignaturePreset p{};
    float dcCoefficient = 0, attack = 0, release = 0;
    float inputGain = 1, makeupGain = 1, colorGain = 1;
    LinkedPeakLimiter limiter;

    float mono(float x, Channel& c) {
        x *= inputGain;
        const float dc = x - c.dcInput + dcCoefficient * c.dcOutput;
        c.dcInput = x;
        c.dcOutput = dc;
        x = dc;
        for (int i = 0; i < p.hpStages; ++i) x = c.hp[i].process(x);
        if (p.lp > 0) for (auto& f : c.lp) x = f.process(x);
        for (auto& f : c.eq) x = f.process(x);
        const float detector = std::abs(p.comp.sidechain > 0 ? c.sidechain.process(x) : x);
        const float coeff = detector > c.envelope ? attack : release;
        c.envelope = coeff*c.envelope + (1-coeff)*detector;
        const float over = 20*std::log10(std::max(c.envelope, 1e-9f)) - p.comp.threshold;
        float reduction = std::max(over, 0.f)*(1.f/p.comp.ratio-1.f);
        if (p.comp.knee > 0 && over > -p.comp.knee*.5f && over < p.comp.knee*.5f) {
            const float blend = (over+p.comp.knee*.5f)/p.comp.knee;
            reduction *= blend;
        }
        x *= (1-p.comp.mix) + dbToGain(reduction)*makeupGain*p.comp.mix;
        if (p.color.type != 0) {
            const float drive=1+p.color.drive*3;
            auto shape=[this](float value) {
                if (p.color.type==1) return std::tanh(value)*.85f;
                if (p.color.type==2) return smoothSaturate(value);
                return clampf(value,-1,1);
            };
            // Drive belongs to the wet branch; compensate it before parallel mixing.
            const float saturated=(shape(x*drive+p.color.bias)-shape(p.color.bias))/drive;
            x=x*(1-p.color.mix)+saturated*p.color.mix*colorGain;
            const float dc=x-c.colorDcInput+dcCoefficient*c.colorDcOutput;
            c.colorDcInput=x;
            c.colorDcOutput=dc;
            x=dc;
        }
        return clampf(x, -32, 32);
    }

public:
    void configure(int id, float fs) {
        if (id < 15 || id > 34) throw std::invalid_argument("Mastering chain unavailable");
        *this = SignatureChain();
        p = signaturePresets[id-15];
        dcCoefficient = std::exp(-2.f*M_PI*15.f/fs);
        attack = timeCoefficient(fs, p.comp.attack);
        release = timeCoefficient(fs, p.comp.release);
        inputGain = dbToGain(p.input);
        makeupGain = dbToGain(p.comp.makeup);
        colorGain = dbToGain(p.color.output);
        limiter.configure(fs, p.ceiling, p.limiterRelease);
        for (auto& c : ch) {
            for (auto& f : c.hp) f.setPass(fs, std::max(p.hp, 10.f), p.hpQ, true);
            for (auto& f : c.lp) f.setPass(fs, std::max(p.lp, 10.f), .5f, false);
            const Tone tones[] = {p.low, p.mid, p.high};
            for (int i = 0; i < 3; ++i) {
                const auto& t = tones[i];
                if (t.type == 1) c.eq[i].setPeakingEQ(fs,t.hz,t.q,t.db);
                else if (t.type == 2 && i == 0) c.eq[i].setLowShelf(fs,t.hz,t.db,t.q);
                else if (t.type == 2) c.eq[i].setHighShelf(fs,t.hz,t.db,t.q);
            }
            c.sidechain.setPass(fs, std::max(p.comp.sidechain, 10.f), .707f, true);
        }
    }

    void process(float& l, float& r, int channels) {
        l = mono(l, ch[0]);
        r = channels == 2 ? mono(r, ch[1]) : l;
        const float mid = (l+r)*.5f, side = (l-r)*.5f*p.width;
        l = mid+side;
        r = mid-side;
        limiter.process(l, r);
    }
};
}
