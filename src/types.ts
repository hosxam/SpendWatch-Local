export type TransactionType =
  | 'card_purchase'
  | 'pos'
  | 'online'
  | 'atm'
  | 'transfer_sent'
  | 'direct_debit'
  | 'fee'
  | 'other_expense';

export interface Transaction {
  id: number;
  amount: number;
  currency: string; // 'AED', 'USD', 'EUR', etc.
  merchant: string;
  timestamp: number;
  bankSource: string;
  rawMessage: string;
  category: string;
  transactionType: TransactionType;
  isIgnored?: boolean;
  ignoredReason?: string;
  isManual?: boolean;
}

export interface SpendTotals {
  primaryCurrency: 'AED';
  aedToday: number;
  aedThisWeek: number;
  aedThisMonth: number;
  aedAllTime: number;
  aedCount: number;
  // Foreign currencies kept strictly separate from AED totals
  foreignTotals: {
    [currency: string]: {
      total: number;
      count: number;
    };
  };
  ignoredCount: number;
}

export interface DebugNotificationLog {
  id: string;
  timestamp: number;
  rawText: string;
  packageName: string;
  senderOrTitle: string;
  detectedBank: string;
  isAccepted: boolean;
  parsedAmount?: number;
  parsedCurrency?: string;
  merchant?: string;
  transactionType?: TransactionType;
  reason?: string;
}

export interface BankNotificationSample {
  id: string;
  bankName: string;
  packageName: string;
  title: string;
  body: string;
  expectedType: 'outgoing' | 'incoming' | 'non-transaction' | 'declined';
  explanation: string;
  isPrimary?: boolean; // For Ruya
}

export interface RuyaConfig {
  senderAliases: string[];
  customOutgoingKeywords: string[];
  enableFuzzyMatching: boolean;
}
