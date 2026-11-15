import React from 'react';
import {
  Sparkles,
  UserCheck,
  Eye,
  Smile,
  SunMedium,
  Volume2,
  Image,
  Palette,
  Cpu,
} from 'lucide-react';
import type { AiStudioState } from '../types';

interface AiStudioPanelProps {
  aiState: AiStudioState;
  onChange: (updates: Partial<AiStudioState>) => void;
}

export const AiStudioPanel: React.FC<AiStudioPanelProps> = ({ aiState, onChange }) => {
  const bgModes: { id: AiStudioState['background_mode']; label: string; desc: string }[] = [
    { id: 'NONE', label: 'Original', desc: 'Passthrough camera feed' },
    { id: 'BLUR', label: 'Portrait Blur', desc: 'Neural bokeh background' },
    { id: 'REPLACE', label: 'Virtual BG', desc: 'Custom studio backdrop' },
    { id: 'GREEN_SCREEN', label: 'Green Screen', desc: 'Hardware chroma-keying' },
  ];

  return (
    <div className="flex flex-col gap-6 p-6 rounded-2xl bg-studio-card border border-slate-800 shadow-xl overflow-y-auto max-h-[calc(100vh-140px)]">
      {/* Header */}
      <div className="flex items-center justify-between pb-3 border-b border-slate-800">
        <div className="flex items-center gap-2">
          <Sparkles className="w-5 h-5 text-purple-400" />
          <h2 className="text-base font-semibold text-white">Local AI Neural Studio</h2>
        </div>
        <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-purple-500/10 border border-purple-500/30 text-[11px] font-medium text-purple-300">
          <Cpu className="w-3.5 h-3.5" />
          <span>DirectML / ONNX Local Acceleration</span>
        </div>
      </div>

      {/* Background Segmentation Mode */}
      <div className="flex flex-col gap-2">
        <label className="text-xs font-semibold text-slate-300 uppercase tracking-wider">
          Background Segmentation
        </label>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
          {bgModes.map((m) => (
            <button
              key={m.id}
              onClick={() => onChange({ background_mode: m.id })}
              className={`flex flex-col items-center justify-center p-3 rounded-xl border text-center transition-all ${
                aiState.background_mode === m.id
                  ? 'bg-purple-600/20 border-purple-500 text-white shadow-lg shadow-purple-500/10'
                  : 'bg-slate-900/60 border-slate-800 text-slate-400 hover:border-slate-700 hover:text-slate-200'
              }`}
            >
              <span className="text-xs font-bold">{m.label}</span>
              <span className="text-[10px] text-slate-400">{m.desc}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Blur Intensity Slider */}
      {aiState.background_mode === 'BLUR' && (
        <div className="flex flex-col gap-2 p-4 rounded-xl bg-purple-950/20 border border-purple-900/40">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-purple-200">Bokeh Blur Intensity</span>
            <span className="font-mono text-xs text-purple-300 font-bold">
              {Math.round(aiState.blur_intensity * 100)}%
            </span>
          </div>
          <input
            type="range"
            min="0.1"
            max="1.0"
            step="0.05"
            value={aiState.blur_intensity}
            onChange={(e) => onChange({ blur_intensity: parseFloat(e.target.value) })}
            className="w-full accent-purple-500 bg-slate-800 rounded-lg h-2 cursor-pointer"
          />
        </div>
      )}

      {/* Green Screen Chroma Key Controls */}
      {aiState.background_mode === 'GREEN_SCREEN' && (
        <div className="flex items-center justify-between p-4 rounded-xl bg-emerald-950/20 border border-emerald-900/40">
          <div className="flex items-center gap-3">
            <Palette className="w-5 h-5 text-emerald-400" />
            <div>
              <div className="text-xs font-bold text-white">Chroma Key Color</div>
              <div className="text-[11px] text-slate-400">Match physical backdrop</div>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <input
              type="color"
              value={aiState.green_screen_hex}
              onChange={(e) => onChange({ green_screen_hex: e.target.value })}
              className="w-8 h-8 rounded border border-slate-700 cursor-pointer bg-transparent"
            />
            <span className="font-mono text-xs text-emerald-300">{aiState.green_screen_hex}</span>
          </div>
        </div>
      )}

      {/* Neural AI Toggles */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        {/* Face Tracking & Auto-Framing */}
        <button
          onClick={() => onChange({ face_tracking: !aiState.face_tracking })}
          className={`flex items-start gap-3 p-3.5 rounded-xl border text-left transition-all ${
            aiState.face_tracking
              ? 'bg-indigo-600/20 border-indigo-500/50 text-white'
              : 'bg-slate-900/50 border-slate-800 text-slate-300 hover:border-slate-700'
          }`}
        >
          <UserCheck className="w-5 h-5 mt-0.5 text-indigo-400 shrink-0" />
          <div>
            <div className="text-xs font-bold flex items-center gap-1.5">
              <span>Cinematic Auto-Framing</span>
              <span
                className={`text-[10px] px-1.5 py-0.5 rounded font-mono ${
                  aiState.face_tracking ? 'bg-indigo-500 text-white' : 'bg-slate-800 text-slate-400'
                }`}
              >
                {aiState.face_tracking ? 'ON' : 'OFF'}
              </span>
            </div>
            <div className="text-[11px] text-slate-400 mt-0.5">
              Automatically keeps you centered in frame as you move
            </div>
          </div>
        </button>

        {/* Eye Contact Correction */}
        <button
          onClick={() => onChange({ eye_contact: !aiState.eye_contact })}
          className={`flex items-start gap-3 p-3.5 rounded-xl border text-left transition-all ${
            aiState.eye_contact
              ? 'bg-cyan-600/20 border-cyan-500/50 text-white'
              : 'bg-slate-900/50 border-slate-800 text-slate-300 hover:border-slate-700'
          }`}
        >
          <Eye className="w-5 h-5 mt-0.5 text-cyan-400 shrink-0" />
          <div>
            <div className="text-xs font-bold flex items-center gap-1.5">
              <span>Eye Contact Correction</span>
              <span
                className={`text-[10px] px-1.5 py-0.5 rounded font-mono ${
                  aiState.eye_contact ? 'bg-cyan-500 text-white' : 'bg-slate-800 text-slate-400'
                }`}
              >
                {aiState.eye_contact ? 'ON' : 'OFF'}
              </span>
            </div>
            <div className="text-[11px] text-slate-400 mt-0.5">
              Adjusts gaze so you appear to look directly at the audience
            </div>
          </div>
        </button>

        {/* Studio Lighting Boost */}
        <button
          onClick={() => onChange({ low_light_boost: !aiState.low_light_boost })}
          className={`flex items-start gap-3 p-3.5 rounded-xl border text-left transition-all ${
            aiState.low_light_boost
              ? 'bg-amber-600/20 border-amber-500/50 text-white'
              : 'bg-slate-900/50 border-slate-800 text-slate-300 hover:border-slate-700'
          }`}
        >
          <SunMedium className="w-5 h-5 mt-0.5 text-amber-400 shrink-0" />
          <div>
            <div className="text-xs font-bold flex items-center gap-1.5">
              <span>Studio Lighting Enhancer</span>
              <span
                className={`text-[10px] px-1.5 py-0.5 rounded font-mono ${
                  aiState.low_light_boost ? 'bg-amber-500 text-white' : 'bg-slate-800 text-slate-400'
                }`}
              >
                {aiState.low_light_boost ? 'ON' : 'OFF'}
              </span>
            </div>
            <div className="text-[11px] text-slate-400 mt-0.5">
              Intelligent multi-scale tone mapping for dim environments
            </div>
          </div>
        </button>

        {/* Neural Audio Denoiser (RNNoise) */}
        <button
          onClick={() => onChange({ noise_reduction: !aiState.noise_reduction })}
          className={`flex items-start gap-3 p-3.5 rounded-xl border text-left transition-all ${
            aiState.noise_reduction
              ? 'bg-emerald-600/20 border-emerald-500/50 text-white'
              : 'bg-slate-900/50 border-slate-800 text-slate-300 hover:border-slate-700'
          }`}
        >
          <Volume2 className="w-5 h-5 mt-0.5 text-emerald-400 shrink-0" />
          <div>
            <div className="text-xs font-bold flex items-center gap-1.5">
              <span>RNNoise Audio Denoiser</span>
              <span
                className={`text-[10px] px-1.5 py-0.5 rounded font-mono ${
                  aiState.noise_reduction ? 'bg-emerald-500 text-white' : 'bg-slate-800 text-slate-400'
                }`}
              >
                {aiState.noise_reduction ? 'ON' : 'OFF'}
              </span>
            </div>
            <div className="text-[11px] text-slate-400 mt-0.5">
              Eliminates AC hum, keyboard clicks, and fan whine in real time
            </div>
          </div>
        </button>
      </div>

      {/* Face Beautification Slider */}
      <div className="flex flex-col gap-2 p-4 rounded-xl bg-slate-900/50 border border-slate-800">
        <div className="flex items-center justify-between">
          <span className="text-xs font-medium text-slate-300 flex items-center gap-1.5">
            <Smile className="w-4 h-4 text-pink-400" /> Face Beautification & Skin Softening
          </span>
          <span className="font-mono text-xs text-pink-300 font-bold">
            {aiState.beautification === 0 ? 'OFF' : `${Math.round(aiState.beautification * 100)}%`}
          </span>
        </div>
        <input
          type="range"
          min="0.0"
          max="1.0"
          step="0.05"
          value={aiState.beautification}
          onChange={(e) => onChange({ beautification: parseFloat(e.target.value) })}
          className="w-full accent-pink-500 bg-slate-800 rounded-lg h-1.5 cursor-pointer"
        />
      </div>
    </div>
  );
};
