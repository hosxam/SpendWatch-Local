import {
  Transaction,
  SpendTotals,
  BankNotificationSample,
  DebugNotificationLog,
  RuyaConfig,
} from './types';
import { DEFAULT_RUYA_SENDER_ALIASES } from './parser';

const STORAGE_KEY_TRANSACTIONS = 'spendwatch_uae_transactions_v2';
const STORAGE_KEY_PERMISSION = 'spendwatch_uae_permission_granted';
const STORAGE_KEY_DEBUG_LOGS = 'spendwatch_uae_debug_logs_v1';
const STORAGE_KEY_RUYA_CONFIG = 'spendwatch_uae_ruya_config_v1';

// At least 20+ realistic UAE banking notification scenarios, with Ruya as primary bank!
export const UAE_NOTIFICATION_SAMPLES: BankNotificationSample[] = [
  // --- RUYA (PRIMARY BANK) ---
  {
    id: 'ruya-carrefour',
    bankName: 'Ruya',
    packageName: 'ae.ruya.digital',
    title: 'Ruya',
    body: 'Your Ruya card ending 4091 was used for AED 142.50 at CARREFOUR DEIRA on 07/10/2026. Avail Bal: AED 3,850.20.',
    expectedType: 'outgoing',
    explanation: 'Ruya card purchase of AED 142.50 at Carrefour Deira (Groceries).',
    isPrimary: true,
  },
  {
    id: 'ruya-amazon',
    bankName: 'Ruya',
    packageName: 'ae.ruya.digital',
    title: 'Ruya Bank Alert',
    body: 'Online purchase of AED 315.00 at AMAZON.AE approved on your Ruya Debit Card.',
    expectedType: 'outgoing',
    explanation: 'Ruya online eCommerce purchase of AED 315.00 at Amazon.ae (Shopping).',
    isPrimary: true,
  },
  {
    id: 'ruya-transfer-sent',
    bankName: 'Ruya',
    packageName: 'ae.ruya.digital',
    title: 'Ruya',
    body: 'Outward transfer of AED 1,200.00 sent to Omar Khalid was completed successfully.',
    expectedType: 'outgoing',
    explanation: 'Ruya outward transfer of AED 1,200.00 sent to recipient.',
    isPrimary: true,
  },
  {
    id: 'ruya-atm-cash',
    bankName: 'Ruya',
    packageName: 'ae.ruya.digital',
    title: 'Ruya Bank',
    body: 'ATM Cash withdrawal of AED 500.00 from Dubai Mall ATM on 07/10/2026.',
    expectedType: 'outgoing',
    explanation: 'Ruya cash withdrawal of AED 500.00 from ATM.',
    isPrimary: true,
  },
  {
    id: 'ruya-tim-hortons',
    bankName: 'Ruya',
    packageName: 'ae.ruya.digital',
    title: 'Ruya Alert',
    body: 'AED 38.00 spent at TIM HORTONS DUBAI using Apple Pay on card ending 4091.',
    expectedType: 'outgoing',
    explanation: 'Ruya Apple Pay purchase of AED 38.00 at Tim Hortons (Food & Dining).',
    isPrimary: true,
  },
  {
    id: 'ruya-dewa-utility',
    bankName: 'Ruya',
    packageName: 'ae.ruya.digital',
    title: 'Ruya',
    body: 'Direct debit payment of AED 430.00 to DEWA (Dubai Electricity & Water) completed.',
    expectedType: 'outgoing',
    explanation: 'Ruya utility direct debit of AED 430.00 to DEWA (Utilities).',
    isPrimary: true,
  },
  {
    id: 'ruya-vox-cinemas',
    bankName: 'Ruya',
    packageName: 'ae.ruya.digital',
    title: 'Ruya Bank',
    body: 'Purchase of AED 95.00 at VOX CINEMAS with your Ruya Card.',
    expectedType: 'outgoing',
    explanation: 'Ruya entertainment purchase of AED 95.00 at VOX Cinemas (Entertainment).',
    isPrimary: true,
  },
  {
    id: 'ruya-enoc-petrol',
    bankName: 'Ruya',
    packageName: 'ae.ruya.digital',
    title: 'Ruya',
    body: 'Card used for AED 120.00 at ENOC STATION 1042 DUBAI.',
    expectedType: 'outgoing',
    explanation: 'Ruya fuel expense of AED 120.00 at ENOC (Transport & Fuel).',
    isPrimary: true,
  },
  {
    id: 'ruya-talabat',
    bankName: 'Ruya',
    packageName: 'ae.ruya.digital',
    title: 'Ruya Alert',
    body: 'AED 64.50 debited for order at TALABAT on your Ruya card.',
    expectedType: 'outgoing',
    explanation: 'Ruya food delivery order of AED 64.50 at Talabat (Food & Dining).',
    isPrimary: true,
  },
  {
    id: 'ruya-bank-fee',
    bankName: 'Ruya',
    packageName: 'ae.ruya.digital',
    title: 'Ruya Bank',
    body: 'Monthly account service fee of AED 25.00 debited from your account.',
    expectedType: 'outgoing',
    explanation: 'Ruya bank fee of AED 25.00 debited.',
    isPrimary: true,
  },

  // --- SECONDARY UAE BANKS ---
  {
    id: 'enbd-lulu',
    bankName: 'Emirates NBD',
    packageName: 'com.emiratesnbd.enbd',
    title: 'Emirates NBD',
    body: 'Purchase of AED 215.00 at LULU HYPERMARKET with Debit Card ending 8765. Avail Bal: AED 8,920.',
    expectedType: 'outgoing',
    explanation: 'Emirates NBD supermarket purchase of AED 215.00 at Lulu Hypermarket.',
  },
  {
    id: 'adcb-starbucks',
    bankName: 'ADCB',
    packageName: 'com.adcb.mobilebanking',
    title: 'ADCB Alert',
    body: 'You have made a POS transaction of AED 34.00 at STARBUCKS with your ADCB card.',
    expectedType: 'outgoing',
    explanation: 'ADCB POS purchase of AED 34.00 at Starbucks.',
  },
  {
    id: 'fab-virgin',
    bankName: 'FAB',
    packageName: 'com.firstabudhabibank.fab',
    title: 'FAB Alert',
    body: 'Your card ending 3321 was used for AED 389.00 at VIRGIN MEGASTORE on 07/10/2026.',
    expectedType: 'outgoing',
    explanation: 'FAB retail shopping purchase of AED 389.00 at Virgin Megastore.',
  },
  {
    id: 'mashreq-noon',
    bankName: 'Mashreq',
    packageName: 'com.mashreq.neo',
    title: 'Mashreq Neo',
    body: 'Online purchase of AED 175.00 at NOON.COM on Debit Card ending 2210.',
    expectedType: 'outgoing',
    explanation: 'Mashreq online eCommerce purchase of AED 175.00 at Noon.com.',
  },
  {
    id: 'rakbank-ikea',
    bankName: 'RAKBANK',
    packageName: 'com.rakbank.digital',
    title: 'RAKBANK',
    body: 'Card ending 5543 used for AED 260.00 at IKEA DUBAI.',
    expectedType: 'outgoing',
    explanation: 'RAKBANK purchase of AED 260.00 at IKEA Dubai.',
  },
  {
    id: 'cbd-subway',
    bankName: 'CBD',
    packageName: 'com.cbd.mobile',
    title: 'CBD Alert',
    body: 'An amount of AED 42.00 has been debited at SUBWAY with your CBD Card.',
    expectedType: 'outgoing',
    explanation: 'CBD meal purchase of AED 42.00 at Subway.',
  },
  {
    id: 'adib-etisalat',
    bankName: 'ADIB',
    packageName: 'com.adib.mobile',
    title: 'ADIB Alert',
    body: 'Payment of AED 315.00 made to ETISALAT for mobile service.',
    expectedType: 'outgoing',
    explanation: 'ADIB telecom bill payment of AED 315.00 to Etisalat.',
  },
  {
    id: 'dib-centrepoint',
    bankName: 'Dubai Islamic Bank',
    packageName: 'com.dib.alislami',
    title: 'DIB Alert',
    body: 'DIB: Purchase of AED 180.00 at CENTREPOINT on your card ending 9941.',
    expectedType: 'outgoing',
    explanation: 'Dubai Islamic Bank purchase of AED 180.00 at Centrepoint.',
  },
  {
    id: 'hsbc-foreign-usd',
    bankName: 'HSBC UAE',
    packageName: 'net.hsbc.uae',
    title: 'HSBC UAE',
    body: 'Card ending 1234 spent USD 50.00 at GITHUB INC (approx AED 183.65).',
    expectedType: 'outgoing',
    explanation: 'HSBC UAE international charge in USD 50.00 (tracked in foreign totals).',
  },
  {
    id: 'sc-atm-cash',
    bankName: 'Standard Chartered UAE',
    packageName: 'com.sc.banc',
    title: 'StanChart',
    body: 'StanChart: ATM cash withdrawal of AED 1,000.00 from Dubai Festival City ATM.',
    expectedType: 'outgoing',
    explanation: 'Standard Chartered UAE cash withdrawal of AED 1,000.00.',
  },

  // --- REJECTION SCENARIOS (Must NOT be counted) ---
  {
    id: 'ruya-salary-rejected',
    bankName: 'Ruya',
    packageName: 'ae.ruya.digital',
    title: 'Ruya',
    body: 'Your account ...4091 was credited with AED 18,500.00 (Salary transfer).',
    expectedType: 'incoming',
    explanation: 'REJECTED: Salary credit / incoming funds (not an outgoing expense).',
    isPrimary: true,
  },
  {
    id: 'ruya-otp-rejected',
    bankName: 'Ruya',
    packageName: 'ae.ruya.digital',
    title: 'Ruya OTP',
    body: '849201 is your Ruya one-time password for card authentication. Valid for 5 minutes. Do not share.',
    expectedType: 'non-transaction',
    explanation: 'REJECTED: Authentication OTP security code.',
    isPrimary: true,
  },
  {
    id: 'ruya-declined-rejected',
    bankName: 'Ruya',
    packageName: 'ae.ruya.digital',
    title: 'Ruya Alert',
    body: 'Transaction of AED 450.00 at NOON was DECLINED due to insufficient funds.',
    expectedType: 'declined',
    explanation: 'REJECTED: Declined transaction (no funds deducted).',
    isPrimary: true,
  },
  {
    id: 'ruya-inward-transfer-rejected',
    bankName: 'Ruya',
    packageName: 'ae.ruya.digital',
    title: 'Ruya',
    body: 'Inward transfer of AED 650.00 received from Mansoor Ali into your account.',
    expectedType: 'incoming',
    explanation: 'REJECTED: Inward transfer received.',
    isPrimary: true,
  },
  {
    id: 'ruya-refund-rejected',
    bankName: 'Ruya',
    packageName: 'ae.ruya.digital',
    title: 'Ruya Bank',
    body: 'Refund of AED 75.00 from AMAZON.AE has been credited to your card.',
    expectedType: 'incoming',
    explanation: 'REJECTED: Merchant refund credited back.',
    isPrimary: true,
  },
  {
    id: 'enbd-cashback-rejected',
    bankName: 'Emirates NBD',
    packageName: 'com.emiratesnbd.enbd',
    title: 'Emirates NBD',
    body: 'Cashback of AED 45.00 has been credited to your card account.',
    expectedType: 'incoming',
    explanation: 'REJECTED: Cashback credit.',
  },
  {
    id: 'fab-balance-rejected',
    bankName: 'FAB',
    packageName: 'com.firstabudhabibank.fab',
    title: 'FAB Info',
    body: 'Your available account balance as of 07/10/2026 is AED 14,890.50.',
    expectedType: 'non-transaction',
    explanation: 'REJECTED: Balance information only (no transaction occurred).',
  },
];

