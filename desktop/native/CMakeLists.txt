cmake_minimum_required(VERSION 3.16)
project(nekoplayer_audio_effects LANGUAGES CXX)
find_package(JNI REQUIRED)
add_library(nekoplayer_audio_effects SHARED ../../app/src/main/cpp/native-audio-effects.cpp)
target_include_directories(nekoplayer_audio_effects PRIVATE ${JNI_INCLUDE_DIRS})
target_compile_features(nekoplayer_audio_effects PRIVATE cxx_std_17)
set_target_properties(nekoplayer_audio_effects PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}")
