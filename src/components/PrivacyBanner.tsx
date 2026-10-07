import { ShieldCheck, WifiOff, HardDrive, Lock } from 'lucide-react';

export function PrivacyBanner() {
  return (
    <div className="bg-emerald-950/40 border border-emerald-500/30 rounded-xl p-3.5 text-xs text-emerald-200">
      <div className="flex items-center gap-2 font-semibold text-emerald-400 mb-1">
        <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0" />
        <span>100% On-Device • Zero Internet Permission</span>
      </div>
      <div className="grid grid-cols-3 gap-2 mt-2 pt-2 border-t border-emerald-500/20 text-[11px] text-emerald-300/90">
        <div className="flex items-center gap-1.5">
          <WifiOff className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
          <span>No network requests</span>
        </div>
        <div className="flex items-center gap-1.5">
          <HardDrive className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
          <span>Local SQLite storage</span>
        </div>
        <div className="flex items-center gap-1.5">
          <Lock className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
          <span>Zero cloud analytics</span>
        </div>
      </div>
    </div>
  );
}
