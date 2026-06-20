import { keepPreviousData, useQuery } from '@tanstack/react-query';
import {
  getFinancialSummary,
  getGroupOccupancy,
  getStudentBalances,
  getTeacherPayouts,
  type GetStudentBalancesParams,
} from '../../api/reports';

/** Finansal özet sorgusu — dönem girilince calisir. ADMIN. */
export function useFinancialSummary(donem: string) {
  return useQuery({
    queryKey: ['report', 'financial', donem],
    queryFn: () => getFinancialSummary(donem),
    enabled: Boolean(donem),
  });
}

/** Öğrenci borç listesi (sayfali); sayfa/filtre degisince onceki veriyi korur. */
export function useStudentBalances(params: GetStudentBalancesParams) {
  return useQuery({
    queryKey: ['report', 'studentBalances', params],
    queryFn: () => getStudentBalances(params),
    placeholderData: keepPreviousData,
  });
}

/** Öğretmen hakediş raporu — dönem girilince calisir. ADMIN. */
export function useTeacherPayouts(donem: string) {
  return useQuery({
    queryKey: ['report', 'teacherPayouts', donem],
    queryFn: () => getTeacherPayouts(donem),
    enabled: Boolean(donem),
  });
}

/** Grup doluluk raporu (aktifMi: undefined/true/false). */
export function useGroupOccupancy(aktifMi?: boolean) {
  return useQuery({
    queryKey: ['report', 'groupOccupancy', aktifMi ?? null],
    queryFn: () => getGroupOccupancy(aktifMi),
    placeholderData: keepPreviousData,
  });
}

/** Geçerli ayi YYYY-MM olarak dondurur (tarayici saatine gore). */
export function currentMonth(): string {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  return `${y}-${m}`;
}
