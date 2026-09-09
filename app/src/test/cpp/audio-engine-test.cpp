#include "audio-engine.h"
#include <cassert>
#include <iostream>
#include <limits>
#include <set>
#include <vector>

using namespace neko_dsp;

static std::vector<int16_t> signal(int rate,int channels,int frames,int amplitude=12000) {
    std::vector<int16_t> samples(frames*channels);
    for (int i=0;i<frames;++i) for (int ch=0;ch<channels;++ch) {
        samples[i*channels+ch]=static_cast<int16_t>(amplitude*std::sin(2*M_PI*997*i/rate+ch*.4));
    }
    return samples;
}

static int longestPeakPlateau(const std::vector<int16_t>& samples,int threshold) {
    int longest=0,run=0;
    int16_t previous=0;
    for (const auto sample:samples) {
        if (std::abs(static_cast<int>(sample))>=threshold && sample==previous) ++run;
        else run=1;
        longest=std::max(longest,run);
        previous=sample;
    }
    return longest;
}

int main() {
    for (int rate : {8000,22050,44100,48000,96000,192000}) {
        for (int channels : {1,2}) {
            AudioEngine engine(rate,channels);
            auto dry=signal(rate,channels,rate/5);
            auto output=dry;
            engine.process(output.data(),output.size());
            assert(output==dry);
            std::set<std::vector<int16_t>> distinct;
            for (int id=15;id<=34;++id) {
                EffectSettings s;
                s.masteringId=id;
                engine.configure(s);
                engine.reset();
                output=dry;
                engine.process(output.data(),output.size());
                assert(output!=dry);
                assert(std::any_of(output.begin(),output.end(),[](int16_t x){return x!=0;}));
                const int limit=static_cast<int>(std::ceil(dbToGain(signaturePresets[id-15].ceiling)*32768));
                for (auto x:output) assert(std::abs(static_cast<int>(x))<=limit);
                distinct.insert(output);

                auto hot=signal(rate,channels,rate/5,32000);
                engine.reset();
                engine.process(hot.data(),hot.size());
                const int safetyLimit=static_cast<int>(std::ceil(dbToGain(-1.f)*32768));
                for (auto x:hot) assert(std::abs(static_cast<int>(x))<=safetyLimit);
                assert(longestPeakPlateau(hot,safetyLimit-1)<=2);

                auto chunked=dry;
                engine.reset();
                for (size_t offset=0;offset<chunked.size();) {
                    size_t count=std::min(static_cast<size_t>(channels*127),chunked.size()-offset);
                    engine.process(chunked.data()+offset,count);
                    offset+=count;
                }
                assert(output==chunked);
                s.masteringMix=0;
                engine.configure(s);
                output=dry;
                engine.process(output.data(),output.size());
                assert(output==dry);
            }
            assert(distinct.size()==20);
            EffectSettings extreme;
            extreme.eq.fill(15);
            extreme.bass=extreme.width=extreme.wet=extreme.loudness=100;
            extreme.room=100; extreme.damping=0;
            engine.configure(extreme);
            output=dry;
            engine.process(output.data(),output.size());
            const int safetyLimit=static_cast<int>(std::ceil(dbToGain(-1.f)*32768));
            for (auto x:output) assert(std::abs(static_cast<int>(x))<=safetyLimit);
            engine.reset();
            std::vector<int16_t> silence(channels*4096);
            engine.process(silence.data(),silence.size());
            assert(std::all_of(silence.begin(),silence.end(),[](int16_t x){return x==0;}));
        }
    }
    float previous=-1.f;
    for (int i=-800;i<=800;++i) {
        const float value=smoothSaturate(i*.01f);
        assert(std::isfinite(value));
        assert(value>=previous);
        assert(std::abs(value)<1.f);
        previous=value;
    }
    assert(std::abs(smoothSaturate(-3.f)+smoothSaturate(3.f))<1e-6f);
    Biquad eq;
    eq.setPeakingEQ(44100,1000,1,0);
    for(int i=0;i<10000;++i) {
        const float x=std::sin(i*.1f);
        assert(std::abs(eq.process(x)-x)<1e-5f);
    }
    for (float gain : {-15.f,15.f}) {
        eq.reset(); eq.setPeakingEQ(44100,1000,1,gain);
        for (int i=0;i<44100;++i) {
            const float y=eq.process(i==0?1.f:0.f);
            assert(std::isfinite(y));
            if (i>40000) assert(std::abs(y)<1e-6f);
        }
    }
    AudioEngine engine(44100,2);
    std::vector<int16_t> allValues(65536);
    for (int i=0;i<65536;++i) allValues[i]=static_cast<int16_t>(i-32768);
    auto output=allValues;
    engine.process(output.data(),output.size());
    assert(output==allValues);
    bool rejected=false;
    try { engine.process(output.data(),3); } catch(const std::invalid_argument&) { rejected=true; }
    assert(rejected);
    EffectSettings invalid;
    invalid.eq.fill(std::numeric_limits<float>::quiet_NaN());
    engine.configure(invalid);
    output=allValues;
    engine.process(output.data(),output.size());
    assert(output==allValues);
    rejected=false;
    invalid.masteringId=1;
    try { engine.configure(invalid); } catch(const std::invalid_argument&) { rejected=true; }
    assert(rejected);
    std::cout << "DSP checks passed: 20 presets, 6 rates, mono/stereo, chunk invariance, exact bypass, reset, bounds\n";
}
