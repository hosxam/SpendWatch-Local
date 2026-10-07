import { Transaction, TransactionType, RuyaConfig } from './types';

// Default Ruya sender aliases & keywords (configurable)
export const DEFAULT_RUYA_SENDER_ALIASES = [
  'ruya',
  'ruyabank',
  'ruya-bank',
  'ruyadubai',
  'ruya digital',
  'ruya islamic',
  'ae.ruya',
  'ae.ruyabank',
  'com.ruya',
  'ruya app',
];

// Rejection keywords - Do not count incoming, declined, OTP, reversals, refunds, or balance
export const REJECTION_KEYWORDS = [
  // Declined & Failed
  'declined',
  'declined.',
  'declined:',
  'failed',
  'unsuccessful',
  'cancelled',
  'canceled',
  'not authorized',
  'insufficient funds',
  'transaction rejected',
  'could not be processed',

  // Incoming funds & Salary
  'credited',
  'credit of',
  'salary',
  'payroll',
  'deposited',
  'deposit of',
  'inward transfer',
  'transfer received',
  'received from',
  'funds received',

  // Refunds & Reversals & Cashback
  'refund',
  'refunded',
  'reversed',
  'reversal',
  'cashback',
  'cash back',
  'reward points',

  // Security & Authentication
  'otp',
  'one-time password',
  'one time password',
  'verification code',
  'security code',
  'login alert',
  'logged in',
  'password reset',
  'passcode',
  'activate your card',

  // Balance only
  'statement ready',
  'e-statement',
  'monthly statement',
];

// Outgoing spending indicator keywords
export const OUTGOING_KEYWORDS = [
  'spent',
  'purchase',
  'purchased',
  'pos',
  'point of sale',
  'debit',
  'debited',
  'charged',
  'paid',
  'payment of',
  'payment made',
  'withdrawn',
  'withdrawal',
  'outward transfer',
  'transfer sent',
  'transferred to',
  'sent to',
  'direct debit',
  'card used',
  'card ending',
  'fee debited',
  'service fee',
  'vat charged',
  'online purchase',
  'apple pay',
  'samsung pay',
  'google pay',
];

// UAE Banks definitions (with Ruya prioritized as primary)
export interface BankDefinition {
  id: string;
  name: string;
  isPrimary: boolean;
  senderMatches: string[];
  packageMatches: string[];
}

export const UAE_BANKS: BankDefinition[] = [
  {
    id: 'ruya',
    name: 'Ruya',
    isPrimary: true,
    senderMatches: DEFAULT_RUYA_SENDER_ALIASES,
    packageMatches: ['ruya', 'ruyabank', 'ae.ruya'],
  },
  {
    id: 'enbd',
    name: 'Emirates NBD',
    isPrimary: false,
    senderMatches: ['enbd', 'emiratesnbd', 'emirates nbd', 'liv'],
    packageMatches: ['emiratesnbd', 'enbd', 'liv.bank'],
  },
  {
    id: 'adcb',
    name: 'ADCB',
    isPrimary: false,
    senderMatches: ['adcb', 'hayyak'],
    packageMatches: ['adcb', 'hayyak'],
  },
  {
    id: 'fab',
    name: 'FAB',
    isPrimary: false,
    senderMatches: ['fab', 'firstabudhabi', 'first abu dhabi bank'],
    packageMatches: ['fab', 'firstabudhabibank'],
  },
  {
    id: 'mashreq',
    name: 'Mashreq',
    isPrimary: false,
    senderMatches: ['mashreq', 'neo', 'mashreqneo'],
    packageMatches: ['mashreq'],
  },
  {
    id: 'rakbank',
    name: 'RAKBANK',
    isPrimary: false,
    senderMatches: ['rakbank', 'rak bank'],
    packageMatches: ['rakbank'],
  },
  {
    id: 'cbd',
    name: 'CBD',
    isPrimary: false,
    senderMatches: ['cbd', 'commercial bank of dubai'],
    packageMatches: ['cbd'],
  },
  {
    id: 'adib',
    name: 'ADIB',
    isPrimary: false,
    senderMatches: ['adib', 'abu dhabi islamic bank'],
    packageMatches: ['adib'],
  },
  {
    id: 'dib',
    name: 'Dubai Islamic Bank',
    isPrimary: false,
    senderMatches: ['dib', 'dubai islamic bank', 'al islami'],
    packageMatches: ['dib', 'alislami'],
  },
  {
    id: 'hsbc_uae',
    name: 'HSBC UAE',
    isPrimary: false,
    senderMatches: ['hsbc uae', 'hsbc'],
    packageMatches: ['hsbc'],
  },
  {
    id: 'sc_uae',
    name: 'Standard Chartered UAE',
    isPrimary: false,
    senderMatches: ['stanchart', 'standard chartered', 'scb'],
    packageMatches: ['sc.banc'],
  },
];

