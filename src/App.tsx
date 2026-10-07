/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { useState, useEffect } from 'react';
import {
  getStoredTransactions,
  insertTransaction,
  deleteTransactionById,
  clearAllTransactions,
  calculateSpendTotals,
  isNotificationAccessGranted,
  setNotificationAccessGranted,
  updateTransactionCategory,
  toggleIgnoreTransaction,
  getDebugLogs,
  addDebugLog,
  clearDebugLogs,
  getStoredRuyaConfig,
  saveRuyaConfig,
} from './storage';
import { Transaction, DebugNotificationLog, RuyaConfig } from './types';
import { parseUaeBankNotification } from './parser';
import { PrivacyBanner } from './components/PrivacyBanner';
import { SpendingSummaryCard } from './components/SpendingSummaryCard';
import { NotificationSimulator } from './components/NotificationSimulator';
import { TransactionList } from './components/TransactionList';
import { NotificationPermissionBanner } from './components/NotificationPermissionBanner';
import { AndroidSourceCodeModal } from './components/AndroidSourceCodeModal';
import { DebugScreen } from './components/DebugScreen';
import { RuyaConfigModal } from './components/RuyaConfigModal';
import {
  Smartphone,
  LayoutGrid,
  Shield,
  Code2,
  Bug,
  Sliders,
  BellRing,
} from 'lucide-react';

