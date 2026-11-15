export type ConnectionState =
  | 'Idle'
  | 'Listening'
  | 'Handshaking'
  | 'Connected'
  | 'Streaming'
  | 'Reconnecting'
  | 'Error';

export interface StreamTelemetry {
  state: ConnectionState;
  fps: number;
  bitrate_kbps: number;
  latency_ms: number;
  jitter_ms: number;
  packet_loss_pct: number;
  resolution: string;
  active_transport: 'USB' | 'Wi-Fi LAN' | 'Wi-Fi Direct' | 'Bluetooth';
  battery_level: number;
  battery_temp_c: number;
  cpu_usage_pct: number;
  memory_mb: number;
}

export type LensType = 'FRONT' | 'WIDE' | 'ULTRA_WIDE' | 'TELEPHOTO';

export interface CameraControls {
  lens: LensType;
  zoom_ratio: number;
  torch_on: boolean;
  iso: number;              // 0 = Auto
  exposure_time_ns: number; // 0 = Auto
  white_balance_k: number;  // 0 = Auto
  focus_distance: number;   // -1 = Auto
  stabilization: boolean;
  hdr: boolean;
}

export interface AiStudioState {
  background_mode: 'NONE' | 'BLUR' | 'REPLACE' | 'GREEN_SCREEN';
  blur_intensity: number;
  virtual_bg_path: string | null;
  green_screen_hex: string;
  face_tracking: boolean;
  eye_contact: boolean;
  beautification: number;
  low_light_boost: boolean;
  noise_reduction: boolean;
}

export interface PairedDevice {
  device_id: string;
  device_name: string;
  client_public_key: string;
  paired_at: string;
  auto_connect: boolean;
  preferred_transport: string;
}

export interface DriverStatus {
  virtualCameraInstalled: boolean;
  virtualMicInstalled: boolean;
  driverName: string;
}

export interface PairingQrData {
  v: number;
  ip: string;
  udpPort: number;
  tcpPort: number;
  pin: string;
  serverPubKey: string;
  hostname: string;
}
