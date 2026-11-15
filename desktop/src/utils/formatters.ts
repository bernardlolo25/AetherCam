export function formatBitrate(kbps: number): string {
  if (kbps >= 1000) {
    return `${(kbps / 1000).toFixed(1)} Mbps`;
  }
  return `${kbps} kbps`;
}

export function formatLatencyColor(latencyMs: number): string {
  if (latencyMs < 25) return 'text-emerald-400 bg-emerald-500/10 border-emerald-500/30';
  if (latencyMs < 60) return 'text-amber-400 bg-amber-500/10 border-amber-500/30';
  return 'text-rose-400 bg-rose-500/10 border-rose-500/30';
}

export function formatTransportBadge(transport: string): { label: string; color: string } {
  switch (transport) {
    case 'USB':
      return { label: 'USB (Fastest)', color: 'bg-emerald-500/20 text-emerald-300 border-emerald-500/40' };
    case 'Wi-Fi Direct':
      return { label: 'Wi-Fi Direct P2P', color: 'bg-cyan-500/20 text-cyan-300 border-cyan-500/40' };
    case 'Wi-Fi LAN':
      return { label: '5GHz Wi-Fi', color: 'bg-indigo-500/20 text-indigo-300 border-indigo-500/40' };
    case 'Bluetooth':
      return { label: 'Bluetooth LE', color: 'bg-purple-500/20 text-purple-300 border-purple-500/40' };
    default:
      return { label: transport, color: 'bg-slate-500/20 text-slate-300 border-slate-500/40' };
  }
}
