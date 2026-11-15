#pragma once

#include <windows.h>
#include <cstdint>
#include <string>

namespace AetherCam {

#pragma pack(push, 1)
struct SharedMemVideoHeader {
    uint32_t magic;         // 0x56494431 = "VID1"
    uint32_t width;         // Width in pixels
    uint32_t height;        // Height in pixels
    uint32_t stride;        // Bytes per scanline
    uint32_t pixelFormat;   // 0: RGB24, 1: NV12, 2: YUY2, 3: RGBA32
    uint64_t frameNumber;   // Monotonic sequence counter
    uint64_t timestampUs;   // Monotonic timestamp in microseconds
    uint32_t dataSize;      // Frame buffer byte length
    uint32_t fps;           // Target FPS (30, 60)
    uint32_t reserved[7];
};
#pragma pack(pop)

constexpr uint32_t SHARED_MEM_MAGIC = 0x56494431;
constexpr size_t MAX_VIDEO_BUFFER_SIZE = 3840 * 2160 * 4; // 4K RGBA support
constexpr size_t TOTAL_SHARED_MEM_SIZE = sizeof(SharedMemVideoHeader) + MAX_VIDEO_BUFFER_SIZE;

class SharedMemReader {
public:
    SharedMemReader();
    ~SharedMemReader();

    bool Open(const std::wstring& mapName, const std::wstring& eventName, const std::wstring& mutexName);
    void Close();

    // Copies newest frame into destination buffer. Returns true if new frame read.
    bool ReadLatestFrame(uint8_t* destBuffer, size_t maxBufferSize, SharedMemVideoHeader& outHeader, DWORD timeoutMs = 20);

    bool IsConnected() const { return m_isMapped; }

private:
    HANDLE m_hMapFile;
    HANDLE m_hFrameReadyEvent;
    HANDLE m_hMutex;
    uint8_t* m_pSharedMemory;
    bool m_isMapped;
    uint64_t m_lastReadFrameNumber;
};

} // namespace AetherCam
