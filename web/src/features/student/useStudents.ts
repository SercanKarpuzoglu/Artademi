import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { getStudentListe, getStudents, type GetStudentsParams } from '../../api/students';

/**
 * Ogrenci listesi sorgusu. Sayfa/filtre degisince akici gecis icin onceki veriyi korur.
 * Query key konvansiyonu: ['students', params].
 */
export function useStudents(params: GetStudentsParams) {
  return useQuery({
    queryKey: ['students', params],
    queryFn: () => getStudents(params),
    placeholderData: keepPreviousData,
  });
}

/** Zengin öğrenci listesi (liste sayfası). Query key: ['student-liste', params]. */
export function useStudentListe(params: GetStudentsParams) {
  return useQuery({
    queryKey: ['student-liste', params],
    queryFn: () => getStudentListe(params),
    placeholderData: keepPreviousData,
  });
}
