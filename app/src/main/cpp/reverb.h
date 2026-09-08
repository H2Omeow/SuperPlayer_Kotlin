#pragma once

#include <array>
#include <vector>
#include <algorithm>

namespace neko_dsp {
class Reverb {
    struct Delay {
        std::vector<float> samples;
        size_t index = 0;
        float filtered = 0;
    };
    std::array<Delay,4> combs;
    std::array<Delay,2> allpasses;
public:
    Reverb(int sampleRate, int offset) {
        const int combLengths[] = {1557,1617,1491,1422};
        const int allpassLengths[] = {225,556};
        for (int i=0;i<4;++i) combs[i].samples.resize(std::max(1,(combLengths[i]+offset)*sampleRate/44100));
        for (int i=0;i<2;++i) allpasses[i].samples.resize(std::max(1,(allpassLengths[i]+offset)*sampleRate/44100));
    }
    void reset() {
        for (auto* list : {&combs[0],&combs[1],&combs[2],&combs[3],&allpasses[0],&allpasses[1]}) {
            std::fill(list->samples.begin(),list->samples.end(),0);
            list->index=0;
            list->filtered=0;
        }
    }
    float process(float input, float room, float damping) {
        float output=0;
        for (auto& d : combs) {
            float delayed=d.samples[d.index];
            d.filtered=delayed*(1-damping*.9f)+d.filtered*damping*.9f;
            d.samples[d.index]=input+d.filtered*(.55f+room*.4f);
            d.index=(d.index+1)%d.samples.size();
            output+=delayed*.25f;
        }
        for (auto& d : allpasses) {
            const float delayed=d.samples[d.index];
            d.samples[d.index]=output+delayed*.5f;
            output=delayed-output*.5f;
            d.index=(d.index+1)%d.samples.size();
        }
        return output;
    }
};
}
