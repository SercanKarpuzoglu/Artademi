import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createStudent, getStudent, updateStudent } from '../../api/students';
import type { StudentInput } from '../../api/types';

/** Tek ogrenci sorgusu (duzenleme formunu doldurmak icin). */
export function useStudent(id: number | undefined) {
  return useQuery({
    queryKey: ['student', id],
    queryFn: () => getStudent(id as number),
    enabled: id !== undefined,
  });
}

/** Yeni ogrenci; basarida liste tazelenir. */
export function useCreateStudent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: StudentInput) => createStudent(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['students'] });
    },
  });
}

/** Ogrenci guncelle; basarida liste ve ilgili kayit tazelenir. */
export function useUpdateStudent(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: StudentInput) => updateStudent(id, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['students'] });
      qc.invalidateQueries({ queryKey: ['student', id] });
    },
  });
}
