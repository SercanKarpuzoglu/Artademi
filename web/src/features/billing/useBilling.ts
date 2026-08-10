import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { cancelSubscription, getBillingSubscription, startCheckout } from '../../api/billing';

/** Abonelik özeti sorgusu. Callback dönüşünde (?sonuc=) sayfa invalidate ile tazeler. */
export function useBillingSubscription() {
  return useQuery({
    queryKey: ['billing', 'subscription'],
    queryFn: getBillingSubscription,
  });
}

/** Checkout başlatma — başarılıysa iyzico form içeriği döner (sayfa embed eder). */
export function useStartCheckout() {
  return useMutation({ mutationFn: startCheckout });
}

/** Callback sonrası abonelik özetini tazelemek için. */
export function useInvalidateBilling() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: ['billing'] });
}

/** Abonelik iptali (dönem sonunda geçerli olur). */
export function useCancelSubscription() {
  return useMutation({ mutationFn: cancelSubscription });
}