// Regex for extracting UAE Dirham (AED, Dhs, DH) and Foreign Currencies
// Example matches: "AED 125.00", "125.00 AED", "Dhs 45.50", "USD 50.00", "EUR 30.00"
const REGEX_CURRENCY_FIRST = /(?:^|\s)(AED|DHS|DH|DIRHAMS?|USD|EUR|GBP|SAR|QAR|KWD|BHD|OMR|INR|CAD|AUD|CHF|JPY)\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)/i;
const REGEX_AMOUNT_FIRST = /(?:^|\s)([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\s*(AED|DHS|DH|DIRHAMS?|USD|EUR|GBP|SAR|QAR|KWD|BHD|OMR|INR|CAD|AUD|CHF|JPY)(?:\s|[,\.]|$)/i;

// Regex for extracting merchant / recipient
const REGEX_MERCHANT = /(?:at|@|to|towards|for|merchant|payee)\s+([A-Za-z0-9&'._\-\s\/]{2,35}?)(?:\s+(?:on|with|using|via|ending|ref|avail|bal|available|card|account|approx|on card|from|dt|date|AED|\.|$)|[\n\.]|$)/i;

export function detectBank(
  senderOrTitle: string,
  packageName: string = '',
  ruyaConfig?: RuyaConfig
): { bankName: string; isRuya: boolean; bankId: string } {
  const text = `${senderOrTitle} ${packageName}`.toLowerCase();

  // 1. Check Ruya first (highest priority)
  const ruyaAliases = ruyaConfig?.senderAliases || DEFAULT_RUYA_SENDER_ALIASES;
  for (const alias of ruyaAliases) {
    if (text.includes(alias.toLowerCase())) {
      return { bankName: 'Ruya', isRuya: true, bankId: 'ruya' };
    }
  }

  // 2. Check Secondary UAE Banks
  for (const bank of UAE_BANKS) {
    if (bank.id === 'ruya') continue;
    for (const sender of bank.senderMatches) {
      if (text.includes(sender.toLowerCase())) {
        return { bankName: bank.name, isRuya: false, bankId: bank.id };
      }
    }
    for (const pkg of bank.packageMatches) {
      if (text.includes(pkg.toLowerCase())) {
        return { bankName: bank.name, isRuya: false, bankId: bank.id };
      }
    }
  }

  // Fallback: If title has a bank name
  if (senderOrTitle && senderOrTitle.trim()) {
    return { bankName: senderOrTitle.trim(), isRuya: false, bankId: 'other' };
  }

  return { bankName: 'UAE Bank Alert', isRuya: false, bankId: 'other' };
}

export function detectTransactionType(text: string): TransactionType {
  const lower = text.toLowerCase();
  if (lower.includes('atm') || lower.includes('cash withdrawal') || lower.includes('withdrawn')) {
    return 'atm';
  }
  if (
    lower.includes('online') ||
    lower.includes('e-commerce') ||
    lower.includes('amazon') ||
    lower.includes('noon') ||
    lower.includes('internet')
  ) {
    return 'online';
  }
  if (lower.includes('pos') || lower.includes('point of sale')) {
    return 'pos';
  }
  if (
    lower.includes('outward transfer') ||
    lower.includes('transfer sent') ||
    lower.includes('transferred to') ||
    lower.includes('sent to') ||
    lower.includes('remittance')
  ) {
    return 'transfer_sent';
  }
  if (lower.includes('direct debit') || lower.includes('standing order') || lower.includes('auto debit')) {
    return 'direct_debit';
  }
  if (lower.includes('fee') || lower.includes('charge') || lower.includes('vat')) {
    return 'fee';
  }
  if (lower.includes('card') || lower.includes('purchase') || lower.includes('apple pay')) {
    return 'card_purchase';
  }
  return 'other_expense';
}

export function categorizeUaeMerchant(merchant: string, text: string): string {
  const combined = `${merchant} ${text}`.toLowerCase();

  // Supermarkets & Groceries in UAE
  if (
    combined.includes('carrefour') ||
    combined.includes('spinneys') ||
    combined.includes('waitrose') ||
    combined.includes('lulu') ||
    combined.includes('choithrams') ||
    combined.includes('al maya') ||
    combined.includes('viva') ||
    combined.includes('union coop') ||
    combined.includes('west zone') ||
    combined.includes('grocer') ||
    combined.includes('supermarket') ||
    combined.includes('hypermarket') ||
    combined.includes('grandiose')
  ) {
    return 'Groceries';
  }

  // Dining & Cafes in UAE
  if (
    combined.includes('starbucks') ||
    combined.includes('costa') ||
    combined.includes('tim hortons') ||
    combined.includes('cafe') ||
    combined.includes('coffee') ||
    combined.includes('mcdonald') ||
    combined.includes('kfc') ||
    combined.includes('subway') ||
    combined.includes('talabat') ||
    combined.includes('deliveroo') ||
    combined.includes('noon food') ||
    combined.includes('restaurant') ||
    combined.includes('shawarma') ||
    combined.includes('burger') ||
    combined.includes('pizza') ||
    combined.includes('karak')
  ) {
    return 'Food & Dining';
  }

  // Transport & Fuel in UAE
  if (
    combined.includes('enoc') ||
    combined.includes('eppco') ||
    combined.includes('adnoc') ||
    combined.includes('emarat') ||
    combined.includes('fuel') ||
    combined.includes('gas station') ||
    combined.includes('petrol') ||
    combined.includes('salik') ||
    combined.includes('rta') ||
    combined.includes('careem') ||
    combined.includes('uber') ||
    combined.includes('taxi') ||
    combined.includes('metro') ||
    combined.includes('parking')
  ) {
    return 'Transport & Fuel';
  }

  // Utilities & Telecom in UAE
  if (
    combined.includes('dewa') ||
    combined.includes('addc') ||
    combined.includes('sewa') ||
    combined.includes('etisalat') ||
    combined.includes('e&') ||
    combined.includes('du') ||
    combined.includes('virgin mobile') ||
    combined.includes('utility') ||
    combined.includes('telecom') ||
    combined.includes('electricity') ||
    combined.includes('water')
  ) {
    return 'Utilities & Telecom';
  }

  // Shopping & Retail in UAE
  if (
    combined.includes('amazon') ||
    combined.includes('noon') ||
    combined.includes('sharaf dg') ||
    combined.includes('virgin megastore') ||
    combined.includes('ikea') ||
    combined.includes('zara') ||
    combined.includes('h&m') ||
    combined.includes('centrepoint') ||
    combined.includes('namshi') ||
    combined.includes('shein') ||
    combined.includes('apple') ||
    combined.includes('mall') ||
    combined.includes('store')
  ) {
    return 'Shopping';
  }

  // Entertainment & Leisure
  if (
    combined.includes('vox') ||
    combined.includes('reel cinemas') ||
    combined.includes('cinema') ||
    combined.includes('netflix') ||
    combined.includes('spotify') ||
    combined.includes('ticket') ||
    combined.includes('expo') ||
    combined.includes('theme park') ||
    combined.includes('atlantis')
  ) {
    return 'Entertainment';
  }

  // Health & Pharmacy
  if (
    combined.includes('aster') ||
    combined.includes('boots') ||
    combined.includes('life pharmacy') ||
    combined.includes('bin sina') ||
    combined.includes('pharmacy') ||
    combined.includes('clinic') ||
    combined.includes('hospital') ||
    combined.includes('doctor')
  ) {
    return 'Health & Pharmacy';
  }

  // ATM / Cash
  if (combined.includes('atm') || combined.includes('cash withdrawal')) {
    return 'Cash / ATM';
  }

  // Bank Fees
  if (combined.includes('fee') || combined.includes('vat') || combined.includes('charge')) {
    return 'Bank Fees & Charges';
  }

  return 'General';
}

export function parseUaeBankNotification(
  title: string,
  body: string,
  packageName: string = '',
  postTime: number = Date.now(),
  ruyaConfig?: RuyaConfig
): {
  transaction: Transaction | null;
  detectedBank: string;
  isAccepted: boolean;
  reason?: string;
  transactionType?: TransactionType;
} {
  const combined = `${title ? title + ' ' : ''}${body || ''}`.trim();
  if (!combined) {
    return {
      transaction: null,
      detectedBank: 'Unknown',
      isAccepted: false,
      reason: 'Empty notification content',
    };
  }

  // 1. Detect Bank (Prioritizing Ruya)
  const bankInfo = detectBank(title, packageName, ruyaConfig);

  // Also check if text mentions Ruya explicitly inside the body
  let effectiveBankName = bankInfo.bankName;
  if (!bankInfo.isRuya && combined.toLowerCase().includes('ruya')) {
    effectiveBankName = 'Ruya';
  }

  const lower = combined.toLowerCase();

  // 2. Strict Rejection Checks:
  // - Declined / Failed
  if (
    lower.includes('declined') ||
    lower.includes('failed') ||
    lower.includes('unsuccessful') ||
    lower.includes('not authorized') ||
    lower.includes('insufficient funds') ||
    lower.includes('transaction rejected')
  ) {
    return {
      transaction: null,
      detectedBank: effectiveBankName,
      isAccepted: false,
      reason: 'Declined or failed transaction (not charged)',
    };
  }

  // - OTP / Passcode / Verification
  if (
    lower.includes('otp') ||
    lower.includes('one-time password') ||
    lower.includes('one time password') ||
    lower.includes('verification code') ||
    lower.includes('security code') ||
    lower.includes('passcode') ||
    lower.includes('login alert')
  ) {
    return {
      transaction: null,
      detectedBank: effectiveBankName,
      isAccepted: false,
      reason: 'OTP or authentication security message',
    };
  }

  // - Salary / Inward Transfer / Deposit (Incoming funds)
  if (
    lower.includes('salary') ||
    lower.includes('payroll') ||
    lower.includes('inward transfer') ||
    lower.includes('transfer received') ||
    lower.includes('funds received') ||
    (lower.includes('credited') && !lower.includes('debited') && !lower.includes('spent') && !lower.includes('purchase')) ||
    (lower.includes('deposited') && !lower.includes('debited'))
  ) {
    return {
      transaction: null,
      detectedBank: effectiveBankName,
      isAccepted: false,
      reason: 'Incoming transfer, salary, or credit (not an outgoing expense)',
    };
  }

  // - Refund / Reversal / Cashback
  if (
    lower.includes('refund') ||
    lower.includes('reversed') ||
    lower.includes('reversal') ||
    lower.includes('cashback') ||
    lower.includes('cash back')
  ) {
    return {
      transaction: null,
      detectedBank: effectiveBankName,
      isAccepted: false,
      reason: 'Refund, reversal, or cashback credit (not an outgoing expense)',
    };
  }

  // - Balance-only message (without expense keywords)
  if (
    (lower.includes('balance is') || lower.includes('available balance:') || lower.includes('statement')) &&
    !OUTGOING_KEYWORDS.some((kw) => lower.includes(kw))
  ) {
    return {
      transaction: null,
      detectedBank: effectiveBankName,
      isAccepted: false,
      reason: 'Balance inquiry or statement notification only',
    };
  }

  // 3. Must match an outgoing transaction indicator
  const hasOutgoingIndicator = OUTGOING_KEYWORDS.some((kw) => lower.includes(kw));
  if (!hasOutgoingIndicator) {
    return {
      transaction: null,
      detectedBank: effectiveBankName,
      isAccepted: false,
      reason: 'No outgoing expense keywords found',
    };
  }

  // 4. Extract Amount & Currency
  let amount = 0;
  let currency = 'AED'; // Default primary currency for UAE

  const mCurrFirst = REGEX_CURRENCY_FIRST.exec(combined);
  if (mCurrFirst) {
    currency = normalizeUaeCurrency(mCurrFirst[1]);
    amount = parseAmount(mCurrFirst[2]);
  } else {
    const mAmtFirst = REGEX_AMOUNT_FIRST.exec(combined);
    if (mAmtFirst) {
      amount = parseAmount(mAmtFirst[1]);
      currency = normalizeUaeCurrency(mAmtFirst[2]);
    }
  }

  if (amount <= 0) {
    return {
      transaction: null,
      detectedBank: effectiveBankName,
      isAccepted: false,
      reason: 'Could not extract valid transaction amount',
    };
  }

  // 5. Extract Merchant / Recipient
  let merchant = 'Unknown Merchant';
  const mMerch = REGEX_MERCHANT.exec(combined);
  if (mMerch && mMerch[1]) {
    merchant = cleanMerchantString(mMerch[1]);
  } else {
    // If ATM transaction, name it ATM Cash
    if (lower.includes('atm') || lower.includes('cash withdrawal')) {
      merchant = 'ATM Cash Withdrawal';
    } else if (lower.includes('fee') || lower.includes('vat')) {
      merchant = `${effectiveBankName} Fee`;
    }
  }

  // 6. Transaction Type
  const transactionType = detectTransactionType(combined);

  // 7. Category
  const category = categorizeUaeMerchant(merchant, combined);

  const tx: Transaction = {
    id: postTime + Math.floor(Math.random() * 1000),
    amount,
    currency,
    merchant,
    timestamp: postTime,
    bankSource: effectiveBankName,
    rawMessage: combined,
    category,
    transactionType,
    isIgnored: false,
  };

  return {
    transaction: tx,
    detectedBank: effectiveBankName,
    isAccepted: true,
    transactionType,
  };
}

function normalizeUaeCurrency(raw: string): string {
  if (!raw) return 'AED';
  const clean = raw.trim().toUpperCase();
  if (clean === 'DHS' || clean === 'DH' || clean === 'DIRHAM' || clean === 'DIRHAMS') {
    return 'AED';
  }
  return clean;
}

function parseAmount(str: string): number {
  if (!str) return 0;
  try {
    const clean = str.replace(/,/g, '').trim();
    const val = parseFloat(clean);
    return isNaN(val) ? 0 : val;
  } catch {
    return 0;
  }
}

function cleanMerchantString(candidate: string): string {
  if (!candidate) return 'Unknown Merchant';
  let cleaned = candidate.trim().replace(/[,.;:]+$/, '');
  // Strip trailing card details if captured
  cleaned = cleaned.replace(/\s+(?:on|with|using|via|ending)\s+.*$/i, '');
  cleaned = cleaned.replace(/\s+card\s+.*$/i, '');
  if (cleaned.toLowerCase().startsWith('a ') || cleaned.toLowerCase().startsWith('an ')) {
    cleaned = cleaned.substring(cleaned.indexOf(' ') + 1).trim();
  }
  return cleaned || 'Unknown Merchant';
}