export function getStoredRuyaConfig(): RuyaConfig {
  try {
    const raw = localStorage.getItem(STORAGE_KEY_RUYA_CONFIG);
    if (!raw) {
      const def: RuyaConfig = {
        senderAliases: DEFAULT_RUYA_SENDER_ALIASES,
        customOutgoingKeywords: [],
        enableFuzzyMatching: true,
      };
      localStorage.setItem(STORAGE_KEY_RUYA_CONFIG, JSON.stringify(def));
      return def;
    }
    return JSON.parse(raw);
  } catch {
    return {
      senderAliases: DEFAULT_RUYA_SENDER_ALIASES,
      customOutgoingKeywords: [],
      enableFuzzyMatching: true,
    };
  }
}

export function saveRuyaConfig(config: RuyaConfig): void {
  try {
    localStorage.setItem(STORAGE_KEY_RUYA_CONFIG, JSON.stringify(config));
  } catch (e) {
    console.error('Failed to save Ruya config', e);
  }
}

export function getStoredTransactions(): Transaction[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY_TRANSACTIONS);
    if (!raw) {
      // Seed initial realistic UAE transactions with Ruya as primary
      const seed: Transaction[] = [
        {
          id: Date.now() - 1000 * 60 * 35, // 35 mins ago
          amount: 142.50,
          currency: 'AED',
          merchant: 'Carrefour Deira',
          timestamp: Date.now() - 1000 * 60 * 35,
          bankSource: 'Ruya',
          rawMessage: 'Your Ruya card ending 4091 was used for AED 142.50 at CARREFOUR DEIRA on 07/10/2026.',
          category: 'Groceries',
          transactionType: 'pos',
          isIgnored: false,
        },
        {
          id: Date.now() - 1000 * 60 * 120, // 2 hours ago
          amount: 38.00,
          currency: 'AED',
          merchant: 'Tim Hortons Dubai',
          timestamp: Date.now() - 1000 * 60 * 120,
          bankSource: 'Ruya',
          rawMessage: 'Ruya Alert: AED 38.00 spent at TIM HORTONS DUBAI using Apple Pay on card ending 4091.',
          category: 'Food & Dining',
          transactionType: 'card_purchase',
          isIgnored: false,
        },
        {
          id: Date.now() - 1000 * 60 * 360, // 6 hours ago
          amount: 120.00,
          currency: 'AED',
          merchant: 'ENOC Station 1042',
          timestamp: Date.now() - 1000 * 60 * 360,
          bankSource: 'Ruya',
          rawMessage: 'Card used for AED 120.00 at ENOC STATION 1042 DUBAI.',
          category: 'Transport & Fuel',
          transactionType: 'card_purchase',
          isIgnored: false,
        },
        {
          id: Date.now() - 1000 * 60 * 60 * 28, // Yesterday
          amount: 315.00,
          currency: 'AED',
          merchant: 'Amazon.ae',
          timestamp: Date.now() - 1000 * 60 * 60 * 28,
          bankSource: 'Ruya',
          rawMessage: 'Online purchase of AED 315.00 at AMAZON.AE approved on your Ruya Debit Card.',
          category: 'Shopping',
          transactionType: 'online',
          isIgnored: false,
        },
        {
          id: Date.now() - 1000 * 60 * 60 * 48, // 2 days ago
          amount: 50.00,
          currency: 'USD',
          merchant: 'Github Inc',
          timestamp: Date.now() - 1000 * 60 * 60 * 48,
          bankSource: 'HSBC UAE',
          rawMessage: 'Card ending 1234 spent USD 50.00 at GITHUB INC (approx AED 183.65).',
          category: 'Utilities & Telecom',
          transactionType: 'online',
          isIgnored: false,
        },
      ];
      localStorage.setItem(STORAGE_KEY_TRANSACTIONS, JSON.stringify(seed));
      return seed;
    }
    return JSON.parse(raw);
  } catch {
    return [];
  }
}

