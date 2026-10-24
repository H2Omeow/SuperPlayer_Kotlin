set(NEKO_DECODER_DIR "$ENV{NEKO_DECODER_DIR}" CACHE PATH "Verified FFmpeg/ffprobe package for the target architecture")
option(NEKO_REQUIRE_DECODERS "Refuse a bundle without its professional DSP decoders" "$ENV{NEKO_REQUIRE_DECODERS}")
if(NEKO_DECODER_DIR)
  get_filename_component(decoder_root "${NEKO_DECODER_DIR}" REALPATH)
  get_filename_component(build_root "${CMAKE_BINARY_DIR}" REALPATH)
  string(FIND "${decoder_root}/" "${build_root}/" decoder_in_build)
  if(decoder_in_build EQUAL 0)
    message(FATAL_ERROR "Decoder input must be outside the generated platform build directory.")
  endif()
  find_package(Python3 REQUIRED COMPONENTS Interpreter)
  set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS
    "${CMAKE_CURRENT_LIST_DIR}/decoder.json"
    "${CMAKE_CURRENT_LIST_DIR}/../tool/check_artifact.py"
    "${NEKO_DECODER_DIR}/licenses/ffmpeg/build-info.json")
  execute_process(
    COMMAND "${Python3_EXECUTABLE}" "${CMAKE_CURRENT_LIST_DIR}/../tool/check_artifact.py"
      "${FLUTTER_TARGET_PLATFORM}" --decoder-directory "${NEKO_DECODER_DIR}"
    RESULT_VARIABLE decoder_result
    OUTPUT_VARIABLE decoder_output
    ERROR_VARIABLE decoder_error)
  if(NOT decoder_result EQUAL 0)
    message(FATAL_ERROR "Invalid decoder package: ${decoder_output}${decoder_error}")
  endif()
  install(DIRECTORY "${NEKO_DECODER_DIR}/native" "${NEKO_DECODER_DIR}/licenses"
    DESTINATION "${CMAKE_INSTALL_PREFIX}" USE_SOURCE_PERMISSIONS COMPONENT Runtime)
elseif(NEKO_REQUIRE_DECODERS)
  message(FATAL_ERROR "Professional DSP packaging requires NEKO_DECODER_DIR with verified FFmpeg and ffprobe.")
endif()
