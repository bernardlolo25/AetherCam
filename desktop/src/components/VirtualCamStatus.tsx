import React, { useState, useEffect } from 'react';
import {
  Video,
  Mic,
  CheckCircle2,
  AlertCircle,
  Download,
  ExternalLink,
  ShieldAlert,
} from 'lucide-react';
import { invokeTauri } from '../hooks/useTauriIpc';
import type { DriverStatus } from '../types';

export const VirtualCamStatus: React.FC = () => {
  const [driverStatus, setDriverStatus] = useState<DriverStatus>({
    virtualCameraInstalled: true,
    virtualMicInstalled: true,
    driverName: 'AetherCam Virtual Camera',
  });
  const [isInstalling, setIsInstalling] = useState(false);
  const [installMsg, setInstallMsg] = useState<string | null>(null);

  useEffect(() => {
    invokeTauri<DriverStatus>('check_driver_status')
      .then(setDriverStatus)
      .catch(console.error);
  }, []);

  const handleInstall = async () => {
    setIsInstalling(true);
    setInstallMsg(null);
    try {
      const msg = await invokeTauri<string>('install_driver');
      setInstallMsg(msg);
      setDriverStatus((s) => ({ ...s, virtualCameraInstalled: true }));
    } catch (err: any) {
      setInstallMsg(err?.toString() || 'Installation failed');
    } finally {
      setIsInstalling(false);
    }
  };

  const appCompatList = [
    { name: 'Zoom', status: 'Plug & Play' },
    { name: 'Microsoft Teams', status: 'Plug & Play' },
    { name: 'OBS Studio', status: 'DirectShow Native' },
    { name: 'Google Meet', status: 'Chrome / Edge Native' },
    { name: 'Discord', status: 'Native Video' },
    { name: 'Cisco Webex', status: 'Verified' },
  ];

  return (
    <div className="flex flex-col gap-6 p-6 rounded-2xl bg-studio-card border border-slate-800 shadow-xl overflow-y-auto max-h-[calc(100vh-140px)]">
      {/* Header */}
      <div className="flex items-center justify-between pb-3 border-b border-slate-800">
        <div>
          <h2 className="text-base font-semibold text-white">System Virtual Devices</h2>
          <p className="text-xs text-slate-400">
            Windows DirectShow & Media Foundation virtual device endpoints
          </p>
        </div>
      </div>

      {/* Driver Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Virtual Camera */}
        <div className="flex flex-col justify-between p-4 rounded-xl bg-slate-900/60 border border-slate-800">
          <div className="flex items-start justify-between">
            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-xl bg-indigo-600/20 text-indigo-400 border border-indigo-500/30">
                <Video className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-sm font-bold text-white">AetherCam Virtual Camera</h3>
                <span className="text-xs text-slate-400">DirectShow Source Filter DLL</span>
              </div>
            </div>
            {driverStatus.virtualCameraInstalled ? (
              <span className="flex items-center gap-1 px-2.5 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs font-semibold">
                <CheckCircle2 className="w-3.5 h-3.5" />
                Active
              </span>
            ) : (
              <span className="flex items-center gap-1 px-2.5 py-1 rounded-full bg-amber-500/10 border border-amber-500/30 text-amber-400 text-xs font-semibold">
                <AlertCircle className="w-3.5 h-3.5" />
                Not Registered
              </span>
            )}
          </div>
          <div className="mt-4 pt-3 border-t border-slate-800 text-[11px] text-slate-400">
            Visible in all Windows apps without requiring browser extensions or plugins.
          </div>
        </div>

        {/* Virtual Microphone */}
        <div className="flex flex-col justify-between p-4 rounded-xl bg-slate-900/60 border border-slate-800">
          <div className="flex items-start justify-between">
            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-xl bg-cyan-600/20 text-cyan-400 border border-cyan-500/30">
                <Mic className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-sm font-bold text-white">AetherCam Virtual Microphone</h3>
                <span className="text-xs text-slate-400">48kHz Stereo WASAPI Loopback</span>
              </div>
            </div>
            <span className="flex items-center gap-1 px-2.5 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs font-semibold">
              <CheckCircle2 className="w-3.5 h-3.5" />
              Active
            </span>
          </div>
          <div className="mt-4 pt-3 border-t border-slate-800 text-[11px] text-slate-400">
            Streams phone microphone directly into your Windows communication apps.
          </div>
        </div>
      </div>

      {/* Driver Actions */}
      <div className="flex flex-col gap-3 p-4 rounded-xl bg-slate-900/40 border border-slate-800">
        <div className="flex items-center justify-between">
          <div>
            <div className="text-xs font-bold text-white">Driver Registration Maintenance</div>
            <div className="text-[11px] text-slate-400">
              Re-register DLL filter with Windows COM if camera doesn't appear in Zoom or Teams
            </div>
          </div>
          <button
            onClick={handleInstall}
            disabled={isInstalling}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold shadow-lg shadow-indigo-600/20 transition-all disabled:opacity-50"
          >
            <Download className="w-4 h-4" />
            <span>{isInstalling ? 'Registering...' : 'Register / Repair Drivers'}</span>
          </button>
        </div>
        {installMsg && (
          <div className="p-3 rounded-lg bg-indigo-950/40 border border-indigo-800/60 text-xs text-indigo-200">
            {installMsg}
          </div>
        )}
      </div>

      {/* Application Compatibility Grid */}
      <div className="flex flex-col gap-2">
        <label className="text-xs font-semibold text-slate-300 uppercase tracking-wider">
          Verified Native Application Compatibility
        </label>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
          {appCompatList.map((app) => (
            <div
              key={app.name}
              className="flex items-center justify-between p-3 rounded-xl bg-slate-900/50 border border-slate-800 text-xs"
            >
              <span className="font-semibold text-slate-200">{app.name}</span>
              <span className="text-[11px] text-emerald-400 font-mono flex items-center gap-1">
                <CheckCircle2 className="w-3 h-3" />
                {app.status}
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