export function saveTransactions(transactions: Transaction[]): void {
  try {
    localStorage.setItem(STORAGE_KEY_TRANSACTIONS, JSON.stringify(transactions));
  } catch (e) {
    console.error('Failed to save to local storage', e);
  }
}

// Duplicate detection: check if a transaction with same bank, amount, currency,
// and merchant or rawMessage occurred within a 10-minute window
export function isDuplicateTransaction(newTx: Transaction, existing: Transaction[]): boolean {
  const DUPLICATE_WINDOW_MS = 10 * 60 * 1000; // 10 minutes
  return existing.some((t) => {
    const isSameBank = t.bankSource.toLowerCase() === newTx.bankSource.toLowerCase();
    const isSameAmount = Math.abs(t.amount - newTx.amount) < 0.001;
    const isSameCurrency = t.currency === newTx.currency;
    const isWithinTime = Math.abs(t.timestamp - newTx.timestamp) < DUPLICATE_WINDOW_MS;
    const isSameMerchant = t.merchant.toLowerCase() === newTx.merchant.toLowerCase();
    const isSameRaw = t.rawMessage.trim().toLowerCase() === newTx.rawMessage.trim().toLowerCase();

    return isSameBank && isSameAmount && isSameCurrency && isWithinTime && (isSameMerchant || isSameRaw);
  });
}

