cmake_minimum_required(VERSION 3.22.1)
project("nekoplayer_audio_effects")

add_library(nekoplayer_audio_effects SHARED
    native-audio-effects.cpp
)

find_library(log-lib log)

# Android 15+ 16KB Page Size 适配
target_link_options(nekoplayer_audio_effects PRIVATE "-Wl,-z,max-page-size=16384")

target_link_libraries(nekoplayer_audio_effects ${log-lib})
