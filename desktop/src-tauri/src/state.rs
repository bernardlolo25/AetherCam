//! Global Application State and Telemetry Tracking

use parking_lot::RwLock;
use serde::{Deserialize, Serialize};
use std::sync::Arc;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum ConnectionState {
    Idle,
    Listening,
    Handshaking,
    Connected,
    Streaming,
    Reconnecting,
    Error,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StreamTelemetry {
    pub state: ConnectionState,
    pub fps: u32,
    pub bitrate_kbps: u32,
    pub latency_ms: f32,
    pub jitter_ms: f32,
    pub packet_loss_pct: f32,
    pub resolution: String,
    pub active_transport: String,
    pub battery_level: f32,
    pub battery_temp_c: f32,
    pub cpu_usage_pct: f32,
    pub memory_mb: f32,
}

impl Default for StreamTelemetry {
    fn default() -> Self {
        Self {
            state: ConnectionState::Idle,
            fps: 0,
            bitrate_kbps: 0,
            latency_ms: 0.0,
            jitter_ms: 0.0,
            packet_loss_pct: 0.0,
            resolution: "1920x1080".into(),
            active_transport: "USB".into(),
            battery_level: 100.0,
            battery_temp_c: 28.0,
            cpu_usage_pct: 4.2,
            memory_mb: 112.5,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CameraControls {
    pub lens: String, // "WIDE", "ULTRA_WIDE", "TELEPHOTO", "FRONT"
    pub zoom_ratio: f32,
    pub torch_on: bool,
    pub iso: i32,             // 0 = Auto
    pub exposure_time_ns: i64,// 0 = Auto
    pub white_balance_k: i32, // 0 = Auto
    pub focus_distance: f32,  // -1.0 = Auto
    pub stabilization: bool,
    pub hdr: bool,
}

impl Default for CameraControls {
    fn default() -> Self {
        Self {
            lens: "WIDE".into(),
            zoom_ratio: 1.0,
            torch_on: false,
            iso: 0,
            exposure_time_ns: 0,
            white_balance_k: 0,
            focus_distance: -1.0,
            stabilization: true,
            hdr: true,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AiStudioState {
    pub background_mode: String, // "NONE", "BLUR", "REPLACE", "GREEN_SCREEN"
    pub blur_intensity: f32,
    pub virtual_bg_path: Option<String>,
    pub green_screen_hex: String,
    pub face_tracking: bool,
    pub eye_contact: bool,
    pub beautification: f32,
    pub low_light_boost: bool,
    pub noise_reduction: bool,
}

impl Default for AiStudioState {
    fn default() -> Self {
        Self {
            background_mode: "NONE".into(),
            blur_intensity: 0.6,
            virtual_bg_path: None,
            green_screen_hex: "#00FF00".into(),
            face_tracking: false,
            eye_contact: false,
            beautification: 0.0,
            low_light_boost: false,
            noise_reduction: true,
        }
    }
}

#[derive(Clone)]
pub struct AppState {
    pub telemetry: Arc<RwLock<StreamTelemetry>>,
    pub camera_controls: Arc<RwLock<CameraControls>>,
    pub ai_state: Arc<RwLock<AiStudioState>>,
}

impl AppState {
    pub fn new() -> Self {
        Self {
            telemetry: Arc::new(RwLock::new(StreamTelemetry::default())),
            camera_controls: Arc::new(RwLock::new(CameraControls::default())),
            ai_state: Arc::new(RwLock::new(AiStudioState::default())),
        }
    }
}
