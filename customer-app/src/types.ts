export interface PaymentDetails {
  transactionRef: string;
  merchantName: string;
  amount: number;
  currency: string;
  description: string;
  creditorName: string;
  status: string;
  expiresAt: string;
}

export interface PaymentResult {
  transactionRef: string;
  status: string;
  message: string;
  completedAt: string;
}

export interface ConfirmResponse {
  transactionRef: string;
  status: string;
  message: string;
}

export interface ConfirmPaymentData {
  debtorIban: string;
  debtorName?: string;
  debtorBic?: string;
  consentGiven: boolean;
}
