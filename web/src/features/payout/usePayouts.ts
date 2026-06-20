import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getPayouts, odePayout, type GetPayoutsParams } from '../../api/payouts';

/** Hakediş listesi sorgusu. Sayfa/filtre degisince onceki veriyi korur. */
export function usePayouts(params: GetPayoutsParams) {
  return useQuery({
    queryKey: ['payouts', params],
    queryFn: () => getPayouts(params),
    placeholderData: keepPreviousData,
  });
}

/** Hakedişi ödendi olarak isaretle; basarida liste tazelenir. */
export function useOdePayout() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => odePayout(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['payouts'] });
    },
  });
}
