import React, { useState } from 'react';
import {
  Camera,
  Sliders,
  Sparkles,
  Layers,
  Activity,
  Smartphone,
  Terminal,
  Settings,
  HelpCircle,
} from 'lucide-react';
import { VideoPreview } from './components/VideoPreview';
import { StreamAnalytics } from './components/StreamAnalytics';
import { CameraControlsPanel } from './components/CameraControlsPanel';
import { AiStudioPanel } from './components/AiStudioPanel';
import { VirtualCamStatus } from './components/VirtualCamStatus';
import { DevicePairingModal } from './components/DevicePairingModal';
import { DiagnosticsModal } from './components/DiagnosticsModal';
import { useStreamTelemetry, invokeTauri } from './hooks/useTauriIpc';
import type { CameraControls, AiStudioState } from './types';

export const App: React.FC = () => {
  const telemetry = useStreamTelemetry();

  const [activeTab, setActiveTab] = useState<'studio' | 'controls' | 'ai' | 'drivers'>('studio');
  const [isPairingOpen, setIsPairingOpen] = useState(false);
  const [isDiagnosticsOpen, setIsDiagnosticsOpen] = useState(false);

  const [cameraControls, setCameraControls] = useState<CameraControls>({
    lens: 'WIDE',
    zoom_ratio: 1.0,
    torch_on: false,
    iso: 0,
    exposure_time_ns: 0,
    white_balance_k: 0,
    focus_distance: -1,
    stabilization: true,
    hdr: true,
  });

  const [aiState, setAiState] = useState<AiStudioState>({
    background_mode: 'BLUR',
    blur_intensity: 0.65,
    virtual_bg_path: null,
    green_screen_hex: '#00FF00',
    face_tracking: true,
    eye_contact: true,
    beautification: 0.2,
    low_light_boost: false,
    noise_reduction: true,
  });

  const handleUpdateCamera = async (updates: Partial<CameraControls>) => {
    const updated = { ...cameraControls, ...updates };
    setCameraControls(updated);
    try {
      await invokeTauri('set_camera_controls', { controls: updated });
    } catch (err) {
      console.error('Failed to sync camera controls', err);
    }
  };

  const handleUpdateAi = async (updates: Partial<AiStudioState>) => {
    const updated = { ...aiState, ...updates };
    setAiState(updated);
    try {
      await invokeTauri('set_ai_state', { newState: updated });
    } catch (err) {
      console.error('Failed to sync AI state', err);
    }
  };

  return (
    <div className="flex h-screen w-screen bg-studio-dark text-slate-100 overflow-hidden font-sans">
      {/* Sleek Studio Sidebar */}
      <aside className="w-16 sm:w-20 flex flex-col items-center justify-between py-5 bg-studio-card border-r border-slate-800 shrink-0 z-30">
        <div className="flex flex-col items-center gap-6 w-full">
          {/* Logo Mark */}
          <div className="w-10 h-10 rounded-2xl bg-gradient-to-tr from-indigo-600 to-cyan-400 p-0.5 shadow-lg shadow-indigo-500/20">
            <div className="w-full h-full bg-studio-dark rounded-[14px] flex items-center justify-center">
              <Camera className="w-5 h-5 text-indigo-400" />
            </div>
          </div>

          {/* Navigation Items */}
          <nav className="flex flex-col items-center gap-2 w-full px-2">
            <button
              onClick={() => setActiveTab('studio')}
              title="Studio Preview"
              className={`w-full aspect-square flex flex-col items-center justify-center rounded-2xl transition-all ${
                activeTab === 'studio'
                  ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-600/30'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800'
              }`}
            >
              <Camera className="w-5 h-5" />
              <span className="text-[10px] mt-1 font-medium">Studio</span>
            </button>

            <button
              onClick={() => setActiveTab('controls')}
              title="Camera Hardware Controls"
              className={`w-full aspect-square flex flex-col items-center justify-center rounded-2xl transition-all ${
                activeTab === 'controls'
                  ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-600/30'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800'
              }`}
            >
              <Sliders className="w-5 h-5" />
              <span className="text-[10px] mt-1 font-medium">Controls</span>
            </button>

            <button
              onClick={() => setActiveTab('ai')}
              title="AI Neural Effects"
              className={`w-full aspect-square flex flex-col items-center justify-center rounded-2xl transition-all ${
                activeTab === 'ai'
                  ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-600/30'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800'
              }`}
            >
              <Sparkles className="w-5 h-5" />
              <span className="text-[10px] mt-1 font-medium">AI Studio</span>
            </button>

            <button
              onClick={() => setActiveTab('drivers')}
              title="Virtual Webcam & Audio Driver"
              className={`w-full aspect-square flex flex-col items-center justify-center rounded-2xl transition-all ${
                activeTab === 'drivers'
                  ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-600/30'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800'
              }`}
            >
              <Layers className="w-5 h-5" />
              <span className="text-[10px] mt-1 font-medium">Drivers</span>
            </button>
          </nav>
        </div>

        {/* Bottom Utility Tools */}
        <div className="flex flex-col items-center gap-3 w-full px-2">
          <button
            onClick={() => setIsDiagnosticsOpen(true)}
            title="System Diagnostics & Logs"
            className="p-3 rounded-2xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
          >
            <Terminal className="w-5 h-5" />
          </button>
        </div>
      </aside>

      {/* Main Studio Viewport */}
      <main className="flex-1 flex flex-col h-full overflow-hidden">
        {/* Top Header Bar */}
        <header className="h-16 flex items-center justify-between px-6 border-b border-slate-800 bg-studio-card/60 backdrop-blur-md shrink-0 z-20">
          <div className="flex items-center gap-3">
            <h1 className="text-base font-bold tracking-tight text-white flex items-center gap-2">
              <span>AetherCam</span>
              <span className="text-xs px-2 py-0.5 rounded-full bg-indigo-500/10 text-indigo-400 font-mono font-semibold border border-indigo-500/30">
                PRO v2.1
              </span>
            </h1>
            <span className="text-slate-600 hidden sm:inline">|</span>
            <div className="hidden sm:flex items-center gap-2 text-xs text-slate-300">
              <span
                className={`w-2 h-2 rounded-full ${
                  telemetry.state === 'Streaming' || telemetry.state === 'Connected'
                    ? 'bg-emerald-400 animate-pulse'
                    : 'bg-amber-400'
                }`}
              ></span>
              <span className="font-medium">
                {telemetry.state === 'Streaming'
                  ? 'Phone Streaming'
                  : telemetry.state === 'Connected'
                  ? 'Phone Connected'
                  : 'Waiting for Camera'}
              </span>
            </div>
          </div>

          <div className="flex items-center gap-3">
            {/* Quick Transport Pill */}
            <div className="hidden md:flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-slate-900 border border-slate-800 text-xs font-mono text-slate-300">
              <Activity className="w-3.5 h-3.5 text-emerald-400" />
              <span>{telemetry.latency_ms.toFixed(1)} ms</span>
            </div>

            {/* Pair Device Button */}
            <button
              onClick={() => setIsPairingOpen(true)}
              className="flex items-center gap-2 px-3.5 py-1.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold shadow-md shadow-indigo-600/20 transition-all"
            >
              <Smartphone className="w-4 h-4" />
              <span>Pair Device</span>
            </button>
          </div>
        </header>

        {/* Main Content Workspace */}
        <section className="flex-1 p-6 overflow-hidden flex flex-col gap-5">
          {activeTab === 'studio' && (
            <div className="flex-1 flex flex-col gap-4 overflow-hidden">
              <div className="flex-1 min-h-0">
                <VideoPreview
                  telemetry={telemetry}
                  cameraControls={cameraControls}
                  onUpdateCamera={handleUpdateCamera}
                  onOpenPairing={() => setIsPairingOpen(true)}
                />
              </div>
              <div className="shrink-0">
                <StreamAnalytics telemetry={telemetry} />
              </div>
            </div>
          )}

          {activeTab === 'controls' && (
            <div className="flex-1 overflow-y-auto">
              <CameraControlsPanel
                controls={cameraControls}
                onChange={handleUpdateCamera}
              />
            </div>
          )}

          {activeTab === 'ai' && (
            <div className="flex-1 overflow-y-auto">
              <AiStudioPanel aiState={aiState} onChange={handleUpdateAi} />
            </div>
          )}

          {activeTab === 'drivers' && (
            <div className="flex-1 overflow-y-auto">
              <VirtualCamStatus />
            </div>
          )}
        </section>
      </main>

      {/* Modals */}
      <DevicePairingModal
        isOpen={isPairingOpen}
        onClose={() => setIsPairingOpen(false)}
      />
      <DiagnosticsModal
        isOpen={isDiagnosticsOpen}
        onClose={() => setIsDiagnosticsOpen(false)}
        telemetry={telemetry}
      />
    </div>
  );
};
