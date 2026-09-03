cmake_minimum_required(VERSION 3.22.1)
project("ritmo_native" C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Oboe prefab proporcionado por la dependencia com.google.oboe:oboe
find_package(oboe REQUIRED CONFIG)

add_library(ritmo_native SHARED
    audio_decoder.cpp
    native_audio.cpp
    native_bridge.cpp
)

target_include_directories(ritmo_native PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(ritmo_native
    oboe::oboe
    mediandk
    android
    log
    OpenSLES
)
