import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createEnrollment,
  getGroupEnrollments,
  leaveEnrollment,
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
export function useLeaveEnrollment(groupId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => leaveEnrollment(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['group', groupId, 'enrollments'] });
    },
  });
}
