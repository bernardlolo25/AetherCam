import React from 'react';
import {
  Activity,
  Gauge,
  Wifi,
  Cpu,
  HardDrive,
  BatteryCharging,
  Thermometer,
  ShieldCheck,
  TrendingUp,
} from 'lucide-react';
import type { StreamTelemetry } from '../types';
import { formatBitrate, formatLatencyColor } from '../utils/formatters';

interface StreamAnalyticsProps {
  telemetry: StreamTelemetry;
}

export const StreamAnalytics: React.FC<StreamAnalyticsProps> = ({ telemetry }) => {
  const latencyBadgeClass = formatLatencyColor(telemetry.latency_ms);

  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-4 w-full">
      {/* Bitrate Card */}
      <div className="flex flex-col p-4 rounded-2xl bg-studio-card border border-slate-800 shadow-lg">
        <div className="flex items-center justify-between text-slate-400 text-xs font-medium">
          <span className="flex items-center gap-1.5">
            <Gauge className="w-4 h-4 text-indigo-400" />
            Active Bitrate
          </span>
          <span className="text-emerald-400 text-[11px] font-mono">CBR</span>
        </div>
        <div className="mt-2 text-2xl font-bold font-mono text-white">
          {formatBitrate(telemetry.bitrate_kbps)}
        </div>
        <div className="mt-1 flex items-center gap-1 text-[11px] text-slate-400">
          <TrendingUp className="w-3 h-3 text-emerald-400" />
          <span>Encoder Target: 12.0 Mbps</span>
        </div>
      </div>

      {/* Frame Rate Card */}
      <div className="flex flex-col p-4 rounded-2xl bg-studio-card border border-slate-800 shadow-lg">
        <div className="flex items-center justify-between text-slate-400 text-xs font-medium">
          <span className="flex items-center gap-1.5">
            <Activity className="w-4 h-4 text-cyan-400" />
            Framerate
          </span>
          <span className="text-cyan-400 text-[11px] font-mono">Smooth</span>
        </div>
        <div className="mt-2 text-2xl font-bold font-mono text-white">
          {telemetry.fps} <span className="text-sm font-normal text-slate-400">FPS</span>
        </div>
        <div className="mt-1 text-[11px] text-slate-400">
          Drop rate: <span className="text-emerald-400 font-mono">0 frames (0.0%)</span>
        </div>
      </div>

      {/* Glass-to-Glass Latency Card */}
      <div className="flex flex-col p-4 rounded-2xl bg-studio-card border border-slate-800 shadow-lg">
        <div className="flex items-center justify-between text-slate-400 text-xs font-medium">
          <span className="flex items-center gap-1.5">
            <Wifi className="w-4 h-4 text-emerald-400" />
            Glass Latency
          </span>
          <span className={`px-2 py-0.5 rounded-full border text-[10px] font-mono ${latencyBadgeClass}`}>
            Target &lt; 20ms
          </span>
        </div>
        <div className="mt-2 text-2xl font-bold font-mono text-white">
          {telemetry.latency_ms.toFixed(1)}{' '}
          <span className="text-sm font-normal text-slate-400">ms</span>
        </div>
        <div className="mt-1 text-[11px] text-slate-400">
          Jitter: <span className="text-slate-300 font-mono">{telemetry.jitter_ms.toFixed(1)} ms</span> | Loss:{' '}
          <span className="text-emerald-400 font-mono">{telemetry.packet_loss_pct.toFixed(2)}%</span>
        </div>
      </div>

      {/* Hardware Performance Card */}
      <div className="flex flex-col p-4 rounded-2xl bg-studio-card border border-slate-800 shadow-lg">
        <div className="flex items-center justify-between text-slate-400 text-xs font-medium">
          <span className="flex items-center gap-1.5">
            <Cpu className="w-4 h-4 text-purple-400" />
            Host Resources
          </span>
          <span className="text-purple-400 text-[11px] font-mono">D3D11VA</span>
        </div>
        <div className="mt-2 text-2xl font-bold font-mono text-white">
          {telemetry.cpu_usage_pct.toFixed(1)}%{' '}
          <span className="text-sm font-normal text-slate-400">CPU</span>
        </div>
        <div className="mt-1 text-[11px] text-slate-400 flex items-center justify-between">
          <span className="flex items-center gap-1">
            <HardDrive className="w-3 h-3 text-slate-400" />
            RAM: {telemetry.memory_mb.toFixed(0)} MB
          </span>
          <span className="text-emerald-400 text-[10px] font-semibold">Under Budget</span>
        </div>
      </div>

      {/* Phone Thermal & Battery Sub-strip */}
      <div className="col-span-2 md:col-span-4 flex items-center justify-between px-4 py-2.5 rounded-xl bg-slate-900/60 border border-slate-800 text-xs text-slate-300">
        <div className="flex items-center gap-6">
          <div className="flex items-center gap-2">
            <BatteryCharging className="w-4 h-4 text-emerald-400" />
            <span>Phone Battery:</span>
            <span className="font-mono font-bold text-white">{telemetry.battery_level.toFixed(0)}%</span>
          </div>
          <div className="flex items-center gap-2">
            <Thermometer className="w-4 h-4 text-amber-400" />
            <span>Battery Temp:</span>
            <span className="font-mono font-bold text-white">{telemetry.battery_temp_c.toFixed(1)}°C</span>
          </div>
          <div className="flex items-center gap-2">
            <ShieldCheck className="w-4 h-4 text-indigo-400" />
            <span>Hardware FEC:</span>
            <span className="text-indigo-300 font-medium">XOR 8:1 Parity Active</span>
          </div>
        </div>
        <div className="flex items-center gap-2 font-mono text-[11px] text-slate-400">
          <span>ABSP v2.1 Monotonic Clock Sync</span>
        </div>
      </div>
    </div>
  );
};
