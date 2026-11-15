@echo off
call "C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build\vcvars64.bat"
cd /d "C:\Users\U S E R - P C\.gemini\antigravity\scratch\AetherCam\driver\virtual_camera"
echo Compiling AetherCamVirtualCam.dll...
cl.exe /O2 /LD /DUNICODE /D_UNICODE /DWIN32_LEAN_AND_MEAN /EHsc DShowFilter.cpp SharedMemReader.cpp /link /DEF:AetherCamVirtualCam.def /OUT:AetherCamVirtualCam.dll ole32.lib oleaut32.lib advapi32.lib user32.lib
