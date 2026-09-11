#include "audio-engine.h"
#include <cassert>
#include <iostream>
#include <limits>
#include <vector>

using namespace neko_dsp;

// Remove the fitted fundamental and DC; the residual includes harmonics and noise.
static double distortion(const std::vector<float>& samples, int rate, int hz) {
    double sine=0, cosine=0, dc=0, energy=0;
    const int start=rate;
    const int count=static_cast<int>(samples.size())-start;
    for (int i=start;i<static_cast<int>(samples.size());++i) {
        const double x=samples[i], phase=2*M_PI*hz*i/rate;
        sine+=x*std::sin(phase); cosine+=x*std::cos(phase);
        dc+=x; energy+=x*x;
    }
    const double fundamental=2*(sine*sine+cosine*cosine)/count;
    if (!std::isfinite(energy) || !std::isfinite(fundamental) || fundamental<=0)
        return std::numeric_limits<double>::infinity();
    return std::sqrt(std::max(0.,energy-fundamental-dc*dc/count)/fundamental);
}

int main() {
    bool passed=true;
    auto check=[&](bool ok,const char* label,double value) {
        std::cout << (ok ? "PASS " : "FAIL ") << label << ": " << value << '\n';
        passed &= ok;
    };
    constexpr int rate=48000;
    LinkedPeakLimiter limiter;
    limiter.configure(rate,-1,80);
    float left=.8f,right=-.4f;
    limiter.process(left,right);
    check(left==.8f && right==-.4f,"limiter below-ceiling transparency",left);

    for (int hz : {40,80,997}) {
        limiter.reset();
        std::vector<float> samples(rate*2);
        for (int i=0;i<rate*2;++i) {
            left=2*std::sin(2*M_PI*hz*i/rate); right=left*.25f;
            limiter.process(left,right);
            assert(std::abs(left)<=dbToGain(-1.f)+1e-6f);
            assert(std::abs(right-left*.25f)<1e-6f);
            samples[i]=left;
        }
        const double residual=distortion(samples,rate,hz);
        check(residual<.001,"overloaded limiter THD+N",residual);
    }
    for (int i=0;i<rate;++i) { left=right=0; limiter.process(left,right); }
    left=.1f; right=-.1f; limiter.process(left,right);
    check(std::abs(left-.1f)<1e-5f,"limiter recovers after silence",left);
    limiter.reset();
    left=.8f; right=-.4f; limiter.process(left,right);
    check(left==.8f && right==-.4f,"limiter reset clears held reduction",left);

    for (int id=15;id<=34;++id) {
        AudioEngine engine(rate,2);
        EffectSettings settings;
        settings.masteringId=id;
        engine.configure(settings);
        std::vector<int16_t> silence(rate*2);
        engine.process(silence.data(),silence.size());
        int peak=0;
        for (int16_t x:silence) peak=std::max(peak,std::abs(static_cast<int>(x)));
        std::cout << "preset " << id << ' ';
        check(peak==0,"silent input output peak",peak);
    }

    for (int hz : {40,80,1000}) {
        AudioEngine engine(rate,1);
        EffectSettings settings;
        settings.eq.fill(15);
        settings.bass=100;
        engine.configure(settings);
        std::vector<int16_t> pcm(rate*2);
        for (int i=0;i<rate*2;++i) pcm[i]=std::lrint(32000*std::sin(2*M_PI*hz*i/rate));
        engine.process(pcm.data(),pcm.size());
        std::vector<float> samples(pcm.begin(),pcm.end());
        const double residual=distortion(samples,rate,hz);
        check(residual<.002,"boosted EQ and bass THD+N",residual);
    }

    // Representative shipped curves: rock, deep bass, bass-heavy and AI tuning.
    const std::array<float,10> curves[] = {
        {5,4,2,0,-1,-1,0,2,4,5}, {10,8,4,2,0,-2,-2,-1,0,1},
        {12,10,6,3,1,-1,-2,-2,0,2}, {2,3,1,-1,-1,0,1,2,3,2}
    };
    for (int sampleRate : {44100,48000}) for (const auto& curve:curves) {
        double worst=0;
        for (int hz : {40,80,1000,8000}) {
            AudioEngine engine(sampleRate,1);
            EffectSettings settings; settings.eq=curve;
            engine.configure(settings);
            std::vector<int16_t> pcm(sampleRate*2);
            for (int i=0;i<sampleRate*2;++i) pcm[i]=std::lrint(32000*std::sin(2*M_PI*hz*i/sampleRate));
            engine.process(pcm.data(),pcm.size());
            const double residual=distortion(std::vector<float>(pcm.begin(),pcm.end()),sampleRate,hz);
            if (residual>=.002) std::cout << "curve failure rate=" << sampleRate << " hz=" << hz << '\n';
            worst=std::max(worst,residual);
        }
        check(worst<.002,"shipped EQ curve worst THD+N",worst);
    }
    return passed ? 0 : 1;
}
