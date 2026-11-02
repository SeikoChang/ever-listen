cmake_minimum_required(VERSION 3.18)
project(everlisten_vad C)

# A single pinned dependency is shared with iOS and host quality tooling.
get_filename_component(REPO_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../../.." ABSOLUTE)
set(WEBRTC_ROOT "${REPO_ROOT}/third_party/webrtc-vad")
file(GLOB WEBRTC_DSP CONFIGURE_DEPENDS "${WEBRTC_ROOT}/webrtc/common_audio/signal_processing/*.c")
file(GLOB WEBRTC_VAD CONFIGURE_DEPENDS "${WEBRTC_ROOT}/webrtc/common_audio/vad/*.c")
add_library(everlisten_vad SHARED ${WEBRTC_DSP} ${WEBRTC_VAD} everlisten_vad.c)
target_include_directories(everlisten_vad PRIVATE "${WEBRTC_ROOT}" "${CMAKE_CURRENT_LIST_DIR}")
target_compile_definitions(everlisten_vad PRIVATE WEBRTC_POSIX)
find_library(ANDROID_LOG log REQUIRED)
target_link_libraries(everlisten_vad ${ANDROID_LOG})
