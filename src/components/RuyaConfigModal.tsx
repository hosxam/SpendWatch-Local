import { useState } from 'react';
import { RuyaConfig } from '../types';
import { Shield, Plus, X, RotateCcw, Check, Sparkles } from 'lucide-react';
import { DEFAULT_RUYA_SENDER_ALIASES } from '../parser';

interface RuyaConfigModalProps {
  config: RuyaConfig;
  onSaveConfig: (updated: RuyaConfig) => void;
  onClose: () => void;
}

export function RuyaConfigModal({
  config,
  onSaveConfig,
  onClose,
}: RuyaConfigModalProps) {
  const [aliases, setAliases] = useState<string[]>([...config.senderAliases]);
  const [newAlias, setNewAlias] = useState('');
  const [savedSuccess, setSavedSuccess] = useState(false);

  const handleAddAlias = () => {
    const trimmed = newAlias.trim().toLowerCase();
    if (trimmed && !aliases.includes(trimmed)) {
      setAliases([...aliases, trimmed]);
      setNewAlias('');
    }
  };

  const handleRemoveAlias = (aliasToRemove: string) => {
    setAliases(aliases.filter((a) => a !== aliasToRemove));
  };

  const handleResetDefaults = () => {
    setAliases([...DEFAULT_RUYA_SENDER_ALIASES]);
  };

  const handleSave = () => {
    onSaveConfig({
      ...config,
      senderAliases: aliases,
    });
    setSavedSuccess(true);
    setTimeout(() => {
      setSavedSuccess(false);
      onClose();
    }, 1200);
  };

  return (
    <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-lg w-full p-6 shadow-2xl relative space-y-5 animate-fadeIn">
        <button
          onClick={onClose}
          className="absolute top-4 right-4 p-1.5 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition-colors"
        >
          <X className="w-4 h-4" />
        </button>

        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-emerald-500/20 text-emerald-400 flex items-center justify-center font-bold text-lg">
            <Shield className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-base font-bold text-white flex items-center gap-2">
              <span>Ruya Parsing Rules &amp; Sender Config</span>
              <span className="text-[10px] bg-emerald-950 text-emerald-400 border border-emerald-800/40 px-2 py-0.5 rounded-full font-mono">
                PRIMARY BANK
              </span>
            </h3>
            <p className="text-xs text-slate-400">
              Customize SMS sender IDs and app package matchers for Ruya on your device.
            </p>
          </div>
        </div>

        {/* Sender Aliases Management */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <label className="text-xs font-semibold text-slate-200">
              Active Ruya Sender Aliases &amp; Package IDs
            </label>
            <button
              onClick={handleResetDefaults}
              className="text-[11px] text-slate-400 hover:text-emerald-400 flex items-center gap-1 transition-colors"
            >
              <RotateCcw className="w-3 h-3" />
              <span>Reset Defaults</span>
            </button>
          </div>

          <div className="flex flex-wrap gap-2 max-h-40 overflow-y-auto p-1 bg-slate-950/60 rounded-xl border border-slate-800">
            {aliases.map((alias) => (
              <span
                key={alias}
                className="bg-emerald-950/60 border border-emerald-500/40 text-emerald-300 text-xs px-2.5 py-1 rounded-lg flex items-center gap-1.5 font-mono"
              >
                <span>{alias}</span>
                <button
                  onClick={() => handleRemoveAlias(alias)}
                  className="text-emerald-400 hover:text-rose-400 transition-colors"
                  title="Remove alias"
                >
                  <X className="w-3 h-3" />
                </button>
              </span>
            ))}
          </div>

          {/* Add New Sender ID */}
          <div className="flex gap-2">
            <input
              type="text"
              value={newAlias}
              onChange={(e) => setNewAlias(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleAddAlias()}
              placeholder="e.g. RUYASMS, ruyabank, com.ruyabank.app"
              className="flex-1 bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200 font-mono focus:outline-none focus:border-emerald-500"
            />
            <button
              onClick={handleAddAlias}
              className="px-3 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-xl text-xs font-semibold flex items-center gap-1 transition-colors"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Add</span>
            </button>
          </div>
        </div>

        {/* Matching Rule Explanation */}
        <div className="bg-slate-950/80 rounded-xl p-3 border border-slate-800 text-[11px] text-slate-400 space-y-1.5 leading-relaxed">
          <div className="text-slate-300 font-semibold flex items-center gap-1.5">
            <Sparkles className="w-3.5 h-3.5 text-emerald-400" />
            <span>How Ruya Detection Works:</span>
          </div>
          <p>
            1. Whenever an SMS or push notification arrives, SpendWatch checks the sender ID and package against this alias list.
          </p>
          <p>
            2. If matched, it runs the prioritized Ruya parser, extracting card purchases, POS, ATM withdrawals, online shopping, and fees in <strong>AED</strong>.
          </p>
          <p>
            3. Incoming salary transfers, refunds, OTPs, and declined alerts are strictly rejected.
          </p>
        </div>

        {/* Footer Actions */}
        <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-800">
          <button
            onClick={onClose}
            className="px-4 py-2 text-xs font-medium text-slate-400 hover:text-slate-200 transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white rounded-xl text-xs font-semibold flex items-center gap-1.5 shadow-lg shadow-emerald-950 transition-colors"
          >
            {savedSuccess ? (
              <>
                <Check className="w-3.5 h-3.5" />
                <span>Saved!</span>
              </>
            ) : (
              <span>Save Rules</span>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
