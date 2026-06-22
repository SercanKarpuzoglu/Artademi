import { api } from './client';
import type { ApiResponse, StudentStatus } from './types';

/** Abonelik grace uyarısı (backend SubscriptionWarning). */
export interface SubscriptionWarning {
  inGrace: boolean;
  graceEndsAt: string;
  message: string;
}

/** Son kayıtlı öğrenci özeti (parasal alan yok). */
export interface OgrenciOzet {
  ad: string;
  soyad: string;
  statu: StudentStatus;
  kayitTarihi: string;
}

/** Bugünkü ders özeti. */
export interface DersOzet {
  grupAd: string;
  baslangic: string;
  bitis: string;
  salon: string | null;
}

/** Son ödeme özeti (yalnız ADMIN + ACCOUNTING). */
export interface OdemeOzet {
  ogrenciAd: string;
  tutar: number;
  tarih: string;
  yontem: 'NAKIT' | 'KART' | 'HAVALE';
}

export interface AdminDashboard {
  rol: 'ADMIN';
  sayilar: {
    aktifOgrenci: number;
    aktifGrup: number;
    buAyTahsilat: number;
    buAyGider: number;
    buAyNet: number;
    bekleyenBorcToplam: number;
  };
  trend6Ay: { donem: string; tahsilat: number; gider: number; net: number }[];
  sonOdemeler: OdemeOzet[];
  sonOgrenciler: OgrenciOzet[];
  bugunDersler: DersOzet[];
  subscriptionWarning: SubscriptionWarning | null;
}

export interface AccountingDashboard {
  rol: 'FRONTDESK_ACCOUNTING';
  sayilar: {
    aktifOgrenci: number;
    aktifGrup: number;
    buAyTahsilat: number;
    bekleyenBorcToplam: number;
  };
  trend6Ay: { donem: string; tahsilat: number }[];
  sonOdemeler: OdemeOzet[];
  sonOgrenciler: OgrenciOzet[];
  bugunDersler: DersOzet[];
}

export interface FrontdeskDashboard {
  rol: 'FRONTDESK';
  sayilar: { aktifOgrenci: number; aktifGrup: number };
  bugunDersler: DersOzet[];
  sonOgrenciler: OgrenciOzet[];
}

export interface TeacherDashboard {
  rol: 'TEACHER';
  kendiGruplar: { id: number; ad: string; tip: 'GRUP' | 'OZEL'; ogrenciSayisi: number }[];
  bugunDersler: DersOzet[];
  sonYoklamalar: { grupAd: string; tarih: string; gelenSayi: number; toplam: number }[];
}

/** Backend sealed DashboardData'nın TS aynası — `rol` üstünden daraltılır. */
export type DashboardData =
  | AdminDashboard
  | AccountingDashboard
  | FrontdeskDashboard
  | TeacherDashboard;

/** Rol bazlı panel özeti (içerik backend'de role göre filtrelenir; gelmeyen alan zaten yok). */
export async function getDashboard(): Promise<DashboardData> {
  const res = await api.get<ApiResponse<DashboardData>>('/api/dashboard');
  return res.data.data;
}
