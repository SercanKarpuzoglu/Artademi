import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createEnrollment,
  getGroupEnrollments,
  leaveEnrollment,
  planaGecir,
  transferEnrollment,
} from '../../api/enrollments';
import type { EnrollmentDurumu, EnrollmentInput } from '../../api/types';

/** Bir grubun kayıt listesi sorgusu (durum filtreli). */
export function useEnrollments(groupId: number | undefined, durum: EnrollmentDurumu | undefined) {
  return useQuery({
    queryKey: ['group', groupId, 'enrollments', { durum }],
    queryFn: () => getGroupEnrollments(groupId as number, { durum }),
    enabled: groupId !== undefined,
  });
}

/** Öğrenciyi gruba ekler; basarida ilgili grubun kayıt listesi tazelenir. */
export function useCreateEnrollment(groupId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: EnrollmentInput) => createEnrollment(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['group', groupId, 'enrollments'] });
    },
  });
}

/** Öğrenciyi gruptan çıkarır; basarida ilgili grubun kayıt listesi tazelenir. */
/** Deneme dersi kaydını plana geçirir; grup listesi tazelenir (öğrenci artık AKTİF). */
export function usePlanaGecir(groupId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, plan }: { id: number; plan: 'AYLIK' | 'DONEMLIK' }) => planaGecir(id, plan),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['group', groupId, 'enrollments'] });
      qc.invalidateQueries({ queryKey: ['students'] });
    },
  });
}

export function useLeaveEnrollment(groupId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => leaveEnrollment(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['group', groupId, 'enrollments'] });
    },
  });
}

/** Öğrenciyi başka GRUP'a transfer eder; basarida ilgili grubun kayıt listesi tazelenir. */
export function useTransferEnrollment(groupId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, yeniGrupId }: { id: number; yeniGrupId: number }) =>
      transferEnrollment(id, { yeniGrupId }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['group', groupId, 'enrollments'] });
    },
  });
}
