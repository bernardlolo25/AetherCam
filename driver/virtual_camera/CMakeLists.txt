cmake_minimum_required(VERSION 3.20)
project(AetherCamVirtualCam LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_definitions(-DUNICODE -D_UNICODE -DWIN32_LEAN_AND_MEAN)

set(SOURCES
    DShowFilter.cpp
    SharedMemReader.cpp
    AetherCamVirtualCam.def
)

add_library(AetherCamVirtualCam SHARED ${SOURCES})

target_link_libraries(AetherCamVirtualCam PRIVATE
    strmiids
    ole32
    oleaut32
    advapi32
    user32
)

set_target_properties(AetherCamVirtualCam PROPERTIES
    OUTPUT_NAME "AetherCamVirtualCam"
    SUFFIX ".dll"
)
