import { useState } from 'react';
import { Transaction } from '../types';
import {
  Search,
  Trash2,
  Coffee,
  ShoppingBag,
  ShoppingCart,
  Car,
  Tv,
  FileText,
  CreditCard,
  Download,
  Filter,
  EyeOff,
  Eye,
  Edit2,
  Check,
  Tag,
  Building,
  DollarSign,
} from 'lucide-react';

interface TransactionListProps {
  transactions: Transaction[];
  onDelete: (id: number) => void;
  onClearAll: () => void;
  onUpdateCategory: (id: number, newCategory: string) => void;
  onToggleIgnore: (id: number) => void;
}

const UAE_CATEGORIES = [
  'Groceries',
  'Food & Dining',
  'Transport & Fuel',
  'Utilities & Telecom',
  'Shopping',
  'Entertainment',
  'Health & Pharmacy',
  'Cash / ATM',
  'Bank Fees & Charges',
  'General',
];

export function TransactionList({
  transactions,
  onDelete,
  onClearAll,
  onUpdateCategory,
  onToggleIgnore,
}: TransactionListProps) {
  const [searchTerm, setSearchTerm] = useState('');
  const [timeFilter, setTimeFilter] = useState<'all' | 'today' | 'week' | 'month' | 'ruya' | 'ignored'>('all');
  const [editingCategoryId, setEditingCategoryId] = useState<number | null>(null);

  const now = new Date();
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const startOfWeek = new Date(now.getFullYear(), now.getMonth(), now.getDate() - now.getDay()).getTime();
  const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1).getTime();

  const filtered = transactions.filter((t) => {
    // Time & bank filter
    if (timeFilter === 'today' && t.timestamp < startOfToday) return false;
    if (timeFilter === 'week' && t.timestamp < startOfWeek) return false;
    if (timeFilter === 'month' && t.timestamp < startOfMonth) return false;
    if (timeFilter === 'ruya' && t.bankSource.toLowerCase() !== 'ruya') return false;
    if (timeFilter === 'ignored' && !t.isIgnored) return false;

    // Search filter
    if (searchTerm.trim()) {
      const q = searchTerm.toLowerCase();
      return (
        t.merchant.toLowerCase().includes(q) ||
        t.bankSource.toLowerCase().includes(q) ||
        t.category.toLowerCase().includes(q) ||
        t.currency.toLowerCase().includes(q) ||
        t.rawMessage.toLowerCase().includes(q)
      );
    }
    return true;
  });

  const getCategoryIcon = (category: string) => {
    switch (category) {
      case 'Food & Dining':
        return <Coffee className="w-4 h-4 text-amber-400" />;
      case 'Groceries':
        return <ShoppingCart className="w-4 h-4 text-emerald-400" />;
      case 'Shopping':
        return <ShoppingBag className="w-4 h-4 text-blue-400" />;
      case 'Transport & Fuel':
        return <Car className="w-4 h-4 text-orange-400" />;
      case 'Entertainment':
        return <Tv className="w-4 h-4 text-purple-400" />;
      case 'Utilities & Telecom':
        return <FileText className="w-4 h-4 text-cyan-400" />;
      case 'Bank Fees & Charges':
        return <DollarSign className="w-4 h-4 text-rose-400" />;
      default:
        return <CreditCard className="w-4 h-4 text-slate-400" />;
    }
  };

  const formatDate = (timestamp: number) => {
    const d = new Date(timestamp);
    return d.toLocaleDateString('en-GB', {
      day: 'numeric',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const exportCsv = () => {
    if (transactions.length === 0) return;
    const headers = [
      'ID',
      'Date',
      'Bank',
      'Merchant',
      'Amount',
      'Currency',
      'Type',
      'Category',
      'Ignored',
      'Raw Message',
    ];
    const rows = transactions.map((t) => [
      t.id,
      new Date(t.timestamp).toISOString(),
      `"${t.bankSource.replace(/"/g, '""')}"`,
      `"${t.merchant.replace(/"/g, '""')}"`,
      t.amount,
      t.currency,
      t.transactionType,
      `"${t.category.replace(/"/g, '""')}"`,
      t.isIgnored ? 'YES' : 'NO',
      `"${t.rawMessage.replace(/"/g, '""')}"`,
    ]);
    const csvContent = [headers.join(','), ...rows.map((e) => e.join(','))].join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', 'spendwatch_uae_transactions.csv');
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-lg space-y-4">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-slate-100 flex items-center gap-2">
            <span>Recorded UAE Expenses</span>
            <span className="text-xs bg-slate-800 text-slate-300 px-2 py-0.5 rounded-full font-mono">
              {filtered.length}
            </span>
          </h3>
          <p className="text-xs text-slate-400">
            Stored in local SQLite database • Manual category corrections &amp; ignore toggling
          </p>
        </div>

        <div className="flex items-center gap-2">
          {transactions.length > 0 && (
            <>
              <button
                onClick={exportCsv}
                className="flex items-center gap-1 text-xs px-2.5 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 transition-colors"
                title="Export CSV"
              >
                <Download className="w-3.5 h-3.5" />
                <span>Export CSV</span>
              </button>
              <button
                onClick={() => {
                  if (confirm('Clear all local transactions from SQLite?')) {
                    onClearAll();
                  }
                }}
                className="flex items-center gap-1 text-xs px-2.5 py-1.5 rounded-xl bg-rose-950/50 hover:bg-rose-900/60 text-rose-300 border border-rose-800/40 transition-colors"
                title="Clear Database"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span>Clear</span>
              </button>
            </>
          )}
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
            placeholder="Search merchant, Ruya, ENOC, Talabat..."
            className="w-full pl-9 pr-3 py-1.5 bg-slate-950 border border-slate-800 rounded-xl text-xs text-slate-200 focus:outline-none focus:border-emerald-500"
          />
        </div>

        <div className="flex flex-wrap items-center gap-1 bg-slate-950 border border-slate-800 rounded-xl p-1 text-xs">
          <Filter className="w-3 h-3 text-slate-500 ml-1.5 mr-0.5" />
          {(['all', 'today', 'week', 'month', 'ruya', 'ignored'] as const).map((filter) => (
            <button
              key={filter}
              onClick={() => setTimeFilter(filter)}
              className={`px-2 py-1 rounded-lg capitalize transition-colors ${
                timeFilter === filter
                  ? 'bg-slate-800 text-emerald-400 font-semibold shadow-sm'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              {filter === 'ruya' ? 'Ruya Only' : filter}
            </button>
          ))}
        </div>
      </div>

      {/* Transaction Entries List */}
      <div className="space-y-2">
        {filtered.length === 0 ? (
          <div className="text-center py-10 px-4 bg-slate-950/50 rounded-xl border border-dashed border-slate-800 text-slate-400 text-xs">
            <CreditCard className="w-8 h-8 text-slate-600 mx-auto mb-2" />
            <p className="font-medium text-slate-300">No outgoing transactions found</p>
            <p className="text-[11px] text-slate-500 mt-1">
              {searchTerm || timeFilter !== 'all'
                ? 'Try adjusting your search query or filter.'
                : 'Simulate or receive a UAE bank notification above to populate your database.'}
            </p>
          </div>
        ) : (
          filtered.map((t) => (
            <div
              key={t.id}
              className={`p-3.5 rounded-xl border transition-all flex flex-col sm:flex-row sm:items-center justify-between gap-3 group ${
                t.isIgnored
                  ? 'bg-slate-950/40 border-slate-800/50 opacity-60'
                  : 'bg-slate-950/80 border-slate-800/80 hover:border-slate-700/80'
              }`}
            >
              {/* Left Details */}
              <div className="flex items-start sm:items-center gap-3 min-w-0">
                <div className="p-2.5 bg-slate-900 border border-slate-800 rounded-xl shrink-0 mt-0.5 sm:mt-0">
                  {getCategoryIcon(t.category)}
                </div>

                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <span
                      className={`font-semibold text-xs truncate ${
                        t.isIgnored ? 'line-through text-slate-500' : 'text-slate-100'
                      }`}
                    >
                      {t.merchant}
                    </span>

                    {/* Bank Badge */}
                    <span
                      className={`text-[9px] px-1.5 py-0.2 rounded font-mono font-semibold ${
                        t.bankSource.toLowerCase() === 'ruya'
                          ? 'bg-emerald-950 text-emerald-400 border border-emerald-800/40'
                          : 'bg-slate-800 text-slate-400'
                      }`}
                    >
                      {t.bankSource}
                    </span>

                    {/* Transaction Type Badge */}
                    <span className="text-[9px] px-1.5 py-0.2 rounded bg-slate-900 text-slate-400 border border-slate-800 capitalize">
                      {t.transactionType.replace('_', ' ')}
                    </span>

                    {/* Ignored badge */}
                    {t.isIgnored && (
                      <span className="text-[9px] px-1.5 py-0.2 rounded bg-amber-950 text-amber-400 border border-amber-800/40 font-mono">
                        IGNORED FROM TOTALS
                      </span>
                    )}
                  </div>

                  <div className="text-[11px] text-slate-400 flex items-center gap-2 mt-1 truncate">
                    <span>{formatDate(t.timestamp)}</span>
                    <span>•</span>
                    {/* Category Selector / Editor */}
                    {editingCategoryId === t.id ? (
                      <div className="flex items-center gap-1">
                        <select
                          value={t.category}
                          onChange={(e) => {
                            onUpdateCategory(t.id, e.target.value);
                            setEditingCategoryId(null);
                          }}
                          className="bg-slate-900 text-emerald-400 border border-emerald-500 rounded px-1.5 py-0.5 text-[11px] focus:outline-none"
                        >
                          {UAE_CATEGORIES.map((c) => (
                            <option key={c} value={c}>
                              {c}
                            </option>
                          ))}
                        </select>
                        <button
                          onClick={() => setEditingCategoryId(null)}
                          className="p-1 text-slate-400 hover:text-white"
                        >
                          <Check className="w-3 h-3" />
                        </button>
                      </div>
                    ) : (
                      <button
                        onClick={() => setEditingCategoryId(t.id)}
                        className="text-slate-300 hover:text-emerald-400 flex items-center gap-1 text-[11px] group/cat underline decoration-slate-700 hover:decoration-emerald-400"
                        title="Click to change category"
                      >
                        <Tag className="w-3 h-3 text-slate-500 group-hover/cat:text-emerald-400" />
                        <span>{t.category}</span>
                        {t.isManual && <span className="text-[9px] text-emerald-400">(edited)</span>}
                      </button>
                    )}
                  </div>
                </div>
              </div>

              {/* Right: Amount & Actions */}
              <div className="flex items-center justify-between sm:justify-end gap-3 shrink-0 pt-2 sm:pt-0 border-t sm:border-t-0 border-slate-800/50">
                <div className="text-left sm:text-right">
                  <div
                    className={`text-sm font-bold font-mono ${
                      t.isIgnored ? 'line-through text-slate-500' : 'text-rose-400'
                    }`}
                  >
                    -{t.amount.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                  </div>
                  <div className="text-[10px] font-bold text-slate-400 uppercase tracking-wider font-mono">
                    {t.currency}
                    {t.currency !== 'AED' && (
                      <span className="ml-1 text-[9px] text-blue-400">(Foreign)</span>
                    )}
                  </div>
                </div>

                <div className="flex items-center gap-1">
                  {/* Ignore Toggle Button */}
                  <button
                    onClick={() => onToggleIgnore(t.id)}
                    className={`p-1.5 rounded-lg text-xs transition-colors ${
                      t.isIgnored
                        ? 'text-amber-400 hover:bg-amber-950/40 bg-amber-950/20'
                        : 'text-slate-500 hover:text-amber-400 hover:bg-slate-800'
                    }`}
                    title={t.isIgnored ? 'Restore transaction to totals' : 'Ignore from spending totals'}
                  >
                    {t.isIgnored ? <Eye className="w-3.5 h-3.5" /> : <EyeOff className="w-3.5 h-3.5" />}
                  </button>

                  {/* Delete Button */}
                  <button
                    onClick={() => onDelete(t.id)}
                    className="p-1.5 text-slate-500 hover:text-rose-400 hover:bg-rose-950/40 rounded-lg transition-all"
                    title="Delete permanently"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
