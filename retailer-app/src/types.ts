export interface InitiateResponse {
  transactionId: string;
  transactionRef: string;
  status: string;
  amount: number;
  currency: string;
  description: string;
  qrPayload: string;
  expiresAt: string;
  initiatedAt: string;
}

export interface StatusResponse {
  transactionId: string;
  transactionRef: string;
  status: string;
  amount: number;
  currency: string;
  completedAt?: string;
  bankReference?: string;
}
