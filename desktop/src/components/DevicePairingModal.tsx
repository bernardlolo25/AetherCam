import React, { useState, useEffect } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import {
  QrCode,
  Usb,
  Wifi,
  Smartphone,
  Copy,
  Check,
  X,
  ShieldCheck,
  RefreshCw,
} from 'lucide-react';
import { invokeTauri } from '../hooks/useTauriIpc';
import type { PairingQrData } from '../types';

interface DevicePairingModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const DevicePairingModal: React.FC<DevicePairingModalProps> = ({
  isOpen,
  onClose,
}) => {
  const [qrData, setQrData] = useState<PairingQrData>({
    v: 2,
    ip: '192.168.1.108',
    udpPort: 48200,
    tcpPort: 48201,
    pin: '849201',
    serverPubKey: 'BM7v9LqP3rM1z0NvQ8sYt4XuKgH2jR5wFpE6aN8=',
    hostname: 'AetherCam-Studio-PC',
  });
  const [copiedPin, setCopiedPin] = useState(false);
  const [activeTab, setActiveTab] = useState<'qr' | 'usb' | 'pin'>('qr');

  useEffect(() => {
    if (!isOpen) return;
    // Try to fetch real QR data from the running desktop server first
    fetch('http://127.0.0.1:48202/api/qr-data')
      .then(r => { if (!r.ok) throw new Error('not ok'); return r.json(); })
      .then((data: PairingQrData) => setQrData(data))
      .catch(() => {
        // Fallback to Tauri IPC (or mock) if server not reachable
        invokeTauri<PairingQrData>('get_pairing_qr_data')
          .then(setQrData)
          .catch(console.error);
      });
  }, [isOpen]);

  if (!isOpen) return null;

  const qrPayloadString = JSON.stringify(qrData);

  const copyPin = () => {
    navigator.clipboard.writeText(qrData.pin);
    setCopiedPin(true);
    setTimeout(() => setCopiedPin(false), 2000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-in fade-in duration-150">
      <div className="relative w-full max-w-lg p-6 rounded-3xl bg-studio-card border border-slate-700 shadow-2xl flex flex-col gap-6">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-slate-800">
          <div className="flex items-center gap-2.5">
            <Smartphone className="w-5 h-5 text-indigo-400" />
            <div>
              <h2 className="text-base font-bold text-white">Connect Android Camera</h2>
              <p className="text-xs text-slate-400">Zero-setup instant pairing</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Tab Selector */}
        <div className="flex rounded-xl bg-slate-900/80 p-1 border border-slate-800">
          <button
            onClick={() => setActiveTab('qr')}
            className={`flex-1 flex items-center justify-center gap-2 py-2 rounded-lg text-xs font-semibold transition-all ${
              activeTab === 'qr' ? 'bg-indigo-600 text-white shadow' : 'text-slate-400 hover:text-white'
            }`}
          >
            <QrCode className="w-4 h-4" />
            <span>QR Scan (Instant)</span>
          </button>
          <button
            onClick={() => setActiveTab('usb')}
            className={`flex-1 flex items-center justify-center gap-2 py-2 rounded-lg text-xs font-semibold transition-all ${
              activeTab === 'usb' ? 'bg-indigo-600 text-white shadow' : 'text-slate-400 hover:text-white'
            }`}
          >
            <Usb className="w-4 h-4" />
            <span>USB (Zero Latency)</span>
          </button>
          <button
            onClick={() => setActiveTab('pin')}
            className={`flex-1 flex items-center justify-center gap-2 py-2 rounded-lg text-xs font-semibold transition-all ${
              activeTab === 'pin' ? 'bg-indigo-600 text-white shadow' : 'text-slate-400 hover:text-white'
            }`}
          >
            <Wifi className="w-4 h-4" />
            <span>Manual PIN</span>
          </button>
        </div>

        {/* QR Scan Tab Content */}
        {activeTab === 'qr' && (
          <div className="flex flex-col items-center gap-4 py-2">
            <div className="p-4 rounded-2xl bg-white shadow-xl">
              <QRCodeSVG
                value={qrPayloadString}
                size={220}
                level="M"
                includeMargin={false}
              />
            </div>
            <div className="text-center">
              <div className="text-sm font-semibold text-white">
                Open AetherCam on Android and tap "Scan QR"
              </div>
              <div className="text-xs text-slate-400 mt-1">
                E2EE authenticated handshake over LAN / Wi-Fi Direct
              </div>
            </div>
          </div>
        )}

        {/* USB Tab Content */}
        {activeTab === 'usb' && (
          <div className="flex flex-col gap-4 py-3">
            <div className="p-4 rounded-2xl bg-emerald-950/20 border border-emerald-800/40 text-emerald-200">
              <div className="flex items-center gap-2 font-bold text-sm">
                <Usb className="w-5 h-5 text-emerald-400" />
                <span>USB Plug & Play Mode (Sub-20ms)</span>
              </div>
              <p className="text-xs text-emerald-300/80 mt-1">
                Connect your Android phone with a USB-C or USB 3.0 cable for absolute minimum latency and 4K stability.
              </p>
            </div>

            <ol className="flex flex-col gap-3 text-xs text-slate-300">
              <li className="flex items-start gap-2.5">
                <span className="w-5 h-5 rounded-full bg-slate-800 text-indigo-400 flex items-center justify-center font-bold shrink-0">
                  1
                </span>
                <span>Enable <strong>USB Debugging</strong> in Android Developer Options.</span>
              </li>
              <li className="flex items-start gap-2.5">
                <span className="w-5 h-5 rounded-full bg-slate-800 text-indigo-400 flex items-center justify-center font-bold shrink-0">
                  2
                </span>
                <span>Plug phone into PC via USB cable.</span>
              </li>
              <li className="flex items-start gap-2.5">
                <span className="w-5 h-5 rounded-full bg-slate-800 text-indigo-400 flex items-center justify-center font-bold shrink-0">
                  3
                </span>
                <span>AetherCam automatically establishes high-speed reverse port forwarding.</span>
              </li>
            </ol>
          </div>
        )}

        {/* Manual PIN Tab Content */}
        {activeTab === 'pin' && (
          <div className="flex flex-col items-center gap-4 py-4">
            <div className="text-center">
              <div className="text-xs text-slate-400 uppercase tracking-wider font-semibold">
                Desktop Server IP
              </div>
              <div className="font-mono text-lg font-bold text-indigo-400 mt-0.5">
                {qrData.ip}:{qrData.tcpPort}
              </div>
            </div>

            <div className="flex items-center gap-3 p-4 rounded-2xl bg-slate-900 border border-slate-700">
              <span className="font-mono text-3xl font-extrabold tracking-widest text-white">
                {qrData.pin}
              </span>
              <button
                onClick={copyPin}
                className="p-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 transition-colors"
                title="Copy PIN"
              >
                {copiedPin ? <Check className="w-5 h-5 text-emerald-400" /> : <Copy className="w-5 h-5" />}
              </button>
            </div>
            <div className="text-xs text-slate-400 text-center">
              Enter this 6-digit security PIN on the Android app when prompted
            </div>
          </div>
        )}

        {/* Footer Security Note */}
        <div className="flex items-center justify-between pt-3 border-t border-slate-800 text-[11px] text-slate-400">
          <div className="flex items-center gap-1.5 text-emerald-400 font-medium">
            <ShieldCheck className="w-4 h-4" />
            <span>Encrypted with Curve25519 & AES-256-GCM</span>
          </div>
          <span className="font-mono text-slate-500">Port {qrData.udpPort} UDP</span>
        </div>
      </div>
    </div>
  );
};
