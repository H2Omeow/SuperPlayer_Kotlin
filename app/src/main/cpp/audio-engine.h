#pragma once

#include <array>
#include <cstdint>
#include <stdexcept>
#include "audio-dsp.h"
#include "reverb.h"
#include "signature-chain.h"

namespace neko_dsp {
struct EffectSettings {
    std::array<float,10> eq{};
    int bass=0, width=0, wet=0, room=50, damping=30, loudness=0;
    int masteringId=0, masteringMix=100;
};

class AudioEngine {
    int rate, channels;
    EffectSettings settings;
    Biquad eq[2][10], bass[2];
    Reverb reverbL, reverbR;
    SignatureChain mastering;
    float loudnessGain=1;

public:
    AudioEngine(int sampleRate,int channelCount) : rate(sampleRate), channels(channelCount),
        reverbL(sampleRate,0), reverbR(sampleRate,23) {
        configure({});
    }
    void configure(EffectSettings next) {
        if (next.masteringId != 0 && (next.masteringId < 15 || next.masteringId > 34))
            throw std::invalid_argument("Requested mastering chain is not implemented");
        auto percent=[](int x){return std::clamp(x,0,100);};
        next.bass=percent(next.bass); next.width=percent(next.width);
        next.wet=percent(next.wet); next.room=percent(next.room);
        next.damping=percent(next.damping); next.loudness=percent(next.loudness);
        next.masteringMix=percent(next.masteringMix);
        const float frequencies[]={32,64,125,250,500,1000,2000,4000,8000,16000};
        for (int i=0;i<10;++i) {
            next.eq[i]=clampf(next.eq[i],-15,15);
            if (next.eq[i]!=settings.eq[i]) for (auto& channel:eq) {
                channel[i].reset();
                channel[i].setPeakingEQ(rate,frequencies[i],1,next.eq[i]);
            }
        }
        if (next.bass!=settings.bass) for (auto& filter:bass) {
            filter.reset();
            filter.setLowShelf(rate,80,next.bass*.12f);
        }
        if (next.masteringId && next.masteringId!=settings.masteringId)
            mastering.configure(next.masteringId,rate);
        if (next.wet!=settings.wet && next.wet==0) { reverbL.reset(); reverbR.reset(); }
        loudnessGain=dbToGain(next.loudness*.06f);
        settings=next;
    }
    void reset() {
        for (auto& channel:eq) for (auto& f:channel) f.reset();
        for (auto& f:bass) f.reset();
        reverbL.reset(); reverbR.reset();
        if (settings.masteringId) mastering.configure(settings.masteringId,rate);
    }
    void process(int16_t* samples,int count) {
        if (count<0 || count%channels) throw std::invalid_argument("Incomplete PCM frame");
        for (int i=0;i<count;i+=channels) {
            float l=samples[i]/32768.f, r=channels==2?samples[i+1]/32768.f:l;
            for (int b=0;b<10;++b) if (settings.eq[b]!=0) {
                l=eq[0][b].process(l); if (channels==2) r=eq[1][b].process(r);
            }
            if (settings.bass) {
                l=bass[0].process(l); if (channels==2) r=bass[1].process(r);
            }
            if (channels==1) r=l;
            if (settings.masteringId && settings.masteringMix) {
                float ml=l,mr=r;
                mastering.process(ml,mr,channels);
                const float mix=settings.masteringMix*.01f;
                l+=(ml-l)*mix; r+=(mr-r)*mix;
            }
            const float mid=(l+r)*.5f, side=(l-r)*.5f*(1+settings.width*.01f);
            l=mid+side; r=mid-side;
            if (settings.wet) {
                const float wet=settings.wet*.005f, room=settings.room*.01f, damping=settings.damping*.01f;
                const float rl=reverbL.process(l,room,damping);
                const float rr=channels==2?reverbR.process(r,room,damping):rl;
                l=l*(1-wet)+rl*wet; r=r*(1-wet)+rr*wet;
            }
            samples[i]=static_cast<int16_t>(std::lrint(clampf(l*loudnessGain,-1,32767.f/32768)*32768));
            if (channels==2) samples[i+1]=static_cast<int16_t>(std::lrint(clampf(r*loudnessGain,-1,32767.f/32768)*32768));
        }
    }
};
}
