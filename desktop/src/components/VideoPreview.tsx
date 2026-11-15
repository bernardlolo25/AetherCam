import React, { useState, useRef, useEffect } from 'react';
import {
  Camera,
  CircleDot,
  Maximize2,
  Minimize2,
  FlipHorizontal,
  Grid,
  Lock,
  Volume2,
  VolumeX,
  Zap,
  Activity,
  Layers,
  Smartphone,
} from 'lucide-react';
import type { StreamTelemetry, CameraControls } from '../types';
import { formatBitrate, formatTransportBadge } from '../utils/formatters';

interface VideoPreviewProps {
  telemetry: StreamTelemetry;
  cameraControls: CameraControls;
  onUpdateCamera: (controls: Partial<CameraControls>) => void;
  onOpenPairing?: () => void;
}

export const VideoPreview: React.FC<VideoPreviewProps> = ({
  telemetry,
  cameraControls,
  onUpdateCamera,
  onOpenPairing,
}) => {
  const [isRecording, setIsRecording] = useState(false);
  const [recordSeconds, setRecordSeconds] = useState(0);
  const [isMirrored, setIsMirrored] = useState(false);
  const [showGrid, setShowGrid] = useState(false);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [isMuted, setIsMuted] = useState(false);

  const containerRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);

  // Recording timer
  useEffect(() => {
    let interval: any;
    if (isRecording) {
      interval = setInterval(() => setRecordSeconds((s) => s + 1), 1000);
    } else {
      setRecordSeconds(0);
    }
    return () => clearInterval(interval);
  }, [isRecording]);

  // Live Canvas Simulation for smooth 60fps render
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animId: number;
    let frame = 0;

    const render = () => {
      frame++;
      const w = canvas.width;
      const h = canvas.height;

      // Dark modern studio feed
      const grad = ctx.createLinearGradient(0, 0, w, h);
      grad.addColorStop(0, '#0f172a');
      grad.addColorStop(0.5, '#1e1b4b');
      grad.addColorStop(1, '#090d16');
      ctx.fillStyle = grad;
      ctx.fillRect(0, 0, w, h);

      // Render subtle cinematic portrait silhouette
      ctx.fillStyle = 'rgba(99, 102, 241, 0.08)';
      ctx.beginPath();
      ctx.arc(w / 2, h / 2 - 20, 140, 0, Math.PI * 2);
      ctx.fill();

      ctx.fillStyle = 'rgba(99, 102, 241, 0.05)';
      ctx.beginPath();
      ctx.ellipse(w / 2, h / 2 + 180, 220, 140, 0, 0, Math.PI * 2);
      ctx.fill();

      // Camera lens flare / light scan effect
      const sweepX = (Math.sin(frame * 0.02) * 0.4 + 0.5) * w;
      const flare = ctx.createRadialGradient(sweepX, h / 2, 10, sweepX, h / 2, 350);
      flare.addColorStop(0, 'rgba(56, 189, 248, 0.06)');
      flare.addColorStop(1, 'rgba(56, 189, 248, 0)');
      ctx.fillStyle = flare;
      ctx.fillRect(0, 0, w, h);

      animId = requestAnimationFrame(render);
    };

    render();
    return () => cancelAnimationFrame(animId);
  }, []);

  const toggleFullscreen = () => {
    if (!containerRef.current) return;
    if (!document.fullscreenElement) {
      containerRef.current.requestFullscreen();
      setIsFullscreen(true);
    } else {
      document.exitFullscreen();
      setIsFullscreen(false);
    }
  };

  const transportInfo = formatTransportBadge(telemetry.active_transport);

  return (
    <div
      ref={containerRef}
      className="relative flex flex-col items-center justify-center w-full h-full bg-black rounded-2xl overflow-hidden border border-slate-800 shadow-2xl group"
    >
      {/* Video Canvas */}
      <canvas
        ref={canvasRef}
        width={1920}
        height={1080}
        className={`w-full h-full object-contain ${isMirrored ? 'scale-x-[-1]' : ''}`}
      />

      {/* Optional Rule-of-Thirds Grid */}
      {showGrid && (
        <div className="absolute inset-0 pointer-events-none grid grid-cols-3 grid-rows-3 border border-white/10">
          <div className="border-r border-b border-white/15"></div>
          <div className="border-r border-b border-white/15"></div>
          <div className="border-b border-white/15"></div>
          <div className="border-r border-b border-white/15"></div>
          <div className="border-r border-b border-white/15"></div>
          <div className="border-b border-white/15"></div>
          <div className="border-r border-white/15"></div>
          <div className="border-r border-white/15"></div>
          <div></div>
        </div>
      )}

      {/* Standby / No Phone Connected Overlay */}
      {telemetry.state !== 'Streaming' && (
        <div className="absolute inset-0 flex flex-col items-center justify-center p-6 text-center z-10 pointer-events-auto bg-black/50 backdrop-blur-sm">
          <div className="p-4 rounded-3xl bg-indigo-600/15 border border-indigo-500/30 mb-4 shadow-xl shadow-indigo-600/10">
            <Smartphone className="w-10 h-10 text-indigo-400 animate-pulse" />
          </div>
          <h2 className="text-base sm:text-lg font-bold text-white mb-1.5">
            {telemetry.state === 'Connected' ? 'Phone Connected — Starting Stream…' : 'Android Camera Disconnected'}
          </h2>
          <p className="text-xs text-slate-400 max-w-sm mb-5 leading-relaxed">
            {telemetry.state === 'Connected'
              ? 'Handshake completed! Negotiating video stream…'
              : 'Launch AetherCam on your phone and tap Scan QR or connect via USB cable for instant 4K 60FPS streaming.'}
          </p>
          {onOpenPairing && telemetry.state !== 'Connected' && (
            <button
              onClick={onOpenPairing}
              className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-bold shadow-lg shadow-indigo-600/30 transition-all cursor-pointer"
            >
              <Smartphone className="w-4 h-4" />
              <span>Show Pairing QR Code</span>
            </button>
          )}
        </div>
      )}

      {/* Top Floating HUD Badges */}
      <div className="absolute top-4 left-4 right-4 flex items-center justify-between pointer-events-none z-10">
        <div className="flex items-center gap-2">
          {/* Live / Standby Status Pill */}
          {telemetry.state === 'Streaming' ? (
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-slate-900/80 backdrop-blur-md border border-slate-700/60 text-xs font-medium text-slate-200">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
              <span>LIVE</span>
              <span className="text-slate-500">|</span>
              <span className="font-mono text-emerald-400">{telemetry.resolution}</span>
              <span className="text-slate-500">@</span>
              <span className="font-mono text-cyan-400">{telemetry.fps} FPS</span>
            </div>
          ) : (
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-slate-900/80 backdrop-blur-md border border-amber-500/40 text-xs font-medium text-slate-200">
              <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse"></span>
              <span className="text-amber-300 font-semibold">{telemetry.state === 'Connected' ? 'CONNECTED' : 'STANDBY'}</span>
              <span className="text-slate-500">|</span>
              <span className="text-slate-400">{telemetry.state === 'Connected' ? 'Ready' : 'Waiting for Camera'}</span>
            </div>
          )}

          {/* Transport Badge */}
          <div
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full backdrop-blur-md border text-xs font-medium ${transportInfo.color}`}
          >
            <Activity className="w-3.5 h-3.5" />
            <span>{transportInfo.label}</span>
            <span className="font-mono opacity-80">({telemetry.latency_ms.toFixed(1)} ms)</span>
          </div>

          {/* Security Badge */}
          <div className="flex items-center gap-1 px-2.5 py-1.5 rounded-full bg-slate-900/80 backdrop-blur-md border border-emerald-500/30 text-xs text-emerald-400">
            <Lock className="w-3.5 h-3.5" />
            <span>E2EE</span>
          </div>
        </div>

        {/* Right Info: Bitrate & Lens */}
        <div className="flex items-center gap-2">
          <div className="px-3 py-1.5 rounded-full bg-slate-900/80 backdrop-blur-md border border-slate-700/60 text-xs font-mono text-slate-300">
            {formatBitrate(telemetry.bitrate_kbps)}
          </div>
          <div className="px-3 py-1.5 rounded-full bg-indigo-500/20 border border-indigo-500/40 text-xs font-semibold text-indigo-300">
            {cameraControls.lens} {cameraControls.zoom_ratio.toFixed(1)}x
          </div>
        </div>
      </div>

      {/* Recording Indicator */}
      {isRecording && (
        <div className="absolute top-16 left-4 flex items-center gap-2 px-3 py-1.5 rounded-full bg-rose-600/90 text-white font-mono text-xs font-bold shadow-lg animate-pulse">
          <CircleDot className="w-4 h-4 fill-white" />
          <span>REC</span>
          <span>
            {Math.floor(recordSeconds / 60)
              .toString()
              .padStart(2, '0')}
            :{(recordSeconds % 60).toString().padStart(2, '0')}
          </span>
        </div>
      )}

      {/* Bottom Floating Control Bar */}
      <div className="absolute bottom-5 left-1/2 -translate-x-1/2 flex items-center gap-2 px-4 py-2.5 rounded-2xl bg-slate-900/85 backdrop-blur-xl border border-slate-700/60 shadow-2xl z-20 transition-all duration-200">
        {/* Record Button */}
        <button
          onClick={() => setIsRecording(!isRecording)}
          title={isRecording ? 'Stop Recording' : 'Start Recording (MP4/MKV)'}
          className={`flex items-center gap-2 px-3.5 py-1.5 rounded-xl text-xs font-semibold transition-all ${
            isRecording
              ? 'bg-rose-500 text-white hover:bg-rose-600'
              : 'bg-slate-800 text-rose-400 hover:bg-rose-500/20 hover:border-rose-500/30'
          }`}
        >
          <CircleDot className="w-4 h-4" />
          <span>{isRecording ? 'Stop' : 'Record'}</span>
        </button>

        {/* Snapshot Photo */}
        <button
          onClick={() => alert('Snapshot saved to Pictures/AetherCam/')}
          title="Capture High-Res Photo"
          className="p-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 transition-all"
        >
          <Camera className="w-4 h-4" />
        </button>

        <div className="w-px h-5 bg-slate-700"></div>

        {/* Mute Mic */}
        <button
          onClick={() => setIsMuted(!isMuted)}
          title={isMuted ? 'Unmute Phone Microphone' : 'Mute Phone Microphone'}
          className={`p-2 rounded-xl transition-all ${
            isMuted
              ? 'bg-rose-500/20 text-rose-400 border border-rose-500/40'
              : 'bg-slate-800 hover:bg-slate-700 text-slate-200'
          }`}
        >
          {isMuted ? <VolumeX className="w-4 h-4" /> : <Volume2 className="w-4 h-4" />}
        </button>

        {/* Torch Toggle */}
        <button
          onClick={() => onUpdateCamera({ torch_on: !cameraControls.torch_on })}
          title="Toggle Phone Flashlight"
          className={`p-2 rounded-xl transition-all ${
            cameraControls.torch_on
              ? 'bg-amber-500/20 text-amber-300 border border-amber-500/40'
              : 'bg-slate-800 hover:bg-slate-700 text-slate-200'
          }`}
        >
          <Zap className="w-4 h-4" />
        </button>

        {/* Mirror Preview */}
        <button
          onClick={() => setIsMirrored(!isMirrored)}
          title="Mirror Camera Preview"
          className={`p-2 rounded-xl transition-all ${
            isMirrored
              ? 'bg-indigo-500/20 text-indigo-300 border border-indigo-500/40'
              : 'bg-slate-800 hover:bg-slate-700 text-slate-200'
          }`}
        >
          <FlipHorizontal className="w-4 h-4" />
        </button>

        {/* Grid Overlay */}
        <button
          onClick={() => setShowGrid(!showGrid)}
          title="Toggle Rule of Thirds Grid"
          className={`p-2 rounded-xl transition-all ${
            showGrid
              ? 'bg-indigo-500/20 text-indigo-300 border border-indigo-500/40'
              : 'bg-slate-800 hover:bg-slate-700 text-slate-200'
          }`}
        >
          <Grid className="w-4 h-4" />
        </button>

        {/* Fullscreen */}
        <button
          onClick={toggleFullscreen}
          title="Toggle Fullscreen"
          className="p-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 transition-all"
        >
          {isFullscreen ? <Minimize2 className="w-4 h-4" /> : <Maximize2 className="w-4 h-4" />}
        </button>
      </div>
    </div>
  );
};
