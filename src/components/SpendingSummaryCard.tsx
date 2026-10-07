import { SpendTotals } from '../types';
import { Calendar, TrendingDown, Clock, Award, Globe, EyeOff } from 'lucide-react';

interface SpendingSummaryCardProps {
  totals: SpendTotals;
}

export function SpendingSummaryCard({ totals }: SpendingSummaryCardProps) {
  const foreignCurrencies = Object.keys(totals.foreignTotals);

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl shadow-slate-950/40 relative overflow-hidden">
      {/* Background soft glow */}
      <div className="absolute -top-12 -right-12 w-36 h-36 bg-emerald-500/10 rounded-full blur-2xl pointer-events-none" />

      <div className="flex items-center justify-between text-xs text-slate-400 mb-1 font-medium tracking-wider uppercase">
        <span className="flex items-center gap-1.5">
          <Calendar className="w-3.5 h-3.5 text-emerald-400" />
          This Month's Spending (UAE)
        </span>
        <div className="flex items-center gap-1.5">
          {totals.ignoredCount > 0 && (
            <span className="bg-amber-950/60 text-amber-400 border border-amber-800/40 px-2 py-0.5 rounded text-[10px] flex items-center gap-1">
              <EyeOff className="w-3 h-3" />
              {totals.ignoredCount} ignored
            </span>
          )}
          <span className="bg-slate-800/80 px-2 py-0.5 rounded text-[11px] text-slate-300 font-mono">
            {totals.aedCount} {totals.aedCount === 1 ? 'AED expense' : 'AED expenses'}
          </span>
        </div>
      </div>

      {/* Primary AED Hero Number */}
      <div className="flex items-baseline gap-2 mt-1 mb-4">
        <span className="text-3xl sm:text-4xl font-extrabold text-white tracking-tight">
          AED {totals.aedThisMonth.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
        </span>
        <span className="text-xs text-rose-400 font-medium flex items-center">
          <TrendingDown className="w-3.5 h-3.5 mr-0.5 inline" />
          outgoing
        </span>
      </div>

      {/* AED Period Breakdown Grid */}
      <div className="grid grid-cols-3 gap-2 pt-3 border-t border-slate-800/80">
        <div className="bg-slate-950/60 rounded-xl p-2.5 border border-slate-800/50">
          <div className="flex items-center gap-1 text-[11px] text-slate-400 mb-1">
            <Clock className="w-3 h-3 text-slate-500" />
            <span>Today</span>
          </div>
          <div className="text-sm sm:text-base font-bold text-slate-100">
            AED {totals.aedToday.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
          </div>
        </div>

        <div className="bg-slate-950/60 rounded-xl p-2.5 border border-slate-800/50">
          <div className="flex items-center gap-1 text-[11px] text-slate-400 mb-1">
            <Calendar className="w-3 h-3 text-slate-500" />
            <span>This Week</span>
          </div>
          <div className="text-sm sm:text-base font-bold text-slate-100">
            AED {totals.aedThisWeek.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
          </div>
        </div>

        <div className="bg-slate-950/60 rounded-xl p-2.5 border border-slate-800/50">
          <div className="flex items-center gap-1 text-[11px] text-slate-400 mb-1">
            <Award className="w-3 h-3 text-slate-500" />
            <span>All Time</span>
          </div>
          <div className="text-sm sm:text-base font-bold text-slate-100">
            AED {totals.aedAllTime.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
          </div>
        </div>
      </div>

      {/* Foreign Currencies (Kept strictly separate from AED totals) */}
      {foreignCurrencies.length > 0 && (
        <div className="mt-3 pt-3 border-t border-slate-800/60">
          <div className="flex items-center gap-1 text-[11px] font-semibold text-slate-400 uppercase tracking-wider mb-2">
            <Globe className="w-3 h-3 text-blue-400" />
            <span>Foreign Currency Expenses (Tracked Separately)</span>
          </div>
          <div className="flex flex-wrap gap-2">
            {foreignCurrencies.map((curr) => {
              const item = totals.foreignTotals[curr];
              return (
                <div
                  key={curr}
                  className="bg-blue-950/40 border border-blue-500/30 px-3 py-1.5 rounded-xl text-xs text-blue-200 flex items-center gap-2"
                >
                  <span className="font-bold font-mono text-blue-300">{curr}</span>
                  <span className="font-semibold text-white">
                    {item.total.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                  </span>
                  <span className="text-[10px] text-blue-400/80">({item.count} items)</span>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
