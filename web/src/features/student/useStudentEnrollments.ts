import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createEnrollment, getStudentEnrollments, leaveEnrollment } from '../../api/enrollments';
import { planaGecir } from '../../api/enrollments';
import type { EnrollmentInput } from '../../api/types';

/** Öğrencinin tüm kayıtları (aktif + ayrılmış) — öğrenci detayındaki "Gruplar / Kayıtlar" bölümü. */
export function useStudentEnrollments(studentId: number | undefined) {
  return useQuery({
    queryKey: ['student-enrollments', studentId],
    queryFn: () => getStudentEnrollments(studentId as number, { size: 100 }),
    enabled: studentId !== undefined,
  });
}

/** Öğrenci sayfasından gruba ekleme; öğrenci kayıtları, grup kayıtları ve bakiye tazelenir. */
export function useEnrollStudent(studentId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: EnrollmentInput) => createEnrollment(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['student-enrollments', studentId] });
      qc.invalidateQueries({ queryKey: ['enrollments'] });
      qc.invalidateQueries({ queryKey: ['student-finance', studentId] });
    },
  });
}

/** Öğrenci sayfasından gruptan çıkarma (AYRILDI; kayıt silinmez). */
/** Deneme dersi kaydını plana geçirir; öğrenci (statü) ve kayıtları tazelenir. */
export function usePlanaGecirFromStudent(studentId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, plan }: { id: number; plan: 'AYLIK' | 'DONEMLIK' }) => planaGecir(id, plan),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['student-enrollments', studentId] });
      qc.invalidateQueries({ queryKey: ['student', studentId] });
      qc.invalidateQueries({ queryKey: ['paketler', studentId] });
      qc.invalidateQueries({ queryKey: ['students'] });
    },
  });
}

export function useLeaveFromStudent(studentId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (enrollmentId: number) => leaveEnrollment(enrollmentId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['student-enrollments', studentId] });
      qc.invalidateQueries({ queryKey: ['enrollments'] });
    },
  });
}
