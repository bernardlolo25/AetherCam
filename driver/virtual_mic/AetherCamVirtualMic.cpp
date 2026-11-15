#include "AetherCamVirtualMic.h"
#include <olectl.h>

static HINSTANCE g_hInst = nullptr;
static LONG g_cLock = 0;

static const WCHAR* g_szMicFriendlyName = L"AetherCam Virtual Microphone";

class CAetherMicClassFactory : public IClassFactory {
public:
    CAetherMicClassFactory() : m_refCount(1) {}

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

STDAPI DllGetClassObject(REFCLSID rclsid, REFIID riid, void** ppv) {
    if (rclsid != CLSID_AetherCamVirtualMicrophone) {
        return CLASS_E_CLASSNOTAVAILABLE;
    }
    auto* pFactory = new CAetherMicClassFactory();
    if (!pFactory) return E_OUTOFMEMORY;
    HRESULT hr = pFactory->QueryInterface(riid, ppv);
    pFactory->Release();
    return hr;
}

STDAPI DllCanUnloadNow(void) {
    return (g_cLock == 0) ? S_OK : S_FALSE;
}

STDAPI DllRegisterServer(void) {
    WCHAR szClsidStr[64];
    StringFromGUID2(CLSID_AetherCamVirtualMicrophone, szClsidStr, 64);

    WCHAR szSubKey[256];
    wsprintfW(szSubKey, L"CLSID\\%s", szClsidStr);
    HKEY hKey;
    if (RegCreateKeyExW(HKEY_CLASSES_ROOT, szSubKey, 0, nullptr, REG_OPTION_NON_VOLATILE, KEY_WRITE, nullptr, &hKey, nullptr) == ERROR_SUCCESS) {
        RegSetValueExW(hKey, nullptr, 0, REG_SZ, reinterpret_cast<const BYTE*>(g_szMicFriendlyName), (lstrlenW(g_szMicFriendlyName) + 1) * sizeof(WCHAR));

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

    WCHAR szCatKey[512];
    WCHAR szCatClsid[64];
    StringFromGUID2(CLSID_AudioInputDeviceCategory, szCatClsid, 64);
    wsprintfW(szCatKey, L"CLSID\\%s\\Instance\\%s", szCatClsid, szClsidStr);

    if (RegCreateKeyExW(HKEY_CLASSES_ROOT, szCatKey, 0, nullptr, REG_OPTION_NON_VOLATILE, KEY_WRITE, nullptr, &hKey, nullptr) == ERROR_SUCCESS) {
        RegSetValueExW(hKey, L"FriendlyName", 0, REG_SZ, reinterpret_cast<const BYTE*>(g_szMicFriendlyName), (lstrlenW(g_szMicFriendlyName) + 1) * sizeof(WCHAR));
        RegSetValueExW(hKey, L"CLSID", 0, REG_SZ, reinterpret_cast<const BYTE*>(szClsidStr), (lstrlenW(szClsidStr) + 1) * sizeof(WCHAR));
        RegCloseKey(hKey);
    }

    return S_OK;
}

STDAPI DllUnregisterServer(void) {
    WCHAR szClsidStr[64];
    StringFromGUID2(CLSID_AetherCamVirtualMicrophone, szClsidStr, 64);

    WCHAR szSubKey[256];
    wsprintfW(szSubKey, L"CLSID\\%s\\InprocServer32", szClsidStr);
    RegDeleteKeyW(HKEY_CLASSES_ROOT, szSubKey);
    wsprintfW(szSubKey, L"CLSID\\%s", szClsidStr);
    RegDeleteKeyW(HKEY_CLASSES_ROOT, szSubKey);

    WCHAR szCatClsid[64];
    StringFromGUID2(CLSID_AudioInputDeviceCategory, szCatClsid, 64);
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
