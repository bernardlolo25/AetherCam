#pragma once

#include <windows.h>
#include <dshow.h>
#include <initguid.h>
#include <memory>
#include "SharedMemReader.h"

// CLSID_AetherCamVirtualCamera: {A84CE391-7681-4321-B089-8FA3E5114D20}
DEFINE_GUID(CLSID_AetherCamVirtualCamera,
    0xa84ce391, 0x7681, 0x4321, 0xb0, 0x89, 0x8f, 0xa3, 0xe5, 0x11, 0x4d, 0x20);

namespace AetherCam {

class CAetherCamStream;

class CAetherCamFilter {
public:
    CAetherCamFilter();
    virtual ~CAetherCamFilter();

    static const WCHAR* GetFilterName() { return L"AetherCam Virtual Camera"; }
    static const GUID& GetFilterClsid() { return CLSID_AetherCamVirtualCamera; }
};

// Stream format configuration
struct VideoFormatDesc {
    int width;
    int height;
    int fps;
    GUID mediaSubType;
    int bitCount;
};

const VideoFormatDesc g_SupportedFormats[] = {
    { 1920, 1080, 60, MEDIASUBTYPE_RGB24, 24 },
    { 1920, 1080, 30, MEDIASUBTYPE_RGB24, 24 },
    { 3840, 2160, 30, MEDIASUBTYPE_RGB24, 24 },
    { 1280, 720,  60, MEDIASUBTYPE_RGB24, 24 },
    { 1280, 720,  30, MEDIASUBTYPE_RGB24, 24 },
    { 640,  480,  30, MEDIASUBTYPE_RGB24, 24 }
};

const int g_SupportedFormatCount = sizeof(g_SupportedFormats) / sizeof(g_SupportedFormats[0]);

} // namespace AetherCam