export default function App() {
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [debugLogs, setDebugLogs] = useState<DebugNotificationLog[]>([]);
  const [ruyaConfig, setRuyaConfig] = useState<RuyaConfig>(getStoredRuyaConfig());
  const [permissionGranted, setPermissionGranted] = useState<boolean>(true);
  const [viewMode, setViewMode] = useState<'device' | 'full'>('full');
  const [activeTab, setActiveTab] = useState<'app' | 'debug' | 'source'>('app');
  const [showRuyaModal, setShowRuyaModal] = useState<boolean>(false);
  const [headsUpToast, setHeadsUpToast] = useState<{
    title: string;
    body: string;
    isAccepted: boolean;
  } | null>(null);

  useEffect(() => {
    setTransactions(getStoredTransactions());
    setDebugLogs(getDebugLogs());
    setRuyaConfig(getStoredRuyaConfig());
    setPermissionGranted(isNotificationAccessGranted());
  }, []);

  const handleTransactionCaptured = (tx: Transaction, _isDuplicate: boolean) => {
    const { transactions: updated, isDuplicate } = insertTransaction(tx);
    setTransactions(updated);

    // Show simulated Android heads-up notification banner
    setHeadsUpToast({
      title: `${tx.bankSource} • ${tx.merchant}`,
      body: isDuplicate
        ? `Duplicate suppressed: -${tx.currency} ${tx.amount.toFixed(2)}`
        : `-${tx.currency} ${tx.amount.toFixed(2)} (${tx.category})`,
      isAccepted: !isDuplicate,
    });
    setTimeout(() => {
      setHeadsUpToast(null);
    }, 4000);
  };

  const handleRecordDebugLog = (
    title: string,
    body: string,
    pkg: string,
    result: ReturnType<typeof parseUaeBankNotification>
  ) => {
    const newLog: DebugNotificationLog = {
      id: `${Date.now()}-${Math.random()}`,
      timestamp: Date.now(),
      rawText: `${title ? title + ' : ' : ''}${body}`.trim(),
      packageName: pkg,
      senderOrTitle: title,
      detectedBank: result.detectedBank,
      isAccepted: result.isAccepted,
      parsedAmount: result.transaction?.amount,
      parsedCurrency: result.transaction?.currency,
      merchant: result.transaction?.merchant,
      transactionType: result.transactionType,
      reason: result.reason,
    };
    const updated = addDebugLog(newLog);
    setDebugLogs(updated);

    if (!result.isAccepted) {
      setHeadsUpToast({
        title: `${result.detectedBank} Notification`,
        body: result.reason || 'Ignored / Rejected',
        isAccepted: false,
      });
      setTimeout(() => {
        setHeadsUpToast(null);
      }, 3500);
    }
  };

  const handleSimulateRaw = (title: string, body: string, pkg: string) => {
    const result = parseUaeBankNotification(title, body, pkg, Date.now(), ruyaConfig);
    handleRecordDebugLog(title, body, pkg, result);
    if (result.isAccepted && result.transaction) {
      handleTransactionCaptured(result.transaction, false);
    }
  };

  const handleDeleteTransaction = (id: number) => {
    const updated = deleteTransactionById(id);
    setTransactions(updated);
  };

  const handleClearAll = () => {
    const updated = clearAllTransactions();
    setTransactions(updated);
  };

  const handleUpdateCategory = (id: number, newCategory: string) => {
    const updated = updateTransactionCategory(id, newCategory);
    setTransactions(updated);
  };

  const handleToggleIgnore = (id: number) => {
    const updated = toggleIgnoreTransaction(id);
    setTransactions(updated);
  };

  const handleClearDebugLogs = () => {
    clearDebugLogs();
    setDebugLogs([]);
  };

  const handleSaveRuyaConfig = (updated: RuyaConfig) => {
    saveRuyaConfig(updated);
    setRuyaConfig(updated);
  };

  const handleTogglePermission = (granted: boolean) => {
    setNotificationAccessGranted(granted);
    setPermissionGranted(granted);
  };

  const totals = calculateSpendTotals(transactions);

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans selection:bg-emerald-500/30 selection:text-emerald-200">
      {/* Heads-up Android notification simulation toast */}
      {headsUpToast && (
        <div
          className={`fixed top-4 left-1/2 -translate-x-1/2 z-50 w-11/12 max-w-md backdrop-blur-md border shadow-2xl rounded-2xl p-3.5 flex items-center gap-3 transition-all animate-bounce ${
            headsUpToast.isAccepted
              ? 'bg-slate-900/95 border-emerald-500/60 shadow-emerald-950/80'
              : 'bg-slate-900/95 border-rose-500/60 shadow-rose-950/80'
          }`}
        >
          <div
            className={`w-10 h-10 rounded-xl flex items-center justify-center font-bold text-sm shrink-0 ${
              headsUpToast.isAccepted ? 'bg-emerald-500/20 text-emerald-400' : 'bg-rose-500/20 text-rose-400'
            }`}
          >
            <BellRing className="w-5 h-5" />
          </div>
          <div className="min-w-0 flex-1">
            <div className="text-xs font-semibold text-slate-100 truncate">
              {headsUpToast.title}
            </div>
            <div
              className={`text-xs font-bold truncate ${
                headsUpToast.isAccepted ? 'text-emerald-400' : 'text-rose-400'
              }`}
            >
              {headsUpToast.body}
            </div>
            <div className="text-[10px] text-slate-400 mt-0.5">
              {headsUpToast.isAccepted ? 'Logged to local SQLite database' : 'Rejection rule applied'}
            </div>
          </div>
        </div>
      )}

      {/* Ruya Config Modal */}
      {showRuyaModal && (
        <RuyaConfigModal
          config={ruyaConfig}
          onSaveConfig={handleSaveRuyaConfig}
          onClose={() => setShowRuyaModal(false)}
        />
      )}

      {/* Top Navigation Bar */}
      <header className="border-b border-slate-800 bg-slate-900/80 backdrop-blur-md sticky top-0 z-40">
        <div className="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-emerald-600 to-teal-400 flex items-center justify-center font-bold text-white shadow-md shadow-emerald-900/50">
              <Shield className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="font-bold text-base text-white tracking-tight">
                  SpendWatch Local
                </h1>
                <span className="text-[10px] bg-emerald-950 text-emerald-400 border border-emerald-800/40 px-2 py-0.5 rounded-full font-bold">
                  UAE • RUYA
                </span>
                <span className="hidden sm:inline-block text-[10px] bg-slate-800 text-slate-300 px-2 py-0.5 rounded-full font-mono">
                  AED PRIMARY
                </span>
              </div>
              <p className="text-[11px] text-slate-400">
                Local UAE Banking Notification Parser &amp; Expense Tracker
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {/* View Mode Tabs */}
            <div className="hidden sm:flex items-center bg-slate-950 border border-slate-800 p-0.5 rounded-xl text-xs">
              <button
                onClick={() => setActiveTab('app')}
                className={`px-3 py-1.5 rounded-lg flex items-center gap-1.5 font-medium transition-colors ${
                  activeTab === 'app'
                    ? 'bg-slate-800 text-emerald-400 shadow-sm font-semibold'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                <LayoutGrid className="w-3.5 h-3.5" />
                <span>Dashboard</span>
              </button>
              <button
                onClick={() => setActiveTab('debug')}
                className={`px-3 py-1.5 rounded-lg flex items-center gap-1.5 font-medium transition-colors ${
                  activeTab === 'debug'
                    ? 'bg-slate-800 text-emerald-400 shadow-sm font-semibold'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                <Bug className="w-3.5 h-3.5" />
                <span>Debug Screen</span>
                {debugLogs.length > 0 && (
                  <span className="text-[9px] bg-emerald-950 text-emerald-400 px-1.5 rounded-full font-mono">
                    {debugLogs.length}
                  </span>
                )}
              </button>
              <button
                onClick={() => setActiveTab('source')}
                className={`px-3 py-1.5 rounded-lg flex items-center gap-1.5 font-medium transition-colors ${
                  activeTab === 'source'
                    ? 'bg-slate-800 text-emerald-400 shadow-sm font-semibold'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                <Code2 className="w-3.5 h-3.5" />
                <span>Android Source</span>
              </button>
            </div>

            {/* Ruya Config Button */}
            <button
              onClick={() => setShowRuyaModal(true)}
              className="p-2 rounded-xl bg-slate-900 border border-slate-800 text-slate-300 hover:text-emerald-400 hover:border-emerald-500/40 transition-colors"
              title="Configure Ruya SMS & Matching Rules"
            >
              <Sliders className="w-4 h-4" />
            </button>

            {/* Phone Frame Toggle */}
            <button
              onClick={() => setViewMode(viewMode === 'device' ? 'full' : 'device')}
              className={`p-2 rounded-xl border text-xs flex items-center gap-1.5 transition-colors ${
                viewMode === 'device'
                  ? 'bg-emerald-950/60 border-emerald-500/50 text-emerald-300'
                  : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200'
              }`}
              title={viewMode === 'device' ? 'Switch to Full Dashboard' : 'Switch to Android Frame'}
            >
              <Smartphone className="w-4 h-4" />
              <span className="hidden md:inline">
                {viewMode === 'device' ? 'Phone Mode' : 'Full Mode'}
              </span>
            </button>
          </div>
        </div>

        {/* Mobile Navigation Tabs */}
        <div className="flex sm:hidden border-t border-slate-800/60 bg-slate-950 px-4 py-1.5 gap-2 text-xs">
          <button
            onClick={() => setActiveTab('app')}
            className={`flex-1 py-1 rounded-lg text-center font-medium ${
              activeTab === 'app' ? 'bg-slate-800 text-emerald-400' : 'text-slate-400'
            }`}
          >
            Dashboard
          </button>
          <button
            onClick={() => setActiveTab('debug')}
            className={`flex-1 py-1 rounded-lg text-center font-medium ${
              activeTab === 'debug' ? 'bg-slate-800 text-emerald-400' : 'text-slate-400'
            }`}
          >
            Debug ({debugLogs.length})
          </button>
          <button
            onClick={() => setActiveTab('source')}
            className={`flex-1 py-1 rounded-lg text-center font-medium ${
              activeTab === 'source' ? 'bg-slate-800 text-emerald-400' : 'text-slate-400'
            }`}
          >
            Android
          </button>
        </div>
      </header>

      {/* Main Container */}
      <main className="flex-1 max-w-6xl w-full mx-auto p-4 md:p-6">
        {activeTab === 'debug' ? (
          <DebugScreen
            debugLogs={debugLogs}
            onClearLogs={handleClearDebugLogs}
            onSimulateRaw={handleSimulateRaw}
            ruyaConfig={ruyaConfig}
          />
        ) : activeTab === 'source' ? (
          <div className="space-y-6">
            <AndroidSourceCodeModal />
          </div>
        ) : viewMode === 'device' ? (
          /* Android Phone Simulation Container */
          <div className="flex justify-center py-4">
            <div className="w-full max-w-[420px] bg-slate-950 rounded-[40px] border-8 border-slate-800 shadow-2xl p-4 overflow-hidden relative">
              {/* Android Notch / Speaker */}
              <div className="w-28 h-4 bg-slate-800 mx-auto rounded-b-xl mb-3 flex items-center justify-center">
                <div className="w-3 h-3 bg-slate-900 rounded-full" />
              </div>

              {/* Android Status Bar */}
              <div className="flex items-center justify-between text-[11px] text-slate-400 px-2 mb-3">
                <span className="font-semibold text-slate-200">12:30</span>
                <div className="flex items-center gap-1.5">
                  <span className="text-[10px] text-emerald-400 font-bold">🔒 ZERO INTERNET</span>
                  <span>100%</span>
                </div>
              </div>

              <div className="space-y-3.5 max-h-[720px] overflow-y-auto pr-1">
                <NotificationPermissionBanner
                  isGranted={permissionGranted}
                  onToggle={handleTogglePermission}
                />
                <PrivacyBanner />
                <SpendingSummaryCard totals={totals} />
                <NotificationSimulator
                  onTransactionCaptured={handleTransactionCaptured}
                  onRecordDebugLog={handleRecordDebugLog}
                  isPermissionGranted={permissionGranted}
                  ruyaConfig={ruyaConfig}
                  onOpenRuyaConfig={() => setShowRuyaModal(true)}
                />
                <TransactionList
                  transactions={transactions}
                  onDelete={handleDeleteTransaction}
                  onClearAll={handleClearAll}
                  onUpdateCategory={handleUpdateCategory}
                  onToggleIgnore={handleToggleIgnore}
                />
              </div>

              {/* Android Navigation bar indicator */}
              <div className="w-32 h-1 bg-slate-700 mx-auto rounded-full mt-4" />
            </div>
          </div>
        ) : (
          /* Full Wide Dashboard Mode */
          <div className="space-y-6">
            <NotificationPermissionBanner
              isGranted={permissionGranted}
              onToggle={handleTogglePermission}
            />

            <PrivacyBanner />

            {/* Grid layout */}
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
              {/* Left Column: Totals & Notification Simulator */}
              <div className="lg:col-span-5 space-y-6">
                <SpendingSummaryCard totals={totals} />
                <NotificationSimulator
                  onTransactionCaptured={handleTransactionCaptured}
                  onRecordDebugLog={handleRecordDebugLog}
                  isPermissionGranted={permissionGranted}
                  ruyaConfig={ruyaConfig}
                  onOpenRuyaConfig={() => setShowRuyaModal(true)}
                />
              </div>

              {/* Right Column: Transactions Database */}
              <div className="lg:col-span-7 space-y-6">
                <TransactionList
                  transactions={transactions}
                  onDelete={handleDeleteTransaction}
                  onClearAll={handleClearAll}
                  onUpdateCategory={handleUpdateCategory}
                  onToggleIgnore={handleToggleIgnore}
                />
              </div>
            </div>
          </div>
        )}
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-800/80 bg-slate-900/50 py-4 text-center text-xs text-slate-500">
        SpendWatch Local (UAE) • Primary Bank: Ruya • Local SQLite &amp; Zero Internet Permission Requested
      </footer>
    </div>
  );
}
