cmake_minimum_required(VERSION 3.14)
project(nekoplayer_plugins LANGUAGES C CXX)

set(NEKO_QUICKJS_VERSION "0.12.1")
set(NEKO_QUICKJS_COMMIT "58bdcf0ce35594df30bf5cfbba3be8b454799cc0")
set(NEKO_QUICKJS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/vendor/quickjs-ng")

find_package(Threads REQUIRED)
add_library(neko_quickjs STATIC
  "${NEKO_QUICKJS_DIR}/quickjs.c"
  "${NEKO_QUICKJS_DIR}/dtoa.c"
  "${NEKO_QUICKJS_DIR}/libregexp.c"
  "${NEKO_QUICKJS_DIR}/libunicode.c")
set_target_properties(neko_quickjs PROPERTIES
  C_STANDARD 11 C_STANDARD_REQUIRED ON C_EXTENSIONS ON
  POSITION_INDEPENDENT_CODE ON C_VISIBILITY_PRESET hidden)
target_include_directories(neko_quickjs PUBLIC "${NEKO_QUICKJS_DIR}")
target_compile_definitions(neko_quickjs PRIVATE _GNU_SOURCE QUICKJS_NG_BUILD)
target_link_libraries(neko_quickjs PUBLIC Threads::Threads)

add_library(nekoplayer_plugins SHARED
  "neko_plugin.cpp" "neko_plugin_json.cpp" "neko_plugin_random.cpp")
target_compile_features(nekoplayer_plugins PRIVATE cxx_std_17)
target_include_directories(nekoplayer_plugins PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}")
target_link_libraries(nekoplayer_plugins PRIVATE neko_quickjs)
set_target_properties(nekoplayer_plugins PROPERTIES
  CXX_VISIBILITY_PRESET hidden VISIBILITY_INLINES_HIDDEN ON)

if(WIN32)
  target_compile_definitions(neko_quickjs PRIVATE WIN32_LEAN_AND_MEAN _WIN32_WINNT=0x0601)
  target_compile_definitions(nekoplayer_plugins PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX _WIN32_WINNT=0x0601)
  target_link_libraries(nekoplayer_plugins PRIVATE bcrypt)
  set_target_properties(nekoplayer_plugins PROPERTIES PREFIX "")
endif()

if(MSVC)
  target_compile_options(neko_quickjs PRIVATE /J /utf-8 /experimental:c11atomics)
  target_compile_options(nekoplayer_plugins PRIVATE /EHsc /utf-8)
  target_compile_definitions(nekoplayer_plugins PRIVATE _HAS_EXCEPTIONS=1)
else()
  target_compile_options(neko_quickjs PRIVATE -funsigned-char)
  target_compile_options(nekoplayer_plugins PRIVATE -fexceptions)
  if(MINGW)
    target_link_options(nekoplayer_plugins PRIVATE -static-libgcc -static-libstdc++ -static)
  else()
    target_link_libraries(nekoplayer_plugins PRIVATE m)
    if(CMAKE_SYSTEM_NAME STREQUAL "Linux" OR ANDROID)
      target_link_options(nekoplayer_plugins PRIVATE
        "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/exports.map")
      set_property(TARGET nekoplayer_plugins APPEND PROPERTY
        LINK_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/exports.map")
    endif()
  endif()
endif()

if(ANDROID)
  target_sources(nekoplayer_plugins PRIVATE "neko_plugin_jni.cpp")
  target_link_options(nekoplayer_plugins PRIVATE "-Wl,-z,max-page-size=16384")
endif()

if(CMAKE_SOURCE_DIR STREQUAL CMAKE_CURRENT_SOURCE_DIR)
  include(GNUInstallDirs)
  install(TARGETS nekoplayer_plugins
    LIBRARY DESTINATION "${CMAKE_INSTALL_LIBDIR}"
    RUNTIME DESTINATION "${CMAKE_INSTALL_BINDIR}")
  install(FILES "${NEKO_QUICKJS_DIR}/LICENSE" DESTINATION "licenses/quickjs-ng")
endif()
