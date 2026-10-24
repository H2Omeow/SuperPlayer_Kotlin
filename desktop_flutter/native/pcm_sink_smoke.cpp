#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <iostream>
#include <limits>
#include <memory>
#include <stdexcept>
#include <vector>

extern "C" {
void* neko_sink_create(int silent);
void neko_sink_destroy(void* handle);
int neko_sink_write(void* handle, const int16_t* samples, int count);
uint64_t neko_sink_played(void* handle);
int neko_sink_buffered(void* handle);
void neko_sink_pause(void* handle, int paused);
void neko_sink_volume(void* handle, float volume);
int neko_sink_settings(void* handle, const float* values, int count);
int neko_sink_render_test(void* handle, int16_t* output, int frames);
}

namespace {
void require(bool condition, const char* message) {
  if (!condition) throw std::runtime_error(message);
}

using Sink = std::unique_ptr<void, decltype(&neko_sink_destroy)>;

Sink create() {
  Sink sink(neko_sink_create(1), neko_sink_destroy);
  require(sink != nullptr, "Silent PCM sink initialization failed");
  return sink;
}

void check_pcm() {
  auto sink = create();
  std::vector<int16_t> input(100000, 2000);
  std::vector<int16_t> output(96000, -1);
  require(neko_sink_write(sink.get(), input.data(), 3) == -1, "Incomplete stereo frame accepted");
  require(neko_sink_write(sink.get(), input.data(), static_cast<int>(input.size())) == 96000, "Ring capacity mismatch");
  require(neko_sink_write(sink.get(), input.data(), 2) == 0, "Full ring accepted more samples");
  require(neko_sink_render_test(sink.get(), output.data(), 2) == 0, "Paused render failed");
  require(std::all_of(output.begin(), output.begin() + 4, [](int16_t sample) { return sample == 0; }), "Paused audio was not silent");
  require(neko_sink_buffered(sink.get()) == 96000 && neko_sink_played(sink.get()) == 0, "Paused render consumed audio");
  neko_sink_volume(sink.get(), .5f);
  neko_sink_pause(sink.get(), 0);
  require(neko_sink_render_test(sink.get(), output.data(), 48000) == 0, "PCM render failed");
  require(std::all_of(output.begin(), output.end(), [](int16_t sample) { return sample == 1000; }), "PCM volume or channel output mismatch");
  require(neko_sink_buffered(sink.get()) == 0 && neko_sink_played(sink.get()) == 48000, "PCM position accounting failed");
  require(neko_sink_render_test(sink.get(), output.data(), 2) == 0, "Underrun render failed");
  require(std::all_of(output.begin(), output.begin() + 4, [](int16_t sample) { return sample == 0; }), "Underrun was not silent");
  require(neko_sink_played(sink.get()) == 48000, "Underrun advanced the playback position");
  std::array<float, 18> settings{};
  settings[0] = std::numeric_limits<float>::quiet_NaN();
  require(neko_sink_settings(sink.get(), settings.data(), 18) == -1, "Nonfinite effects accepted");
  settings[0] = 0;
  settings[16] = 14;
  require(neko_sink_settings(sink.get(), settings.data(), 18) == -1, "Unimplemented mastering preset accepted");
  require(neko_sink_settings(sink.get(), settings.data(), 17) == -1, "Invalid settings length accepted");
}

void check_mastering() {
  constexpr int frames = 2048;
  std::vector<int16_t> input(frames * 2);
  for (int index = 0; index < frames; index++) {
    input[index * 2] = static_cast<int16_t>(std::sin(index * 440. * 6.283185307179586 / 48000) * 10000);
    input[index * 2 + 1] = static_cast<int16_t>(std::sin(index * 660. * 6.283185307179586 / 48000) * 8000);
  }
  for (int preset = 15; preset <= 34; preset++) {
    auto sink = create();
    std::array<float, 18> settings{};
    settings[13] = 50;
    settings[14] = 30;
    settings[16] = static_cast<float>(preset);
    settings[17] = 100;
    require(neko_sink_settings(sink.get(), settings.data(), 18) == 0, "Implemented mastering preset rejected");
    neko_sink_volume(sink.get(), 1);
    neko_sink_pause(sink.get(), 0);
    require(neko_sink_write(sink.get(), input.data(), static_cast<int>(input.size())) == static_cast<int>(input.size()), "Mastering input was truncated");
    std::vector<int16_t> output(input.size());
    require(neko_sink_render_test(sink.get(), output.data(), frames) == 0, "Mastering render failed");
    require(output != input, "Mastering preset passed audio through unchanged");
    require(std::any_of(output.begin(), output.end(), [](int16_t sample) { return sample != 0; }), "Mastering preset produced only silence");
  }
}
}

int main() {
  try {
    require(sizeof(void*) == 8, "Native smoke requires a 64-bit target");
    check_pcm();
    check_mastering();
    std::cout << "PASS: native 64-bit PCM ring, volume, pause, underrun, position, validation and all 20 mastering presets.\n";
    return 0;
  } catch (const std::exception& failure) {
    std::cerr << "FAIL: " << failure.what() << '\n';
    return 1;
  }
}
