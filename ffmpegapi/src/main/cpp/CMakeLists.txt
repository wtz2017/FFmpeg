cmake_minimum_required(VERSION 3.4.1)

include_directories(weplayer/include/)
include_directories(ffmpeg/include/)
include_directories(soundtouch/include/)
include_directories(test/include/)

# Creates and names a library, sets it as either STATIC or SHARED,
# and provides the relative paths to its source code.
add_library(
        weplayer

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        weplayer/WeJNI.cpp
        weplayer/WeDemux.cpp
        weplayer/WePlayer.cpp
        weplayer/WeAudioDecoder.cpp
        weplayer/WeAudioPlayer.cpp
        weplayer/AudioStream.cpp
        weplayer/LooperThread.cpp
        weplayer/AVPacketQueue.cpp
        weplayer/OpenSLPlayer.cpp
        weplayer/PlayStatus.cpp
        weplayer/JavaListener.cpp
        weplayer/WeUtils.cpp

        soundtouch/AAFilter.cpp
        soundtouch/FIFOSampleBuffer.cpp
        soundtouch/FIRFilter.cpp
        soundtouch/cpu_detect_x86.cpp
        soundtouch/sse_optimized.cpp
        soundtouch/RateTransposer.cpp
        soundtouch/SoundTouch.cpp
        soundtouch/InterpolateCubic.cpp
        soundtouch/InterpolateLinear.cpp
        soundtouch/InterpolateShannon.cpp
        soundtouch/TDStretch.cpp
        soundtouch/PeakFinder.cpp )

add_library(
        cpp_test

        SHARED

        test/CppTest.cpp
        weplayer/JavaListener.cpp)

add_library(avcodec SHARED IMPORTED)
set_target_properties(avcodec
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/jniLibs/${ANDROID_ABI}/libavcodec.so)

add_library(avdevice SHARED IMPORTED)
set_target_properties(avdevice
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/jniLibs/${ANDROID_ABI}/libavdevice.so)

add_library(avfilter SHARED IMPORTED)
set_target_properties(avfilter
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/jniLibs/${ANDROID_ABI}/libavfilter.so)

add_library(avformat SHARED IMPORTED)
set_target_properties(avformat
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/jniLibs/${ANDROID_ABI}/libavformat.so)

add_library(avutil SHARED IMPORTED)
set_target_properties(avutil
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/jniLibs/${ANDROID_ABI}/libavutil.so)

add_library(postproc SHARED IMPORTED)
set_target_properties(postproc
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/jniLibs/${ANDROID_ABI}/libpostproc.so)

add_library(swresample SHARED IMPORTED)
set_target_properties(swresample
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/jniLibs/${ANDROID_ABI}/libswresample.so)

add_library(swscale SHARED IMPORTED)
set_target_properties(swscale
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/jniLibs/${ANDROID_ABI}/libswscale.so)

# Searches for a specified prebuilt library and stores the path as a variable.
# Because CMake includes system libraries in the search path by default,
# you only need to specify the name of the public NDK library you want to add.
find_library(
        # Sets the name of the path variable.
        log-lib
        # Specifies the name of the NDK library that you want CMake to locate.
        log)

# Specifies libraries CMake should link to your target library.
target_link_libraries(
        weplayer

        avcodec
        avdevice
        avfilter
        avformat
        avutil
        postproc
        swresample
        swscale

        OpenSLES

        # Links the target library to the log library included in the NDK.
        ${log-lib})

target_link_libraries(
        cpp_test

        OpenSLES

        ${log-lib})

