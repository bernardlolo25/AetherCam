cmake_minimum_required(VERSION 3.20)
project(AetherCamVirtualMic LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_definitions(-DUNICODE -D_UNICODE -DWIN32_LEAN_AND_MEAN)

set(SOURCES
    AetherCamVirtualMic.cpp
)

add_library(AetherCamVirtualMic SHARED ${SOURCES})

target_link_libraries(AetherCamVirtualMic PRIVATE
    strmiids
    ole32
    oleaut32
    advapi32
    user32
)

set_target_properties(AetherCamVirtualMic PROPERTIES
    OUTPUT_NAME "AetherCamVirtualMic"
    SUFFIX ".dll"
)
