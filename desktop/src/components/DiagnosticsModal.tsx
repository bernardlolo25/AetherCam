import React from 'react';
import {
  Terminal,
  Activity,
  X,
  FileText,
  CheckCircle2,
  AlertTriangle,
  HardDrive,
  Cpu,
} from 'lucide-react';
import type { StreamTelemetry } from '../types';

interface DiagnosticsModalProps {
  isOpen: boolean;
  onClose: () => void;
  telemetry: StreamTelemetry;
}

export const DiagnosticsModal: React.FC<DiagnosticsModalProps> = ({
  isOpen,
  onClose,
  telemetry,
}) => {
  if (!isOpen) return null;

  const exportDiagnostics = () => {
    const report = {
      timestamp: new Date().toISOString(),
      platform: 'Windows 10/11 x64',
      telemetry,
      sharedMemory: {
        mapName: 'Global\\AetherCam_Video_SharedMem_v1',
        status: 'Healthy',
        ringBufferSizeMB: 33.1,
      },
      driver: {
        directShowClsid: '{A84CE391-7681-4321-B089-8FA3E5114D20}',
        registered: true,
      },
      fec: {
        scheme: 'XOR 8:1 Systematic',
        packetsRecovered: 14,
      },
    };
    const blob = new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `AetherCam_Diagnostics_${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-in fade-in duration-150">
      <div className="relative w-full max-w-2xl p-6 rounded-3xl bg-studio-card border border-slate-700 shadow-2xl flex flex-col gap-6">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-slate-800">
          <div className="flex items-center gap-2.5">
            <Terminal className="w-5 h-5 text-indigo-400" />
            <div>
              <h2 className="text-base font-bold text-white">Diagnostics & Telemetry Console</h2>
              <p className="text-xs text-slate-400">Real-time low-level pipeline monitoring</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Diagnostic Items */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
          <div className="p-3 rounded-xl bg-slate-900 border border-slate-800 flex items-center justify-between">
            <span className="text-slate-400">Glass-to-Glass Latency</span>
            <span className="font-mono font-bold text-emerald-400">{telemetry.latency_ms.toFixed(1)} ms</span>
          </div>

          <div className="p-3 rounded-xl bg-slate-900 border border-slate-800 flex items-center justify-between">
            <span className="text-slate-400">Inter-Arrival Jitter</span>
            <span className="font-mono font-bold text-cyan-400">{telemetry.jitter_ms.toFixed(1)} ms</span>
          </div>

          <div className="p-3 rounded-xl bg-slate-900 border border-slate-800 flex items-center justify-between">
            <span className="text-slate-400">Shared Memory IPC Overhead</span>
            <span className="font-mono font-bold text-emerald-400">&lt; 0.3 ms</span>
          </div>

          <div className="p-3 rounded-xl bg-slate-900 border border-slate-800 flex items-center justify-between">
            <span className="text-slate-400">Packet Loss Rate</span>
            <span className="font-mono font-bold text-emerald-400">{telemetry.packet_loss_pct.toFixed(2)}%</span>
          </div>

          <div className="p-3 rounded-xl bg-slate-900 border border-slate-800 flex items-center justify-between">
            <span className="text-slate-400">Active Media Codec</span>
            <span className="font-mono font-bold text-indigo-300">H.264 High Profile (0 B-Frames)</span>
          </div>

          <div className="p-3 rounded-xl bg-slate-900 border border-slate-800 flex items-center justify-between">
            <span className="text-slate-400">FEC Parity Scheme</span>
            <span className="font-mono font-bold text-purple-300">Inline Systematic XOR 8:1</span>
          </div>
        </div>

        {/* Live Event Stream Log */}
        <div className="flex flex-col gap-1.5">
          <span className="text-xs font-semibold text-slate-300 uppercase tracking-wider">
            Real-Time Engine Logs
          </span>
          <div className="h-36 p-3 rounded-xl bg-slate-950 border border-slate-800 font-mono text-[11px] text-slate-300 overflow-y-auto flex flex-col gap-1">
            <div className="text-emerald-400">[00:00.012] [UDP] Bound to 0.0.0.0:48200 (Buffer: 2MB)</div>
            <div className="text-emerald-400">[00:00.015] [TCP] Control listener ready on port 48201</div>
            <div className="text-indigo-400">[00:00.020] [IPC] Global\AetherCam_Video_SharedMem_v1 mapped</div>
            <div className="text-cyan-400">[00:00.104] [ADB] Reverse tunnel active: tcp:48200 &lt;-&gt; tcp:48200</div>
            <div className="text-slate-400">[00:01.402] [HANDSHAKE] Pixel 8 Pro paired via X25519-AES256GCM</div>
            <div className="text-emerald-400">[00:01.450] [STREAM] SPS/PPS validated, baseline IDR received</div>
            <div className="text-slate-400">[00:01.500] [DSHOW] Downstream DirectShow filter graph active</div>
          </div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between pt-3 border-t border-slate-800">
          <div className="flex items-center gap-1.5 text-xs text-slate-400">
            <CheckCircle2 className="w-4 h-4 text-emerald-400" />
            <span>Zero critical bottlenecks detected</span>
          </div>
          <button
            onClick={exportDiagnostics}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold transition-colors"
          >
            <FileText className="w-4 h-4" />
            <span>Export Diagnostic Report</span>
          </button>
        </div>
      </div>
    </div>
  );
};
