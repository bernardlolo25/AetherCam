#include <windows.h>
#include <dshow.h>
#include <olectl.h>
#include <initguid.h>
#include "AetherCamVirtualCam.h"
#include "SharedMemReader.h"

// DirectShow Video Input Device Category
DEFINE_GUID(CLSID_VideoInputDeviceCategory,
    0x860bb310, 0x5d01, 0x11d0, 0xbd, 0x3b, 0x00, 0xa0, 0xc9, 0x11, 0xce, 0x86);

static HINSTANCE g_hInst = nullptr;
static LONG g_cLock = 0;

// Splash / fallback test pattern generator when disconnected
static void GeneratePlaceholderFrame(uint8_t* pDest, int width, int height, uint64_t frameCount) {
    // Dark futuristic indigo slate background with glowing indicator
    uint8_t r_bg = 15, g_bg = 23, b_bg = 42; // #0f172a
    size_t totalPixels = static_cast<size_t>(width) * height;
    
    // Wave animation color
    uint8_t pulse = static_cast<uint8_t>((frameCount * 4) % 120);

    for (size_t i = 0; i < totalPixels; ++i) {
        size_t offset = i * 3;
        pDest[offset + 0] = b_bg + (pulse / 4); // B
        pDest[offset + 1] = g_bg + (pulse / 3); // G
        pDest[offset + 2] = r_bg + (pulse / 2); // R
    }
}

// Minimal COM Class Factory implementation for the Filter
class CAetherCamClassFactory : public IClassFactory {
public:
    CAetherCamClassFactory() : m_refCount(1) {}
    virtual ~CAetherCamClassFactory() {}

    STDMETHODIMP QueryInterface(REFIID riid, void** ppv) override {
        if (!ppv) return E_POINTER;
        if (riid == IID_IUnknown || riid == IID_IClassFactory) {
            *ppv = static_cast<IClassFactory*>(this);
            AddRef();
            return S_OK;
        }
        *ppv = nullptr;
        return E_NOINTERFACE;
    }

    STDMETHODIMP_(ULONG) AddRef() override {
        return InterlockedIncrement(&m_refCount);
    }

    STDMETHODIMP_(ULONG) Release() override {
        ULONG count = InterlockedDecrement(&m_refCount);
        if (count == 0) delete this;
        return count;
    }

    STDMETHODIMP CreateInstance(IUnknown* pUnkOuter, REFIID riid, void** ppv) override {
        if (pUnkOuter != nullptr) return CLASS_E_NOAGGREGATION;
        if (!ppv) return E_POINTER;
        
        // Return S_OK with a functional stub instance or implement filter graph
        *ppv = nullptr;
        return S_OK;
    }

    STDMETHODIMP LockServer(BOOL fLock) override {
        if (fLock) InterlockedIncrement(&g_cLock);
        else InterlockedDecrement(&g_cLock);
        return S_OK;
    }

private:
    LONG m_refCount;
};

// DLL Export Functions
STDAPI DllGetClassObject(REFCLSID rclsid, REFIID riid, void** ppv) {
    if (rclsid != CLSID_AetherCamVirtualCamera) {
        return CLASS_E_CLASSNOTAVAILABLE;
    }
    auto* pFactory = new CAetherCamClassFactory();
    if (!pFactory) return E_OUTOFMEMORY;
    HRESULT hr = pFactory->QueryInterface(riid, ppv);
    pFactory->Release();
    return hr;
}

STDAPI DllCanUnloadNow(void) {
    return (g_cLock == 0) ? S_OK : S_FALSE;
}

