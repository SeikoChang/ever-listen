cmake_minimum_required(VERSION 3.10.2)

project("everlisten_vad")

add_library(
    # Sets the name of the library.
    everlisten_vad

    # Sets the type of the library.
    SHARED

    # Provides a relative path to your source file(s).
    webrtc_vad.c
    everlisten_vad.c
)

# Searches for a JNI-compatible Java VM library and sets the
# appropriate variables.
find_library(
    # Sets the name of the path variable.
    jnigraphics

    # Specifies the name of the NDK library that
    # you want CMake to locate.
    jnigraphics
)

target_include_directories(everlisten_vad PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

# Specifies directories to include to locate header files.
target_include_directories(everlisten_vad PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/
    ${ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include # Standard NDK headers
)

# Links your native library to the log library and other dependencies.
target_link_libraries(
    everlisten_vad

    # The log library included in the NDK.
    android
    log

    # Links the target to the jnigraphics library.
    ${jnigraphics}
)
