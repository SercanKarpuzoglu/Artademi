import { api } from './client';
import type {
  ApiResponse,
  BillingSubscriptionResponse,
  CheckoutSession,
  CheckoutStartInput,
} from './types';

/**
 * Kendi tenant'ının abonelik özeti (SADECE ADMIN). /api/billing/** TenantStatus'tan muaftır:
 * ASKIDA kurumun admin'i bu sayfaya girip ödeme yapabilir.
 */
export async function getBillingSubscription(): Promise<BillingSubscriptionResponse> {
  const res = await api.get<ApiResponse<BillingSubscriptionResponse>>('/api/billing/subscription');
  return res.data.data;
}

/** iyzico hosted checkout başlatır; dönen formContent AbonelikPage'e gömülür. */
export async function startCheckout(payload: CheckoutStartInput): Promise<CheckoutSession> {
  const res = await api.post<ApiResponse<CheckoutSession>>('/api/billing/checkout', payload);
  return res.data.data;
}
