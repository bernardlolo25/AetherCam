#include "SharedMemReader.h"
#include <iostream>

namespace AetherCam {

SharedMemReader::SharedMemReader()
    : m_hMapFile(nullptr)
    , m_hFrameReadyEvent(nullptr)
    , m_hMutex(nullptr)
    , m_pSharedMemory(nullptr)
    , m_isMapped(false)
    , m_lastReadFrameNumber(0)
{
}

SharedMemReader::~SharedMemReader() {
    Close();
}

bool SharedMemReader::Open(const std::wstring& mapName, const std::wstring& eventName, const std::wstring& mutexName) {
    Close();

    // Open existing named shared file mapping created by AetherCam Desktop Server
    m_hMapFile = OpenFileMappingW(FILE_MAP_READ, FALSE, mapName.c_str());
    if (!m_hMapFile) {
        return false;
    }

    m_pSharedMemory = static_cast<uint8_t*>(MapViewOfFile(m_hMapFile, FILE_MAP_READ, 0, 0, TOTAL_SHARED_MEM_SIZE));
    if (!m_pSharedMemory) {
        CloseHandle(m_hMapFile);
        m_hMapFile = nullptr;
        return false;
    }

    m_hFrameReadyEvent = OpenEventW(SYNCHRONIZE, FALSE, eventName.c_str());
    m_hMutex = OpenMutexW(SYNCHRONIZE, FALSE, mutexName.c_str());

    m_isMapped = true;
    return true;
}

void SharedMemReader::Close() {
    if (m_pSharedMemory) {
        UnmapViewOfFile(m_pSharedMemory);
        m_pSharedMemory = nullptr;
    }
    if (m_hMapFile) {
        CloseHandle(m_hMapFile);
        m_hMapFile = nullptr;
    }
    if (m_hFrameReadyEvent) {
        CloseHandle(m_hFrameReadyEvent);
        m_hFrameReadyEvent = nullptr;
    }
    if (m_hMutex) {
        CloseHandle(m_hMutex);
        m_hMutex = nullptr;
    }
    m_isMapped = false;
    m_lastReadFrameNumber = 0;
}

bool SharedMemReader::ReadLatestFrame(uint8_t* destBuffer, size_t maxBufferSize, SharedMemVideoHeader& outHeader, DWORD timeoutMs) {
    if (!m_isMapped || !m_pSharedMemory) {
        return false;
    }

    // Wait on the new frame event if available
    if (m_hFrameReadyEvent) {
        DWORD waitResult = WaitForSingleObject(m_hFrameReadyEvent, timeoutMs);
        if (waitResult != WAIT_OBJECT_0 && waitResult != WAIT_TIMEOUT) {
            return false;
        }
    }

    // Acquire lock briefly for reading header and copying
    DWORD mutexWait = WAIT_OBJECT_0;
    if (m_hMutex) {
        mutexWait = WaitForSingleObject(m_hMutex, timeoutMs);
    }

    const auto* pHeader = reinterpret_cast<const SharedMemVideoHeader*>(m_pSharedMemory);
    if (pHeader->magic != SHARED_MEM_MAGIC) {
        if (m_hMutex && mutexWait == WAIT_OBJECT_0) {
            ReleaseMutex(m_hMutex);
        }
        return false;
    }

    outHeader = *pHeader;

    if (outHeader.dataSize > 0 && outHeader.dataSize <= maxBufferSize) {
        const uint8_t* pSrcData = m_pSharedMemory + sizeof(SharedMemVideoHeader);
        memcpy(destBuffer, pSrcData, outHeader.dataSize);
    }

    m_lastReadFrameNumber = outHeader.frameNumber;

    if (m_hMutex && mutexWait == WAIT_OBJECT_0) {
        ReleaseMutex(m_hMutex);
    }

    return true;
}

} // namespace AetherCam
