cmake_minimum_required(VERSION 3.14)
project(nekoplayer_dsp LANGUAGES C CXX)

set(NEKO_MINIAUDIO_HEADER "${CMAKE_CURRENT_BINARY_DIR}/miniaudio.h" CACHE FILEPATH "Pinned miniaudio header")
if(NOT EXISTS "${NEKO_MINIAUDIO_HEADER}")
  file(DOWNLOAD
    "https://raw.githubusercontent.com/mackron/miniaudio/0.11.24/miniaudio.h"
    "${NEKO_MINIAUDIO_HEADER}"
    EXPECTED_HASH SHA256=c029554572ae71a94b7825ef1ab9bd7e646344a79e2e852a03f8b18e428c1443
    TLS_VERIFY ON)
endif()
file(SHA256 "${NEKO_MINIAUDIO_HEADER}" NEKO_MINIAUDIO_HASH)
if(NOT NEKO_MINIAUDIO_HASH STREQUAL "c029554572ae71a94b7825ef1ab9bd7e646344a79e2e852a03f8b18e428c1443")
  message(FATAL_ERROR "miniaudio header checksum mismatch")
endif()
get_filename_component(NEKO_MINIAUDIO_DIRECTORY "${NEKO_MINIAUDIO_HEADER}" DIRECTORY)
add_library(nekoplayer_dsp SHARED "pcm_sink.cpp")
target_compile_features(nekoplayer_dsp PRIVATE cxx_std_17)
target_include_directories(nekoplayer_dsp PRIVATE "${NEKO_MINIAUDIO_DIRECTORY}" "${CMAKE_CURRENT_SOURCE_DIR}/../../app/src/main/cpp")
target_compile_definitions(nekoplayer_dsp PRIVATE MA_NO_DECODING MA_NO_ENCODING MA_NO_RESOURCE_MANAGER MA_NO_NODE_GRAPH MA_NO_ENGINE)
if(WIN32)
  target_compile_definitions(nekoplayer_dsp PRIVATE NOMINMAX _HAS_EXCEPTIONS=1)
  target_compile_options(nekoplayer_dsp PRIVATE /EHsc /utf-8)
else()
  target_compile_options(nekoplayer_dsp PRIVATE -O1 -fvisibility=hidden)
  target_link_libraries(nekoplayer_dsp PRIVATE dl pthread m)
endif()
