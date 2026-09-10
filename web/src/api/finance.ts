import { api } from './client';
import type { ApiResponse, BalanceResponse, FinanceSummaryResponse, GelirOzetiResponse } from './types';

/** Öğrenci bakiyesi (toplam tahakkuk / ödeme / bakiye). Tenant-guvenli; yok -> 404. */
export async function getStudentBalance(studentId: number): Promise<BalanceResponse> {
  const res = await api.get<ApiResponse<BalanceResponse>>(`/api/students/${studentId}/balance`);
  return res.data.data;
}

/** Öğrenci finans özeti (tahakkuklar + ödemeler + bakiye). Tenant-guvenli; yok -> 404. */
export async function getStudentFinance(studentId: number): Promise<FinanceSummaryResponse> {
  const res = await api.get<ApiResponse<FinanceSummaryResponse>>(`/api/students/${studentId}/finance`);
  return res.data.data;
}

/** Gelirler özeti: tarih aralığında öğrenci ödemeleri + ürün satışları (ADMIN + muhasebe). */
export async function getGelirOzeti(from: string, to: string): Promise<GelirOzetiResponse> {
  const res = await api.get<ApiResponse<GelirOzetiResponse>>('/api/finance/gelir-ozeti', {
    params: { from, to },
  });
  return res.data.data;
}