export function insertTransaction(tx: Transaction): {
  transactions: Transaction[];
  isDuplicate: boolean;
} {
  const current = getStoredTransactions();
  if (isDuplicateTransaction(tx, current)) {
    return { transactions: current, isDuplicate: true };
  }
  const updated = [tx, ...current];
  saveTransactions(updated);
  return { transactions: updated, isDuplicate: false };
}

export function updateTransactionCategory(id: number, newCategory: string): Transaction[] {
  const current = getStoredTransactions();
  const updated = current.map((t) => {
    if (t.id === id) {
      return { ...t, category: newCategory, isManual: true };
    }
    return t;
  });
  saveTransactions(updated);
  return updated;
}

export function toggleIgnoreTransaction(
  id: number,
  ignoredReason: string = 'Manually ignored by user'
): Transaction[] {
  const current = getStoredTransactions();
  const updated = current.map((t) => {
    if (t.id === id) {
      const willIgnore = !t.isIgnored;
      return {
        ...t,
        isIgnored: willIgnore,
        ignoredReason: willIgnore ? ignoredReason : undefined,
      };
    }
    return t;
  });
  saveTransactions(updated);
  return updated;
}

export function deleteTransactionById(id: number): Transaction[] {
  const current = getStoredTransactions();
  const updated = current.filter((t) => t.id !== id);
  saveTransactions(updated);
  return updated;
}

