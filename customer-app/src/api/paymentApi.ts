import axios from 'axios';
import type { ConfirmPaymentData, ConfirmResponse, PaymentDetails, PaymentResult } from '../types';

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
});

export async function getPaymentDetails(ref: string): Promise<PaymentDetails> {
  const response = await apiClient.get<PaymentDetails>(`/api/v1/payment/${ref}`);
  return response.data;
}

export async function confirmPayment(ref: string, data: ConfirmPaymentData): Promise<ConfirmResponse> {
  const response = await apiClient.post<ConfirmResponse>(`/api/v1/payment/${ref}/confirm`, data);
  return response.data;
}

export async function getPaymentResult(ref: string): Promise<PaymentResult> {
  const response = await apiClient.get<PaymentResult>(`/api/v1/payment/${ref}/result`);
  return response.data;
}