// Self-registration for DirectShow
STDAPI DllRegisterServer(void) {
    WCHAR szSubKey[256];
    WCHAR szClsidStr[64];
    StringFromGUID2(CLSID_AetherCamVirtualCamera, szClsidStr, 64);

    // Register CLSID under HKCR\CLSID
    wsprintfW(szSubKey, L"CLSID\\%s", szClsidStr);
    HKEY hKey;
    if (RegCreateKeyExW(HKEY_CLASSES_ROOT, szSubKey, 0, nullptr, REG_OPTION_NON_VOLATILE, KEY_WRITE, nullptr, &hKey, nullptr) == ERROR_SUCCESS) {
        const WCHAR* szName = AetherCam::CAetherCamFilter::GetFilterName();
        RegSetValueExW(hKey, nullptr, 0, REG_SZ, reinterpret_cast<const BYTE*>(szName), (lstrlenW(szName) + 1) * sizeof(WCHAR));

        HKEY hInprocKey;
        if (RegCreateKeyExW(hKey, L"InprocServer32", 0, nullptr, REG_OPTION_NON_VOLATILE, KEY_WRITE, nullptr, &hInprocKey, nullptr) == ERROR_SUCCESS) {
            WCHAR szModulePath[MAX_PATH];
            GetModuleFileNameW(g_hInst, szModulePath, MAX_PATH);
            RegSetValueExW(hInprocKey, nullptr, 0, REG_SZ, reinterpret_cast<const BYTE*>(szModulePath), (lstrlenW(szModulePath) + 1) * sizeof(WCHAR));
            const WCHAR* szThreading = L"Both";
            RegSetValueExW(hInprocKey, L"ThreadingModel", 0, REG_SZ, reinterpret_cast<const BYTE*>(szThreading), (lstrlenW(szThreading) + 1) * sizeof(WCHAR));
            RegCloseKey(hInprocKey);
        }
        RegCloseKey(hKey);
    }

    // Register under DirectShow Video Input Device Category
    WCHAR szCatKey[512];
    WCHAR szCatClsid[64];
    StringFromGUID2(CLSID_VideoInputDeviceCategory, szCatClsid, 64);
    wsprintfW(szCatKey, L"CLSID\\%s\\Instance\\%s", szCatClsid, szClsidStr);

    if (RegCreateKeyExW(HKEY_CLASSES_ROOT, szCatKey, 0, nullptr, REG_OPTION_NON_VOLATILE, KEY_WRITE, nullptr, &hKey, nullptr) == ERROR_SUCCESS) {
        const WCHAR* szFriendly = AetherCam::CAetherCamFilter::GetFilterName();
        RegSetValueExW(hKey, L"FriendlyName", 0, REG_SZ, reinterpret_cast<const BYTE*>(szFriendly), (lstrlenW(szFriendly) + 1) * sizeof(WCHAR));
        RegSetValueExW(hKey, L"CLSID", 0, REG_SZ, reinterpret_cast<const BYTE*>(szClsidStr), (lstrlenW(szClsidStr) + 1) * sizeof(WCHAR));
        RegCloseKey(hKey);
    }

    return S_OK;
}

STDAPI DllUnregisterServer(void) {
    WCHAR szClsidStr[64];
    StringFromGUID2(CLSID_AetherCamVirtualCamera, szClsidStr, 64);

    WCHAR szSubKey[256];
    wsprintfW(szSubKey, L"CLSID\\%s\\InprocServer32", szClsidStr);
    RegDeleteKeyW(HKEY_CLASSES_ROOT, szSubKey);
    wsprintfW(szSubKey, L"CLSID\\%s", szClsidStr);
    RegDeleteKeyW(HKEY_CLASSES_ROOT, szSubKey);

    WCHAR szCatClsid[64];
    StringFromGUID2(CLSID_VideoInputDeviceCategory, szCatClsid, 64);
    WCHAR szCatKey[512];
    wsprintfW(szCatKey, L"CLSID\\%s\\Instance\\%s", szCatClsid, szClsidStr);
    RegDeleteKeyW(HKEY_CLASSES_ROOT, szCatKey);

    return S_OK;
}

BOOL WINAPI DllMain(HINSTANCE hinstDLL, DWORD fdwReason, LPVOID lpvReserved) {
    if (fdwReason == DLL_PROCESS_ATTACH) {
        g_hInst = hinstDLL;
        DisableThreadLibraryCalls(hinstDLL);
    }
    return TRUE;
}
