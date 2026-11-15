import { useState, useEffect, useCallback } from 'react';
import type { StreamTelemetry, CameraControls, AiStudioState, PairedDevice, DriverStatus, PairingQrData } from '../types';

// Check if running inside Tauri runtime
const isTauri = typeof window !== 'undefined' && '__TAURI_IPC__' in window;

async function invokeTauri<T>(cmd: string, args?: Record<string, any>): Promise<T> {
  if (isTauri) {
    const { invoke } = await import('@tauri-apps/api/tauri');
    return invoke<T>(cmd, args);
  }

  // Graceful browser mock fallback
  switch (cmd) {
    case 'get_stream_telemetry':
      try {
        const resp = await fetch('http://127.0.0.1:48202/api/telemetry');
        if (resp.ok) return (await resp.json()) as unknown as T;
      } catch {}
      return {
        state: 'Waiting for Phone',
        fps: 0,
        bitrate_kbps: 0,
        latency_ms: 0,
        jitter_ms: 0,
        packet_loss_pct: 0,
        resolution: '1920x1080',
        active_transport: 'Disconnected',
        battery_level: 0,
        battery_temp_c: 0,
        cpu_usage_pct: 0,
        memory_mb: 0,
      } as unknown as T;
    case 'get_camera_controls':
      return {
        lens: 'WIDE',
        zoom_ratio: 1.0,
        torch_on: false,
        iso: 0,
        exposure_time_ns: 0,
        white_balance_k: 0,
        focus_distance: -1,
        stabilization: true,
        hdr: true,
      } as unknown as T;
    case 'get_ai_state':
      return {
        background_mode: 'BLUR',
        blur_intensity: 0.65,
        virtual_bg_path: null,
        green_screen_hex: '#00FF00',
        face_tracking: true,
        eye_contact: true,
        beautification: 0.2,
        low_light_boost: false,
        noise_reduction: true,
      } as unknown as T;
    case 'check_driver_status':
      return {
        virtualCameraInstalled: true,
        virtualMicInstalled: true,
        driverName: 'AetherCam Virtual Camera',
      } as unknown as T;
    case 'get_pairing_qr_data':
      // Try to fetch real IP/PIN from the running desktop server
      try {
        const resp = await fetch('http://127.0.0.1:48202/api/qr-data');
        if (resp.ok) return (await resp.json()) as unknown as T;
      } catch { /* server not running — fall through to mock */ }
      return {
        v: 2,
        ip: '192.168.1.1',
        udpPort: 48200,
        tcpPort: 48201,
        pin: '849201',
        serverPubKey: 'BM7v9LqP3rM1z0NvQ8sYt4XuKgH2jR5wFpE6aN8=',
        hostname: 'AetherCam-PC',
      } as unknown as T;
    case 'get_paired_devices':
      return [
        {
          device_id: 'pixel8_pro_99a',
          device_name: 'Google Pixel 8 Pro',
          client_public_key: 'wK6f9LqP3rM1z0Nv...',
          paired_at: '2026-11-14 18:32',
          auto_connect: true,
          preferred_transport: 'USB',
        },
      ] as unknown as T;
    default:
      return true as unknown as T;
  }
}

export function useStreamTelemetry() {
  const [telemetry, setTelemetry] = useState<StreamTelemetry>({
    state: 'Waiting for Phone' as any,
    fps: 0,
    bitrate_kbps: 0,
    latency_ms: 0,
    jitter_ms: 0,
    packet_loss_pct: 0,
    resolution: '1920x1080',
    active_transport: 'Disconnected' as any,
    battery_level: 0,
    battery_temp_c: 0,
    cpu_usage_pct: 0,
    memory_mb: 0,
  });

  useEffect(() => {
    const interval = setInterval(async () => {
      try {
        const data = await invokeTauri<StreamTelemetry>('get_stream_telemetry');
        setTelemetry(data);
      } catch (err) {
        console.error('Failed to fetch stream telemetry', err);
      }
    }, 1000);
    return () => clearInterval(interval);
  }, []);

  return telemetry;
}

export { invokeTauri };
