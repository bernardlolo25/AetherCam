#pragma once

#include <windows.h>
#include <dshow.h>
#include <initguid.h>
#include <cstdint>

// CLSID_AetherCamVirtualMicrophone: {B95DF402-8792-5432-C19A-9FA4F6225E31}
DEFINE_GUID(CLSID_AetherCamVirtualMicrophone,
    0xb95df402, 0x8792, 0x5432, 0xc1, 0x9a, 0x9f, 0xa4, 0xf6, 0x22, 0x5e, 0x31);

// DirectShow Audio Input Device Category
DEFINE_GUID(CLSID_AudioInputDeviceCategory,
    0x33d9a762, 0x90c8, 0x11d0, 0xbd, 0x43, 0x00, 0xa0, 0xc9, 0x11, 0xce, 0x86);

namespace AetherCam {

#pragma pack(push, 1)
struct SharedMemAudioHeader {
    uint32_t magic;         // 0x41554431 = "AUD1"
    uint32_t sampleRate;    // 48000 Hz
    uint16_t channels;      // 2 (Stereo)
    uint16_t bitsPerSample; // 16-bit PCM
    uint32_t bufferSize;    // Bytes in ring buffer
    uint64_t timestampUs;   // Presentation timestamp
    uint32_t reserved[6];
};
#pragma pack(pop)

constexpr uint32_t SHARED_AUDIO_MAGIC = 0x41554431;

} // namespace AetherCam
