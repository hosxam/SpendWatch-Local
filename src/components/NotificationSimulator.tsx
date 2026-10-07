import { useState } from 'react';
import { UAE_NOTIFICATION_SAMPLES } from '../storage';
import { parseUaeBankNotification } from '../parser';
import { Transaction, RuyaConfig, BankNotificationSample } from '../types';
import {
  Bell,
  Send,
  CheckCircle2,
  XCircle,
  Sparkles,
  Building2,
  Sliders,
  ChevronDown,
  ChevronUp,
  AlertTriangle,
} from 'lucide-react';

interface NotificationSimulatorProps {
  onTransactionCaptured: (tx: Transaction, isDuplicate: boolean) => void;
  onRecordDebugLog: (
    title: string,
    body: string,
    pkg: string,
    result: ReturnType<typeof parseUaeBankNotification>
  ) => void;
  isPermissionGranted: boolean;
  ruyaConfig: RuyaConfig;
  onOpenRuyaConfig: () => void;
}

export function NotificationSimulator({
  onTransactionCaptured,
  onRecordDebugLog,
  isPermissionGranted,
  ruyaConfig,
  onOpenRuyaConfig,
}: NotificationSimulatorProps) {
  const [customTitle, setCustomTitle] = useState('Ruya');
  const [customBody, setCustomBody] = useState(
    'Your Ruya card ending 4091 was used for AED 142.50 at CARREFOUR DEIRA on 07/10/2026.'
  );
  const [customPackage, setCustomPackage] = useState('ae.ruya.digital');
  const [lastResult, setLastResult] = useState<{
    success: boolean;
    message: string;
    isDuplicate?: boolean;
    tx?: Transaction;
  } | null>(null);
  const [showCustomForm, setShowCustomForm] = useState(false);
  const [filterBankType, setFilterBankType] = useState<'ruya' | 'all' | 'secondary' | 'rejections'>('ruya');

  const handleSimulate = (sample: BankNotificationSample) => {
    handleRunNotification(sample.title, sample.body, sample.packageName);
  };

  const handleRunNotification = (title: string, body: string, pkg: string) => {
    if (!isPermissionGranted) {
      setLastResult({
        success: false,
        message: 'Notification Access is disabled. Please enable permission to detect alerts.',
      });
      return;
    }

    const parseResult = parseUaeBankNotification(title, body, pkg, Date.now(), ruyaConfig);

    // Record to Debug Log screen
    onRecordDebugLog(title, body, pkg, parseResult);

    if (parseResult.isAccepted && parseResult.transaction) {
      // Pass to parent storage (with duplicate check)
      let duplicateFlag = false;
      onTransactionCaptured(parseResult.transaction, false);

      setLastResult({
        success: true,
        message: `Extracted ${parseResult.transaction.currency} ${parseResult.transaction.amount.toFixed(2)} at ${parseResult.transaction.merchant} (${parseResult.transaction.category}) [${parseResult.transaction.transactionType}]`,
        tx: parseResult.transaction,
        isDuplicate: duplicateFlag,
      });
    } else {
      setLastResult({
        success: false,
        message: parseResult.reason || 'Rejected: not an outgoing expense.',
      });
    }
  };

  const filteredSamples = UAE_NOTIFICATION_SAMPLES.filter((sample) => {
    if (filterBankType === 'ruya') return sample.isPrimary;
    if (filterBankType === 'secondary') return !sample.isPrimary && sample.expectedType === 'outgoing';
    if (filterBankType === 'rejections') return sample.expectedType !== 'outgoing';
    return true; // 'all'
  });

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-lg space-y-4">
      {/* Title & Ruya Config Button */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2.5">
          <div className="p-2 bg-emerald-500/10 text-emerald-400 rounded-xl">
            <Bell className="w-4 h-4" />
          </div>
          <div>
            <h3 className="text-sm font-bold text-slate-100 flex items-center gap-2">
              <span>UAE Notification Simulator</span>
              <span className="text-[10px] bg-emerald-950 text-emerald-400 border border-emerald-800/40 px-2 py-0.5 rounded-full font-mono">
                RUYA PRIMARY
              </span>
            </h3>
            <p className="text-xs text-slate-400">
              Test Ruya and UAE bank transaction detection with 20+ realistic scenarios
            </p>
          </div>
        </div>

        <button
          onClick={onOpenRuyaConfig}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-emerald-400 rounded-xl text-xs font-medium border border-slate-700/60 transition-colors"
          title="Configure Ruya SMS sender IDs and matching rules"
        >
          <Sliders className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">Ruya Rules</span>
        </button>
      </div>

      {/* Filter Tabs for Scenarios */}
      <div className="flex flex-wrap gap-1.5 bg-slate-950 p-1 rounded-xl border border-slate-800 text-xs">
        <button
          onClick={() => setFilterBankType('ruya')}
          className={`px-3 py-1.5 rounded-lg font-medium transition-colors ${
            filterBankType === 'ruya'
              ? 'bg-emerald-600 text-white font-bold shadow-sm'
              : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          Ruya Scenarios ({UAE_NOTIFICATION_SAMPLES.filter((s) => s.isPrimary && s.expectedType === 'outgoing').length})
        </button>
        <button
          onClick={() => setFilterBankType('secondary')}
          className={`px-3 py-1.5 rounded-lg font-medium transition-colors ${
            filterBankType === 'secondary'
              ? 'bg-slate-800 text-emerald-400 font-bold shadow-sm'
              : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          Secondary UAE Banks ({UAE_NOTIFICATION_SAMPLES.filter((s) => !s.isPrimary && s.expectedType === 'outgoing').length})
        </button>
        <button
          onClick={() => setFilterBankType('rejections')}
          className={`px-3 py-1.5 rounded-lg font-medium transition-colors ${
            filterBankType === 'rejections'
              ? 'bg-rose-950/80 text-rose-300 font-bold border border-rose-800/50 shadow-sm'
              : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          Rejection Tests (OTPs, Salary, Declined)
        </button>
        <button
          onClick={() => setFilterBankType('all')}
          className={`px-3 py-1.5 rounded-lg font-medium transition-colors ${
            filterBankType === 'all'
              ? 'bg-slate-800 text-emerald-400 font-bold shadow-sm'
              : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          All ({UAE_NOTIFICATION_SAMPLES.length})
        </button>
      </div>

      {/* Preset Bank Alerts Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 max-h-[380px] overflow-y-auto pr-1">
        {filteredSamples.map((sample) => (
          <button
            key={sample.id}
            onClick={() => handleSimulate(sample)}
            className={`text-left p-3 rounded-xl border transition-all flex flex-col justify-between ${
              sample.isPrimary && sample.expectedType === 'outgoing'
                ? 'bg-emerald-950/20 border-emerald-500/30 hover:border-emerald-400 hover:bg-emerald-950/30 text-slate-200'
                : sample.expectedType === 'outgoing'
                ? 'bg-slate-950/70 border-slate-800 hover:border-slate-700 hover:bg-slate-800/40 text-slate-200'
                : 'bg-rose-950/15 border-rose-900/30 hover:border-rose-800/50 hover:bg-rose-950/25 text-slate-400'
            }`}
          >
            <div className="flex items-center justify-between w-full mb-1.5">
              <span className="text-xs font-semibold text-slate-200 flex items-center gap-1.5">
                <Building2 className={`w-3.5 h-3.5 ${sample.isPrimary ? 'text-emerald-400' : 'text-slate-400'}`} />
                <span>{sample.bankName}</span>
                {sample.isPrimary && (
                  <span className="text-[9px] bg-emerald-900/80 text-emerald-300 px-1.5 py-0.2 rounded font-mono font-bold">
                    PRIMARY
                  </span>
                )}
              </span>
              <span
                className={`text-[9px] px-1.5 py-0.2 rounded font-bold uppercase ${
                  sample.expectedType === 'outgoing'
                    ? 'bg-emerald-950 text-emerald-400 border border-emerald-800/50'
                    : 'bg-rose-950 text-rose-400 border border-rose-800/50'
                }`}
              >
                {sample.expectedType === 'outgoing' ? 'EXPENSE' : 'REJECT'}
              </span>
            </div>
            <p className="text-[11px] text-slate-300 line-clamp-2 leading-relaxed">
              "{sample.body}"
            </p>
            <div className="text-[10px] text-slate-500 mt-1.5 italic">
              {sample.explanation}
            </div>
          </button>
        ))}
      </div>

      {/* Custom SMS / Push Notification Input */}
      <div className="pt-2 border-t border-slate-800/80">
        <button
          onClick={() => setShowCustomForm(!showCustomForm)}
          className="w-full flex items-center justify-between py-1 text-xs text-slate-400 hover:text-slate-200 font-medium"
        >
          <span className="flex items-center gap-1.5">
            <Sparkles className="w-3.5 h-3.5 text-emerald-400" />
            Paste Custom Real UAE SMS / Notification Text
          </span>
          {showCustomForm ? (
            <ChevronUp className="w-4 h-4 text-slate-500" />
          ) : (
            <ChevronDown className="w-4 h-4 text-slate-500" />
          )}
        </button>

        {showCustomForm && (
          <div className="mt-3 space-y-2.5 pt-2 bg-slate-950/60 p-3 rounded-xl border border-slate-800">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
              <div>
                <label className="block text-[11px] font-medium text-slate-400 mb-1">
                  Sender ID / Bank Title
                </label>
                <input
                  type="text"
                  value={customTitle}
                  onChange={(e) => setCustomTitle(e.target.value)}
                  placeholder="e.g. Ruya, Emirates NBD, ADCB"
                  className="w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-emerald-500 font-mono"
                />
              </div>
              <div>
                <label className="block text-[11px] font-medium text-slate-400 mb-1">
                  Android Package Name (Optional)
                </label>
                <input
                  type="text"
                  value={customPackage}
                  onChange={(e) => setCustomPackage(e.target.value)}
                  placeholder="e.g. ae.ruya.digital"
                  className="w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-emerald-500 font-mono"
                />
              </div>
            </div>

            <div>
              <label className="block text-[11px] font-medium text-slate-400 mb-1">
                Notification Message Body
              </label>
              <textarea
                rows={2}
                value={customBody}
                onChange={(e) => setCustomBody(e.target.value)}
                placeholder="e.g. Your Ruya card ending 4091 was used for AED 85.00 at CARREFOUR DUBAI."
                className="w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-emerald-500 font-mono"
              />
            </div>

            <button
              onClick={() => handleRunNotification(customTitle, customBody, customPackage)}
              className="w-full py-2 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors shadow-lg shadow-emerald-950"
            >
              <Send className="w-3.5 h-3.5" />
              <span>Simulate Notification</span>
            </button>
          </div>
        )}
      </div>

      {/* Simulation Result Feedback */}
      {lastResult && (
        <div
          className={`p-3 rounded-xl border text-xs flex items-start gap-2.5 animate-fadeIn ${
            lastResult.success
              ? 'bg-emerald-950/70 border-emerald-500/50 text-emerald-200'
              : 'bg-rose-950/70 border-rose-500/50 text-rose-200'
          }`}
        >
          {lastResult.success ? (
            <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
          ) : (
            <XCircle className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
          )}
          <div className="min-w-0 flex-1">
            <div className="font-bold flex items-center gap-2">
              <span>{lastResult.success ? 'Recorded to Local SQLite Database' : 'Notification Rejected / Ignored'}</span>
              {lastResult.isDuplicate && (
                <span className="bg-amber-900 text-amber-300 text-[10px] px-1.5 py-0.2 rounded font-mono">
                  DUPLICATE SUPPRESSED
                </span>
              )}
            </div>
            <div className="text-[11px] mt-0.5 opacity-90 break-words">{lastResult.message}</div>
          </div>
        </div>
      )}
    </div>
  );
}
