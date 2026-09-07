import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  durumGuncelle,
  getBasvurular,
  getYeniBasvuruSayisi,
  ogrenciyeDonustur,
  type GetBasvurularParams,
} from '../../api/basvuru';
import { getTenant, updateBasvuruSlug } from '../../api/tenant';
import type { BasvuruDurumu, OgrenciyeDonusturInput } from '../../api/types';

export function useBasvurular(params: GetBasvurularParams) {
  return useQuery({
    queryKey: ['basvurular', params],
    queryFn: () => getBasvurular(params),
  });
}

export function useYeniBasvuruSayisi() {
  return useQuery({ queryKey: ['basvurular', 'yeni-sayisi'], queryFn: getYeniBasvuruSayisi });
}

export function useDurumGuncelle() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, durum }: { id: number; durum: BasvuruDurumu }) => durumGuncelle(id, durum),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['basvurular'] }),
  });
}

export function useOgrenciyeDonustur() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: OgrenciyeDonusturInput }) =>
      ogrenciyeDonustur(id, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['basvurular'] });
      // Yeni öğrenci oluştu; öğrenci listeleri de tazelensin.
      qc.invalidateQueries({ queryKey: ['students'] });
    },
  });
}

export function useTenant() {
  return useQuery({ queryKey: ['tenant'], queryFn: getTenant });
}

export function useBasvuruSlug() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (slug: string | null) => updateBasvuruSlug(slug),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['tenant'] }),
  });
}
