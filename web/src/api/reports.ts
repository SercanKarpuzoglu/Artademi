import { api } from './client';
import type {
  ApiResponse,
  FinancialSummaryResponse,
  GroupOccupancyRow,
  StudentBalanceRow,
  TeacherPayoutsResponse,
} from './types';

/** Finansal özet (gelir/gider/net) — tekil yanit. Tenant JWT'den. YALNIZCA ADMIN. */
export async function getFinancialSummary(donem: string): Promise<FinancialSummaryResponse> {
  const res = await api.get<ApiResponse<FinancialSummaryResponse>>(
    '/api/reports/financial-summary',
    { params: { donem } },
  );
  return res.data.data;
}

export interface GetStudentBalancesParams {
  page?: number;
  size?: number;
  sadeceBorclu?: boolean;
}

/**
 * Öğrenci borç listesi (sayfali). Zarfin tamamini dondurur (data + meta).
 * ADMIN + FRONTDESK_ACCOUNTING.
 */
export async function getStudentBalances(
  params: GetStudentBalancesParams = {},
): Promise<ApiResponse<StudentBalanceRow[]>> {
  const res = await api.get<ApiResponse<StudentBalanceRow[]>>('/api/reports/student-balances', {
    params,
  });
  return res.data;
}

/** Öğretmen hakediş raporu (toplam + kalemler) — tekil yanit. YALNIZCA ADMIN. */
export async function getTeacherPayouts(donem: string): Promise<TeacherPayoutsResponse> {
  const res = await api.get<ApiResponse<TeacherPayoutsResponse>>('/api/reports/teacher-payouts', {
    params: { donem },
  });
  return res.data.data;
}

/**
 * Grup doluluk raporu — backend bir List doner (meta null olabilir), zarfin tamami dondurulur.
 * ADMIN + FRONTDESK + FRONTDESK_ACCOUNTING.
 */
export async function getGroupOccupancy(
  aktifMi?: boolean,
): Promise<ApiResponse<GroupOccupancyRow[]>> {
  const res = await api.get<ApiResponse<GroupOccupancyRow[]>>('/api/reports/group-occupancy', {
    params: aktifMi === undefined ? {} : { aktifMi },
  });
  return res.data;
}

/** Devamsızlık raporu satırı — backend AttendanceReportRow. */
export interface AttendanceReportRow {
  ogrenciId: number;
  ogrenciAdSoyad: string;
  toplamDers: number;
  geldi: number;
  gelmedi: number;
  izinli: number;
  katilimOrani: string | number;
}

/** Devamsızlık raporu — backend AttendanceReportResponse. Satırlar katılım oranı ARTAN sırada. */
export interface AttendanceReportResponse {
  baslangic: string;
  bitis: string;
  toplamOturum: number;
  satirlar: AttendanceReportRow[];
}

/** Tarih aralığında öğrenci bazlı katılım özeti. grupId verilirse tek gruba daralır. */
export async function getAttendanceReport(params: {
  baslangic: string;
  bitis: string;
  grupId?: number;
}): Promise<AttendanceReportResponse> {
  const res = await api.get<ApiResponse<AttendanceReportResponse>>('/api/reports/attendance', {
    params,
  });
  return res.data.data;
}

/** Aynı raporun CSV hali; tarayıcıda indirilir (Excel uyumlu). */
export async function indirDevamsizlikCsv(params: {
  baslangic: string;
  bitis: string;
  grupId?: number;
}): Promise<{ blob: Blob; dosyaAdi: string }> {
  const res = await api.get('/api/reports/attendance.csv', { params, responseType: 'blob' });
  const disposition = String(res.headers['content-disposition'] ?? '');
  const eslesme = disposition.match(/filename="?([^"]+)"?/);
  return { blob: res.data as Blob, dosyaAdi: eslesme?.[1] ?? 'devamsizlik.csv' };
}
