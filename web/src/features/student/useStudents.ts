import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { getStudents, type GetStudentsParams } from '../../api/students';

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
