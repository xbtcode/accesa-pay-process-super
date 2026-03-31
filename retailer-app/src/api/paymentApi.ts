import axios from "axios";
import type { InitiateResponse, StatusResponse } from "../types";

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "",
  headers: {
    "Content-Type": "application/json",
    "X-API-Key": "sk_test_lidl_001",
  },
});

export async function initiatePayment(
  amount: number,
  description: string
): Promise<InitiateResponse> {
  const response = await client.post<InitiateResponse>(
    "/api/v1/transactions/initiate",
    { amount, currency: "EUR", description }
  );
  return response.data;
}

export async function getTransactionStatus(
  transactionId: string
): Promise<StatusResponse> {
  const response = await client.get<StatusResponse>(
    `/api/v1/transactions/${transactionId}/status`
  );
  return response.data;
}

export async function cancelTransaction(
  transactionId: string
): Promise<void> {
  await client.post(`/api/v1/transactions/${transactionId}/cancel`);
}
