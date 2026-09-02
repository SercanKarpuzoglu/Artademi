import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createSube,
  getSube,
  getSubeler,
  setSubeActive,
  updateSube,
  type GetSubelerParams,
} from '../../api/subeler';
import type { SubeInput } from '../../api/types';

/** Şube listesi sorgusu. Sayfa/filtre değişince önceki veriyi korur. */
export function useSubeler(params: GetSubelerParams) {
  return useQuery({
    queryKey: ['subeler', params],
    queryFn: () => getSubeler(params),
    placeholderData: keepPreviousData,
  });
}

/**
 * Salon/grup formlarındaki şube seçici için AKTİF şubeler.
 *
 * Pasif şube seçtirilmez: kapanmış bir şubeye yeni salon/grup bağlamak anlamsızdır.
 */
export function useAktifSubeler() {
  return useQuery({
    queryKey: ['subeler', 'aktif-secim'],
    queryFn: () => getSubeler({ aktif: true, size: 200 }),
    staleTime: 5 * 60 * 1000,
  });
}

/** Tek şube sorgusu (düzenleme formu). */
export function useSube(id: number | undefined) {
  return useQuery({
    queryKey: ['sube', id],
    queryFn: () => getSube(id as number),
    enabled: id !== undefined,
  });
}

/** Yeni şube; başarıda liste tazelenir. */
export function useCreateSube() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: SubeInput) => createSube(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['subeler'] });
    },
  });
}

/** Şube güncelle; başarıda liste ve ilgili kayıt tazelenir. */
export function useUpdateSube(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: SubeInput) => updateSube(id, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['subeler'] });
      qc.invalidateQueries({ queryKey: ['sube', id] });
    },
  });
}

/**
 * Aktiflik değiştir. Salon ve gruplar da tazelenir: pasifleşen şube onların
 * listelerinde de farklı görünebilir.
 */
export function useSetSubeActive() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, aktif }: { id: number; aktif: boolean }) => setSubeActive(id, aktif),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['subeler'] });
      qc.invalidateQueries({ queryKey: ['rooms'] });
      qc.invalidateQueries({ queryKey: ['groups'] });
    },
  });
}
