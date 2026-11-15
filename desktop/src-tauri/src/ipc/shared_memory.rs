//! High-performance Zero-Copy Windows Named Shared Memory Producer for DirectShow Filter

use std::ptr;
use std::sync::atomic::{AtomicU64, Ordering};

pub const SHARED_MEM_MAGIC: u32 = 0x56494431; // "VID1"
pub const MAX_VIDEO_SIZE: usize = 3840 * 2160 * 4; // 4K RGBA buffer

#[repr(C, packed)]
#[derive(Debug, Clone, Copy)]
pub struct SharedMemVideoHeader {
    pub magic: u32,
    pub width: u32,
    pub height: u32,
    pub stride: u32,
    pub pixel_format: u32, // 0: RGB24, 1: NV12, 2: YUY2, 3: RGBA32
    pub frame_number: u64,
    pub timestamp_us: u64,
    pub data_size: u32,
    pub fps: u32,
    pub reserved: [u32; 7],
}

pub const HEADER_SIZE: usize = std::mem::size_of::<SharedMemVideoHeader>();
pub const TOTAL_SHARED_MEM_SIZE: usize = HEADER_SIZE + MAX_VIDEO_SIZE;

#[cfg(windows)]
use windows::core::PCWSTR;
#[cfg(windows)]
use windows::Win32::Foundation::{CloseHandle, HANDLE, INVALID_HANDLE_VALUE};
#[cfg(windows)]
use windows::Win32::System::Memory::{
    CreateFileMappingW, MapViewOfFile, UnmapViewOfFile, FILE_MAP_ALL_ACCESS, PAGE_READWRITE,
};
#[cfg(windows)]
use windows::Win32::System::Threading::{
    CreateEventW, CreateMutexW, ReleaseMutex, SetEvent, WaitForSingleObject, INFINITE,
};

pub struct VideoSharedMemory {
    #[cfg(windows)]
    map_handle: HANDLE,
    #[cfg(windows)]
    event_handle: HANDLE,
    #[cfg(windows)]
    mutex_handle: HANDLE,
    memory_ptr: *mut u8,
    frame_counter: AtomicU64,
}

unsafe impl Send for VideoSharedMemory {}
unsafe impl Sync for VideoSharedMemory {}

impl VideoSharedMemory {
    #[cfg(windows)]
    pub fn new(map_name: &str, event_name: &str, mutex_name: &str) -> Result<Self, String> {
        use std::ffi::OsStr;
        use std::os::windows::ffi::OsStrExt;

        let to_wide = |s: &str| -> Vec<u16> {
            OsStr::new(s).encode_wide().chain(std::iter::once(0)).collect()
        };

        let wide_map = to_wide(map_name);
        let wide_event = to_wide(event_name);
        let wide_mutex = to_wide(mutex_name);

        unsafe {
            let map_handle = CreateFileMappingW(
                INVALID_HANDLE_VALUE,
                None,
                PAGE_READWRITE,
                0,
                TOTAL_SHARED_MEM_SIZE as u32,
                PCWSTR(wide_map.as_ptr()),
            ).map_err(|e| format!("Failed to create file mapping: {:?}", e))?;

            let memory_ptr = MapViewOfFile(
                map_handle,
                FILE_MAP_ALL_ACCESS,
                0,
                0,
                TOTAL_SHARED_MEM_SIZE,
            ).Value as *mut u8;

            if memory_ptr.is_null() {
                CloseHandle(map_handle);
                return Err("Failed to map view of shared memory".into());
            }

            let event_handle = CreateEventW(
                None,
                false, // Auto-reset event
                false,
                PCWSTR(wide_event.as_ptr()),
            ).map_err(|e| format!("Failed to create ready event: {:?}", e))?;

            let mutex_handle = CreateMutexW(
                None,
                false,
                PCWSTR(wide_mutex.as_ptr()),
            ).map_err(|e| format!("Failed to create mutex: {:?}", e))?;

            Ok(Self {
                map_handle,
                event_handle,
                mutex_handle,
                memory_ptr,
                frame_counter: AtomicU64::new(1),
            })
        }
    }

    #[cfg(not(windows))]
    pub fn new(_map_name: &str, _event_name: &str, _mutex_name: &str) -> Result<Self, String> {
        let buf = Box::into_raw(vec![0u8; TOTAL_SHARED_MEM_SIZE].into_boxed_slice()) as *mut u8;
        Ok(Self {
            memory_ptr: buf,
            frame_counter: AtomicU64::new(1),
        })
    }

    pub fn write_frame(
        &self,
        width: u32,
        height: u32,
        stride: u32,
        pixel_format: u32,
        timestamp_us: u64,
        fps: u32,
        frame_bytes: &[u8],
    ) -> Result<u64, String> {
        if frame_bytes.len() > MAX_VIDEO_SIZE {
            return Err("Frame size exceeds allocated shared memory boundary".into());
        }

        let frame_num = self.frame_counter.fetch_add(1, Ordering::SeqCst);

        let header = SharedMemVideoHeader {
            magic: SHARED_MEM_MAGIC,
            width,
            height,
            stride,
            pixel_format,
            frame_number: frame_num,
            timestamp_us,
            data_size: frame_bytes.len() as u32,
            fps,
            reserved: [0; 7],
        };

        unsafe {
            #[cfg(windows)]
            {
                let _ = WaitForSingleObject(self.mutex_handle, 50);
            }

            // Copy header
            ptr::copy_nonoverlapping(
                &header as *const _ as *const u8,
                self.memory_ptr,
                HEADER_SIZE,
            );

            // Copy frame data payload
            let dest_payload = self.memory_ptr.add(HEADER_SIZE);
            ptr::copy_nonoverlapping(
                frame_bytes.as_ptr(),
                dest_payload,
                frame_bytes.len(),
            );

            #[cfg(windows)]
            {
                let _ = ReleaseMutex(self.mutex_handle);
                let _ = SetEvent(self.event_handle);
            }
        }

        Ok(frame_num)
    }
}

impl Drop for VideoSharedMemory {
    fn drop(&mut self) {
        unsafe {
            #[cfg(windows)]
            {
                if !self.memory_ptr.is_null() {
                    let _ = UnmapViewOfFile(windows::Win32::System::Memory::MEMORY_MAPPED_VIEW_ADDRESS {
                        Value: self.memory_ptr as *mut _,
                    });
                }
                let _ = CloseHandle(self.map_handle);
                let _ = CloseHandle(self.event_handle);
                let _ = CloseHandle(self.mutex_handle);
            }
            #[cfg(not(windows))]
            {
                if !self.memory_ptr.is_null() {
                    let _ = Box::from_raw(std::slice::from_raw_parts_mut(self.memory_ptr, TOTAL_SHARED_MEM_SIZE));
                }
            }
        }
    }
}
