import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createTeacher,
  getTeacher,
  getTeachers,
  setTeacherActive,
  updateTeacher,
  type GetTeachersParams,
} from '../../api/teachers';
import type { TeacherInput } from '../../api/types';

/** Öğretmen listesi sorgusu. Sayfa/filtre degisince onceki veriyi korur. */
export function useTeachers(params: GetTeachersParams) {
  return useQuery({
    queryKey: ['teachers', params],
    queryFn: () => getTeachers(params),
    placeholderData: keepPreviousData,
  });
}

/** Tek öğretmen sorgusu (duzenleme formu). */
export function useTeacher(id: number | undefined) {
  return useQuery({
    queryKey: ['teacher', id],
    queryFn: () => getTeacher(id as number),
    enabled: id !== undefined,
  });
}

/** Yeni öğretmen; basarida liste tazelenir. */
export function useCreateTeacher() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: TeacherInput) => createTeacher(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['teachers'] });
    },
  });
}

/** Öğretmen guncelle; basarida liste ve ilgili kayit tazelenir. */
export function useUpdateTeacher(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: TeacherInput) => updateTeacher(id, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['teachers'] });
      qc.invalidateQueries({ queryKey: ['teacher', id] });
    },
  });
}

/** Aktiflik degistir; basarida liste tazelenir. */
export function useSetTeacherActive() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, aktif }: { id: number; aktif: boolean }) => setTeacherActive(id, aktif),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['teachers'] });
    },
  });
}
