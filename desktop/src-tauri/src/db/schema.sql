-- AetherCam Desktop Persistence Schema (SQLite)

CREATE TABLE IF NOT EXISTS paired_devices (
    device_id TEXT PRIMARY KEY,
    device_name TEXT NOT NULL,
    client_public_key TEXT NOT NULL,
    paired_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_seen_at DATETIME,
    auto_connect INTEGER DEFAULT 1,
    preferred_transport TEXT DEFAULT 'AUTO'
);

CREATE TABLE IF NOT EXISTS stream_profiles (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    resolution_width INTEGER NOT NULL,
    resolution_height INTEGER NOT NULL,
    target_fps INTEGER NOT NULL,
    bitrate_bps INTEGER NOT NULL,
    codec TEXT NOT NULL DEFAULT 'H264'
);

CREATE TABLE IF NOT EXISTS ai_presets (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    background_mode TEXT NOT NULL DEFAULT 'NONE',
    blur_intensity REAL DEFAULT 0.5,
    virtual_background_path TEXT,
    face_tracking INTEGER DEFAULT 0,
    eye_contact INTEGER DEFAULT 0,
    beautification REAL DEFAULT 0.0,
    low_light_boost INTEGER DEFAULT 0,
    noise_suppression INTEGER DEFAULT 1
);

CREATE TABLE IF NOT EXISTS connection_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL,
    transport TEXT NOT NULL,
    duration_seconds INTEGER DEFAULT 0,
    avg_latency_ms REAL DEFAULT 0.0,
    avg_bitrate_kbps REAL DEFAULT 0.0,
    packet_loss_rate REAL DEFAULT 0.0,
    connected_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Seed default profiles
INSERT OR IGNORE INTO stream_profiles (id, name, resolution_width, resolution_height, target_fps, bitrate_bps, codec)
VALUES
    ('profile_1080p60_lowlat', '1080p 60fps Ultra-Low Latency', 1920, 1080, 60, 12000000, 'H264'),
    ('profile_4k30_broadcast', '4K 30fps Cinema Broadcast', 3840, 2160, 30, 25000000, 'H265'),
    ('profile_720p60_esports', '720p 60fps High Responsiveness', 1280, 720, 60, 6000000, 'H264'),
    ('profile_meeting_economy', '1080p 30fps Office & Zoom', 1920, 1080, 30, 5000000, 'H264');

-- Seed default AI preset
INSERT OR IGNORE INTO ai_presets (id, name, background_mode, blur_intensity, face_tracking, eye_contact, beautification, noise_suppression)
VALUES ('default_ai', 'Executive Studio', 'BLUR', 0.65, 1, 1, 0.25, 1);
