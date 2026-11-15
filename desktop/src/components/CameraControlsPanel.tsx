import React from 'react';
import {
  Sliders,
  Sun,
  Eye,
  Focus,
  Zap,
  Shield,
  Sparkles,
  Camera,
} from 'lucide-react';
import type { CameraControls, LensType } from '../types';

interface CameraControlsPanelProps {
  controls: CameraControls;
  onChange: (updates: Partial<CameraControls>) => void;
}

export const CameraControlsPanel: React.FC<CameraControlsPanelProps> = ({
  controls,
  onChange,
}) => {
  const lenses: { id: LensType; label: string; desc: string }[] = [
    { id: 'FRONT', label: 'Front Lens', desc: 'TrueDepth / Wide' },
    { id: 'ULTRA_WIDE', label: 'Ultra-Wide', desc: '0.5x 120° FOV' },
    { id: 'WIDE', label: 'Main Wide', desc: '1.0x Optical' },
    { id: 'TELEPHOTO', label: 'Telephoto', desc: '3.0x - 5.0x' },
  ];

  const zoomPresets = [0.5, 1.0, 2.0, 3.0, 5.0, 10.0];

  return (
    <div className="flex flex-col gap-6 p-6 rounded-2xl bg-studio-card border border-slate-800 shadow-xl overflow-y-auto max-h-[calc(100vh-140px)]">
      {/* Header */}
      <div className="flex items-center justify-between pb-3 border-b border-slate-800">
        <div className="flex items-center gap-2">
          <Sliders className="w-5 h-5 text-indigo-400" />
          <h2 className="text-base font-semibold text-white">Camera Hardware Controls</h2>
        </div>
        <button
          onClick={() =>
            onChange({
              iso: 0,
              exposure_time_ns: 0,
              white_balance_k: 0,
              focus_distance: -1,
              zoom_ratio: 1.0,
            })
          }
          className="text-xs text-indigo-400 hover:text-indigo-300 font-medium transition-colors"
        >
          Reset to Auto
        </button>
      </div>

      {/* Lens Selection */}
      <div className="flex flex-col gap-2">
        <label className="text-xs font-semibold text-slate-300 uppercase tracking-wider">
          Active Camera Sensor
        </label>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
          {lenses.map((l) => (
            <button
              key={l.id}
              onClick={() => onChange({ lens: l.id })}
              className={`flex flex-col items-center justify-center p-3 rounded-xl border text-center transition-all ${
                controls.lens === l.id
                  ? 'bg-indigo-600/20 border-indigo-500 text-white shadow-lg shadow-indigo-500/10'
                  : 'bg-slate-900/60 border-slate-800 text-slate-400 hover:border-slate-700 hover:text-slate-200'
              }`}
            >
              <Camera className="w-4 h-4 mb-1 text-indigo-400" />
              <span className="text-xs font-bold">{l.label}</span>
              <span className="text-[10px] text-slate-400">{l.desc}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Zoom Controls */}
      <div className="flex flex-col gap-2">
        <div className="flex items-center justify-between">
          <label className="text-xs font-semibold text-slate-300 uppercase tracking-wider">
            Optical & Digital Zoom
          </label>
          <span className="font-mono text-sm font-bold text-indigo-400">
            {controls.zoom_ratio.toFixed(1)}x
          </span>
        </div>
        <input
          type="range"
          min="0.5"
          max="10.0"
          step="0.1"
          value={controls.zoom_ratio}
          onChange={(e) => onChange({ zoom_ratio: parseFloat(e.target.value) })}
          className="w-full accent-indigo-500 bg-slate-800 rounded-lg h-2 cursor-pointer"
        />
        <div className="flex items-center justify-between gap-1 mt-1">
          {zoomPresets.map((z) => (
            <button
              key={z}
              onClick={() => onChange({ zoom_ratio: z })}
              className={`px-2.5 py-1 rounded-lg text-xs font-mono font-semibold transition-all ${
                Math.abs(controls.zoom_ratio - z) < 0.05
                  ? 'bg-indigo-600 text-white'
                  : 'bg-slate-800 text-slate-400 hover:bg-slate-700 hover:text-white'
              }`}
            >
              {z}x
            </button>
          ))}
        </div>
      </div>

      {/* Exposure & ISO Controls */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* ISO */}
        <div className="flex flex-col gap-2 p-3.5 rounded-xl bg-slate-900/50 border border-slate-800">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-300 flex items-center gap-1.5">
              <Sun className="w-3.5 h-3.5 text-amber-400" /> ISO Sensitivity
            </span>
            <span className="font-mono text-xs text-amber-300 font-bold">
              {controls.iso === 0 ? 'AUTO' : controls.iso}
            </span>
          </div>
          <input
            type="range"
            min="0"
            max="3200"
            step="50"
            value={controls.iso}
            onChange={(e) => onChange({ iso: parseInt(e.target.value) })}
            className="w-full accent-amber-500 bg-slate-800 rounded-lg h-1.5 cursor-pointer"
          />
        </div>

        {/* White Balance */}
        <div className="flex flex-col gap-2 p-3.5 rounded-xl bg-slate-900/50 border border-slate-800">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-300 flex items-center gap-1.5">
              <Eye className="w-3.5 h-3.5 text-cyan-400" /> White Balance
            </span>
            <span className="font-mono text-xs text-cyan-300 font-bold">
              {controls.white_balance_k === 0 ? 'AUTO' : `${controls.white_balance_k}K`}
            </span>
          </div>
          <input
            type="range"
            min="0"
            max="8000"
            step="100"
            value={controls.white_balance_k}
            onChange={(e) => onChange({ white_balance_k: parseInt(e.target.value) })}
            className="w-full accent-cyan-500 bg-slate-800 rounded-lg h-1.5 cursor-pointer"
          />
        </div>
      </div>

      {/* Manual Focus */}
      <div className="flex flex-col gap-2 p-3.5 rounded-xl bg-slate-900/50 border border-slate-800">
        <div className="flex items-center justify-between">
          <span className="text-xs font-medium text-slate-300 flex items-center gap-1.5">
            <Focus className="w-3.5 h-3.5 text-emerald-400" /> Focus Distance
          </span>
          <span className="font-mono text-xs text-emerald-300 font-bold">
            {controls.focus_distance < 0
              ? 'AUTO FOCUS (Continuous AF)'
              : controls.focus_distance === 0
              ? 'Infinity'
              : `${(10 / (controls.focus_distance + 0.1)).toFixed(1)} cm`}
          </span>
        </div>
        <input
          type="range"
          min="-1"
          max="10"
          step="0.2"
          value={controls.focus_distance}
          onChange={(e) => onChange({ focus_distance: parseFloat(e.target.value) })}
          className="w-full accent-emerald-500 bg-slate-800 rounded-lg h-1.5 cursor-pointer"
        />
      </div>

      {/* Toggles (Stabilization, HDR, Flashlight) */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <button
          onClick={() => onChange({ torch_on: !controls.torch_on })}
          className={`flex items-center justify-between p-3 rounded-xl border text-xs font-medium transition-all ${
            controls.torch_on
              ? 'bg-amber-500/20 border-amber-500/50 text-amber-200'
              : 'bg-slate-900/50 border-slate-800 text-slate-300 hover:border-slate-700'
          }`}
        >
          <span className="flex items-center gap-1.5">
            <Zap className="w-4 h-4 text-amber-400" /> Flashlight
          </span>
          <span className="font-bold">{controls.torch_on ? 'ON' : 'OFF'}</span>
        </button>

        <button
          onClick={() => onChange({ stabilization: !controls.stabilization })}
          className={`flex items-center justify-between p-3 rounded-xl border text-xs font-medium transition-all ${
            controls.stabilization
              ? 'bg-indigo-500/20 border-indigo-500/50 text-indigo-200'
              : 'bg-slate-900/50 border-slate-800 text-slate-300 hover:border-slate-700'
          }`}
        >
          <span className="flex items-center gap-1.5">
            <Shield className="w-4 h-4 text-indigo-400" /> Hardware OIS
          </span>
          <span className="font-bold">{controls.stabilization ? 'LOCK' : 'OFF'}</span>
        </button>

        <button
          onClick={() => onChange({ hdr: !controls.hdr })}
          className={`flex items-center justify-between p-3 rounded-xl border text-xs font-medium transition-all ${
            controls.hdr
              ? 'bg-purple-500/20 border-purple-500/50 text-purple-200'
              : 'bg-slate-900/50 border-slate-800 text-slate-300 hover:border-slate-700'
          }`}
        >
          <span className="flex items-center gap-1.5">
            <Sparkles className="w-4 h-4 text-purple-400" /> HDR10 / Night
          </span>
          <span className="font-bold">{controls.hdr ? 'ACTIVE' : 'OFF'}</span>
        </button>
      </div>
    </div>
  );
};
