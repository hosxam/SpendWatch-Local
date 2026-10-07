import { useState } from 'react';
import { DebugNotificationLog, RuyaConfig } from '../types';
import { parseUaeBankNotification } from '../parser';
import {
  Bug,
  CheckCircle,
  XCircle,
  Trash2,
  Play,
  Filter,
  Building,
  DollarSign,
  Tag,
  Search,
} from 'lucide-react';

interface DebugScreenProps {
  debugLogs: DebugNotificationLog[];
  onClearLogs: () => void;
  onSimulateRaw: (title: string, body: string, pkg: string) => void;
  ruyaConfig: RuyaConfig;
}

export function DebugScreen({
  debugLogs,
  onClearLogs,
  onSimulateRaw,
  ruyaConfig,
}: DebugScreenProps) {
  const [testTitle, setTestTitle] = useState('Ruya');
  const [testBody, setTestBody] = useState(
    'Your Ruya card ending 4091 was used for AED 142.50 at CARREFOUR DEIRA on 07/10/2026.'
  );
  const [testPkg, setTestPkg] = useState('ae.ruya.digital');
  const [filterMode, setFilterMode] = useState<'all' | 'accepted' | 'ignored'>('all');
  const [searchTerm, setSearchTerm] = useState('');

  const handleTestNow = () => {
    if (!testBody.trim()) return;
    onSimulateRaw(testTitle, testBody, testPkg);
  };

  const filteredLogs = debugLogs.filter((log) => {
    if (filterMode === 'accepted' && !log.isAccepted) return false;
    if (filterMode === 'ignored' && log.isAccepted) return false;
    if (searchTerm.trim()) {
      const q = searchTerm.toLowerCase();
      return (
        log.rawText.toLowerCase().includes(q) ||
        log.detectedBank.toLowerCase().includes(q) ||
        (log.merchant && log.merchant.toLowerCase().includes(q)) ||
        (log.reason && log.reason.toLowerCase().includes(q))
      );
    }
    return true;
  });

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-lg space-y-5">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-800 pb-4">
        <div>
          <div className="flex items-center gap-2">
            <div className="p-2 bg-emerald-500/10 text-emerald-400 rounded-lg">
              <Bug className="w-4 h-4" />
            </div>
            <h2 className="text-base font-bold text-slate-100 flex items-center gap-2">
              <span>Local Notification Debugger</span>
              <span className="text-[10px] bg-slate-800 text-emerald-400 px-2 py-0.5 rounded font-mono">
                100% ON-DEVICE
              </span>
            </h2>
          </div>
          <p className="text-xs text-slate-400 mt-0.5">
            Real-time parser decisions, extraction diagnostics, and rejection reasons.
          </p>
        </div>

        {debugLogs.length > 0 && (
          <button
            onClick={onClearLogs}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-medium transition-colors"
          >
            <Trash2 className="w-3.5 h-3.5 text-slate-400" />
            <span>Clear Logs</span>
          </button>
        )}
      </div>

      {/* Direct Test Sandbox Box */}
      <div className="bg-slate-950/80 border border-slate-800 rounded-xl p-4 space-y-3">
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold text-slate-200 flex items-center gap-1.5">
            <Play className="w-3.5 h-3.5 text-emerald-400" />
            Live Notification Inspector Sandbox
          </span>
          <span className="text-[10px] text-slate-500">
            Paste any real SMS or push notification from your phone
          </span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
          <div>
            <label className="block text-[10px] uppercase font-bold text-slate-400 mb-1">
              Sender ID / App Title
            </label>
            <input
              type="text"
              value={testTitle}
              onChange={(e) => setTestTitle(e.target.value)}
              placeholder="e.g. Ruya, RUYA, Emirates NBD"
              className="w-full bg-slate-900 border border-slate-800 rounded-lg px-2.5 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-emerald-500 font-mono"
            />
          </div>
          <div className="sm:col-span-2">
            <label className="block text-[10px] uppercase font-bold text-slate-400 mb-1">
              Notification Text / SMS Body
            </label>
            <input
              type="text"
              value={testBody}
              onChange={(e) => setTestBody(e.target.value)}
              placeholder="e.g. Your Ruya card ending 4091 was used for AED 85.00 at..."
              className="w-full bg-slate-900 border border-slate-800 rounded-lg px-2.5 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-emerald-500 font-mono"
            />
          </div>
        </div>

        <div className="flex justify-end pt-1">
          <button
            onClick={handleTestNow}
            className="px-4 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg text-xs font-semibold flex items-center gap-1.5 transition-colors shadow-md"
          >
            <Play className="w-3.5 h-3.5" />
            <span>Parse &amp; Record to Debug Log</span>
          </button>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="flex flex-col sm:flex-row gap-2">
        <div className="relative flex-1">
          <Search className="w-3.5 h-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Search raw text, merchant, bank, or reason..."
            className="w-full pl-9 pr-3 py-1.5 bg-slate-950 border border-slate-800 rounded-xl text-xs text-slate-200 focus:outline-none focus:border-emerald-500"
          />
        </div>

        <div className="flex items-center gap-1 bg-slate-950 border border-slate-800 rounded-xl p-1 text-xs">
          <Filter className="w-3 h-3 text-slate-500 ml-1.5 mr-0.5" />
          {(['all', 'accepted', 'ignored'] as const).map((mode) => (
            <button
              key={mode}
              onClick={() => setFilterMode(mode)}
              className={`px-2.5 py-1 rounded-lg capitalize transition-colors ${
                filterMode === mode
                  ? 'bg-slate-800 text-emerald-400 font-semibold shadow-sm'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              {mode}
            </button>
          ))}
        </div>
      </div>

      {/* Log Entries List */}
      <div className="space-y-3">
        {filteredLogs.length === 0 ? (
          <div className="p-8 text-center bg-slate-950/40 rounded-xl border border-dashed border-slate-800 text-slate-400 text-xs">
            <Bug className="w-8 h-8 text-slate-600 mx-auto mb-2" />
            <p className="font-semibold text-slate-300">No debug log entries yet</p>
            <p className="text-[11px] text-slate-500 mt-1">
              Trigger sample notifications from the UAE simulator or enter a custom test above.
            </p>
          </div>
        ) : (
          filteredLogs.map((log) => (
            <div
              key={log.id}
              className={`rounded-xl border p-4 transition-all text-xs ${
                log.isAccepted
                  ? 'bg-slate-950/80 border-slate-800 hover:border-emerald-500/40'
                  : 'bg-rose-950/20 border-rose-900/30 hover:border-rose-800/50'
              }`}
            >
              {/* Status Header */}
              <div className="flex items-start justify-between gap-3 mb-2">
                <div className="flex items-center gap-2">
                  {log.isAccepted ? (
                    <span className="flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-950 text-emerald-400 border border-emerald-800/50">
                      <CheckCircle className="w-3 h-3" />
                      ACCEPTED OUTGOING
                    </span>
                  ) : (
                    <span className="flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-950 text-rose-400 border border-rose-800/50">
                      <XCircle className="w-3 h-3" />
                      IGNORED / REJECTED
                    </span>
                  )}
                  <span className="text-[11px] font-semibold text-slate-300 flex items-center gap-1">
                    <Building className="w-3 h-3 text-slate-400" />
                    {log.detectedBank}
                  </span>
                  {log.detectedBank.toLowerCase() === 'ruya' && (
                    <span className="text-[9px] bg-emerald-900/60 text-emerald-300 px-1.5 py-0.2 rounded font-mono font-semibold">
                      PRIMARY
                    </span>
                  )}
                </div>

                <span className="text-[10px] text-slate-500 font-mono">
                  {new Date(log.timestamp).toLocaleTimeString()}
                </span>
              </div>

              {/* Raw Notification Text */}
              <div className="bg-slate-900/90 rounded-lg p-2.5 font-mono text-[11px] text-slate-300 mb-3 border border-slate-800/60 break-words leading-relaxed">
                <span className="text-slate-500 select-none mr-2">RAW:</span>
                "{log.rawText}"
              </div>

              {/* Parsed Breakdown Grid */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-[11px]">
                <div className="bg-slate-900/50 rounded-lg p-2 border border-slate-800/40">
                  <div className="text-[10px] text-slate-500 uppercase font-medium">
                    Parsed Amount
                  </div>
                  <div className="font-bold text-slate-200 mt-0.5">
                    {log.parsedAmount !== undefined ? (
                      <span className="text-emerald-400">
                        {log.parsedCurrency || 'AED'} {log.parsedAmount.toFixed(2)}
                      </span>
                    ) : (
                      <span className="text-slate-500">—</span>
                    )}
                  </div>
                </div>

                <div className="bg-slate-900/50 rounded-lg p-2 border border-slate-800/40">
                  <div className="text-[10px] text-slate-500 uppercase font-medium">
                    Merchant / Recipient
                  </div>
                  <div className="font-bold text-slate-200 truncate mt-0.5">
                    {log.merchant || '—'}
                  </div>
                </div>

                <div className="bg-slate-900/50 rounded-lg p-2 border border-slate-800/40">
                  <div className="text-[10px] text-slate-500 uppercase font-medium">
                    Transaction Type
                  </div>
                  <div className="font-bold text-slate-200 capitalize mt-0.5 flex items-center gap-1">
                    <Tag className="w-3 h-3 text-slate-400" />
                    {log.transactionType ? log.transactionType.replace('_', ' ') : '—'}
                  </div>
                </div>

                <div className="bg-slate-900/50 rounded-lg p-2 border border-slate-800/40">
                  <div className="text-[10px] text-slate-500 uppercase font-medium">
                    Status / Reason
                  </div>
                  <div className="font-medium text-slate-300 truncate mt-0.5" title={log.reason}>
                    {log.reason ? (
                      <span className="text-rose-400">{log.reason}</span>
                    ) : (
                      <span className="text-emerald-400">Recorded to SQLite</span>
                    )}
                  </div>
                </div>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
