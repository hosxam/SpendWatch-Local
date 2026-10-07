import { AlertTriangle, CheckCircle, ExternalLink } from 'lucide-react';

interface NotificationPermissionBannerProps {
  isGranted: boolean;
  onToggle: (granted: boolean) => void;
}

export function NotificationPermissionBanner({
  isGranted,
  onToggle,
}: NotificationPermissionBannerProps) {
  if (isGranted) {
    return (
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-3 flex items-center justify-between text-xs text-slate-300">
        <div className="flex items-center gap-2">
          <CheckCircle className="w-4 h-4 text-emerald-400 shrink-0" />
          <span>
            <strong>Notification Access Active:</strong> Intercepting bank alerts on device.
          </span>
        </div>
        <button
          onClick={() => onToggle(false)}
          className="text-[11px] text-slate-400 hover:text-rose-400 transition-colors underline"
        >
          Simulate Revoke
        </button>
      </div>
    );
  }

  return (
    <div className="bg-rose-950/40 border border-rose-500/40 rounded-xl p-3.5 text-xs text-rose-200">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-2.5">
          <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
          <div>
            <div className="font-semibold text-rose-300">
              Notification Access Permission Required
            </div>
            <p className="text-[11px] text-rose-300/80 mt-0.5 leading-relaxed">
              Android requires special access for SpendWatch to read bank notifications.
              No internet access is ever requested or allowed.
            </p>
          </div>
        </div>

        <button
          onClick={() => onToggle(true)}
          className="px-3 py-1.5 bg-rose-600 hover:bg-rose-500 text-white rounded-lg font-semibold text-xs shrink-0 flex items-center gap-1 shadow-md shadow-rose-950 transition-colors"
        >
          <span>Grant Access</span>
          <ExternalLink className="w-3 h-3" />
        </button>
      </div>
    </div>
  );
}