export function clearAllTransactions(): Transaction[] {
  saveTransactions([]);
  return [];
}

// Local Debug Log Storage
export function getDebugLogs(): DebugNotificationLog[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY_DEBUG_LOGS);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

export function addDebugLog(log: DebugNotificationLog): DebugNotificationLog[] {
  const current = getDebugLogs();
  // Keep last 100 debug entries
  const updated = [log, ...current].slice(0, 100);
  try {
    localStorage.setItem(STORAGE_KEY_DEBUG_LOGS, JSON.stringify(updated));
  } catch (e) {
    console.error('Failed to save debug log', e);
  }
  return updated;
}

export function clearDebugLogs(): DebugNotificationLog[] {
  try {
    localStorage.removeItem(STORAGE_KEY_DEBUG_LOGS);
  } catch {}
  return [];
}

export function isNotificationAccessGranted(): boolean {
  return localStorage.getItem(STORAGE_KEY_PERMISSION) !== 'false';
}

export function setNotificationAccessGranted(granted: boolean): void {
  localStorage.setItem(STORAGE_KEY_PERMISSION, String(granted));
}

// Calculate AED Totals and strictly separate foreign currencies
export function calculateSpendTotals(transactions: Transaction[]): SpendTotals {
  const now = new Date();
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const dayOfWeek = now.getDay();
  const startOfWeek = new Date(now.getFullYear(), now.getMonth(), now.getDate() - dayOfWeek).getTime();
  const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1).getTime();

  let aedToday = 0;
  let aedThisWeek = 0;
  let aedThisMonth = 0;
  let aedAllTime = 0;
  let aedCount = 0;
  let ignoredCount = 0;

  const foreignTotals: { [currency: string]: { total: number; count: number } } = {};

  for (const t of transactions) {
    if (t.isIgnored) {
      ignoredCount++;
      continue; // Exclude manually ignored transactions from totals
    }

    const amt = t.amount;
    const curr = t.currency ? t.currency.toUpperCase() : 'AED';

    if (curr === 'AED') {
      aedCount++;
      aedAllTime += amt;
      if (t.timestamp >= startOfMonth) {
        aedThisMonth += amt;
      }
      if (t.timestamp >= startOfWeek) {
        aedThisWeek += amt;
      }
      if (t.timestamp >= startOfToday) {
        aedToday += amt;
      }
    } else {
      // Foreign currency
      if (!foreignTotals[curr]) {
        foreignTotals[curr] = { total: 0, count: 0 };
      }
      foreignTotals[curr].total += amt;
      foreignTotals[curr].count += 1;
    }
  }

  return {
    primaryCurrency: 'AED',
    aedToday,
    aedThisWeek,
    aedThisMonth,
    aedAllTime,
    aedCount,
    foreignTotals,
    ignoredCount,
  };
}
