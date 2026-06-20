// Backend api-contract zarfinin TS karsiligi. Backend DTO'lari degisince burasi guncellenir.

export interface ApiError {
  code: string;
  message: string;
  fields?: Record<string, string> | null;
}

export interface PageMeta {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  error: ApiError | null;
  meta: PageMeta | null;
}

export type StudentStatus = 'AKTIF' | 'PASIF' | 'DENEME' | 'DONDURULMUS';

/**
 * Olusturma/guncelleme istek govdesi — backend CreateStudentRequest/UpdateStudentRequest
 * ile birebir. status ve tenant_id GONDERILMEZ (yeni kayit DENEME; tenant JWT'den).
 * Opsiyonel alanlar bos ise gonderilmez (undefined) — backend @Pattern bos string'i reddeder.
 */
export interface StudentInput {
  ad: string;
  soyad: string;
  tcKimlikNo: string;
  dogumTarihi: string; // YYYY-MM-DD
  telefon?: string;
  yetiskinMi: boolean;
  anneAd?: string;
  anneTcKimlikNo?: string;
  anneTelefon?: string;
  babaAd?: string;
  babaTcKimlikNo?: string;
  babaTelefon?: string;
  veliMeslek?: string;
  evAdresi?: string;
  veliMail?: string;
}

/** Backend StudentResponse ile birebir aynalanir. Backend degisince burasi guncellenir. */
export interface StudentResponse {
  id: number;
  ad: string;
  soyad: string;
  tcKimlikNo: string;
  dogumTarihi: string; // ISO tarih (YYYY-MM-DD)
  telefon: string | null;
  yetiskinMi: boolean;
  status: StudentStatus;
  anneAd: string | null;
  anneTcKimlikNo: string | null;
  anneTelefon: string | null;
  babaAd: string | null;
  babaTcKimlikNo: string | null;
  babaTelefon: string | null;
  veliMeslek: string | null;
  evAdresi: string | null;
  veliMail: string | null;
  olusturulmaTarihi: string;
  guncellenmeTarihi: string;
}

// --- Tanımlar modülü (Branş / Salon / Öğretmen) — backend DTO'lari ile birebir aynalanir ---

/** Branş yaniti — backend BranchResponse. */
export interface BranchResponse {
  id: number;
  ad: string;
  aciklama: string | null;
  aktif: boolean;
  olusturulmaTarihi: string;
  guncellenmeTarihi: string;
}

/** Branş olusturma/guncelleme govdesi. Bos opsiyonel alanlar gonderilmez. */
export interface BranchInput {
  ad: string;
  aciklama?: string;
}

/** Salon yaniti — backend RoomResponse. */
export interface RoomResponse {
  id: number;
  ad: string;
  kapasite: number | null;
  aciklama: string | null;
  aktif: boolean;
  olusturulmaTarihi: string;
  guncellenmeTarihi: string;
}

/** Salon olusturma/guncelleme govdesi. */
export interface RoomInput {
  ad: string;
  kapasite?: number;
  aciklama?: string;
}

/** Öğretmen hakediş tipi. */
export type HakedisTipi = 'SAATLIK' | 'CIRO_ORANI';

/** Öğretmenin bağlı olduğu branş referansı (özet). */
export interface TeacherBranchRef {
  id: number;
  ad: string;
}

/** Öğretmen yaniti — backend TeacherResponse. Para alanlari number VEYA string gelebilir. */
export interface TeacherResponse {
  id: number;
  ad: string;
  soyad: string;
  telefon: string | null;
  email: string | null;
  keycloakUserId: string | null;
  hakedisTipi: HakedisTipi;
  saatlikUcret: string | number | null;
  ciroOrani: string | number | null;
  aktif: boolean;
  branslar: TeacherBranchRef[];
  olusturulmaTarihi: string;
  guncellenmeTarihi: string;
}

/**
 * Öğretmen olusturma/guncelleme govdesi. Para alanlari (saatlikUcret/ciroOrani) BigDecimal
 * hassasiyetini korumak icin STRING gonderilir; hakediş tipine gore yalnizca ilgili alan eklenir.
 */
export interface TeacherInput {
  ad: string;
  soyad: string;
  telefon?: string;
  email?: string;
  keycloakUserId?: string;
  hakedisTipi: HakedisTipi;
  saatlikUcret?: string;
  ciroOrani?: string;
  bransIds: number[];
}

// --- Grup / Kayıt modülü — backend DTO'lari ile birebir aynalanir ---

/** Grup tipi: standart grup veya birebir özel ders. */
export type GrupTipi = 'GRUP' | 'OZEL';

/** Grup yanitindaki branş/salon referansi (özet). */
export interface GroupRef {
  id: number;
  ad: string;
}

/** Grup yanitindaki öğretmen referansi (özet). */
export interface GroupTeacherRef {
  id: number;
  ad: string;
  soyad: string;
}

/** Grup yaniti — backend GroupResponse. Para alanlari number VEYA string gelebilir. */
export interface GroupResponse {
  id: number;
  ad: string;
  tip: GrupTipi;
  brans: GroupRef | null;
  ogretmen: GroupTeacherRef | null;
  salon: GroupRef | null;
  seviye: string | null;
  aylikAidat: string | number | null;
  dersBasiUcret: string | number | null;
  aktif: boolean;
  olusturulmaTarihi: string;
  guncellenmeTarihi: string;
}

/**
 * Grup olusturma/guncelleme govdesi. Para alanlari (aylikAidat/dersBasiUcret) BigDecimal
 * hassasiyetini korumak icin STRING gonderilir; tipe gore yalnizca ilgili para alani eklenir.
 */
export interface GroupInput {
  ad: string;
  tip: GrupTipi;
  bransId: number;
  ogretmenId: number;
  salonId?: number;
  seviye?: string;
  aylikAidat?: string;
  dersBasiUcret?: string;
}

/** Kayıt durumu: grupta aktif ya da ayrılmış. */
export type EnrollmentDurumu = 'AKTIF' | 'AYRILDI';

/** Kayıt yanitindaki öğrenci referansi (özet). */
export interface EnrollmentStudentRef {
  id: number;
  ad: string;
  soyad: string;
}

/** Kayıt yanitindaki grup referansi (özet). */
export interface EnrollmentGroupRef {
  id: number;
  ad: string;
  tip: GrupTipi;
}

/** Kayıt yaniti — backend EnrollmentResponse. */
export interface EnrollmentResponse {
  id: number;
  durum: EnrollmentDurumu;
  kayitTarihi: string;
  ayrilmaTarihi: string | null;
  ogrenci: EnrollmentStudentRef;
  grup: EnrollmentGroupRef;
}

/** Kayıt olusturma govdesi. kayitTarihi gonderilmezse backend bugunu kullanir. */
export interface EnrollmentInput {
  ogrenciId: number;
  grupId: number;
  kayitTarihi?: string;
}

// --- Program (Schedule) modülü — backend DTO'lari ile birebir aynalanir ---

/** Haftanin gunu (backend enum ordinal sirasi — siralama/etiket icin bu sira korunur). */
export type HaftaGunu =
  | 'PAZARTESI'
  | 'SALI'
  | 'CARSAMBA'
  | 'PERSEMBE'
  | 'CUMA'
  | 'CUMARTESI'
  | 'PAZAR';

/** Program yanitindaki grup referansi (özet). */
export interface ScheduleGroupRef {
  id: number;
  ad: string;
  tip: GrupTipi;
}

/** Program yaniti — backend ScheduleResponse. Saatler "HH:mm:ss" (LocalTime) string. */
export interface ScheduleResponse {
  id: number;
  gun: HaftaGunu;
  baslangicSaati: string; // "HH:mm:ss"
  bitisSaati: string; // "HH:mm:ss"
  aktif: boolean;
  grup: ScheduleGroupRef | null;
  olusturulmaTarihi?: string;
  guncellenmeTarihi?: string;
}

/** Program olusturma/guncelleme govdesi. Saatler "HH:mm". */
export interface ScheduleInput {
  grupId: number;
  gun: HaftaGunu;
  baslangicSaati: string; // "HH:mm"
  bitisSaati: string; // "HH:mm"
}

// --- Yoklama (Attendance) modülü — backend DTO'lari ile birebir aynalanir ---

/** Yoklama durumu. */
export type YoklamaDurumu = 'GELDI' | 'GELMEDI' | 'IZINLI';

/** Yoklama kaydindaki öğrenci referansi (özet). */
export interface AttendanceStudentRef {
  id: number;
  ad: string;
  soyad: string;
}

/** Yoklama oturumundaki tekil giris (öğrenci + durum). */
export interface AttendanceEntryView {
  ogrenci: AttendanceStudentRef;
  durum: YoklamaDurumu;
}

/** Yoklama oturumundaki grup referansi (özet). */
export interface AttendanceGroupRef {
  id: number;
  ad: string;
  tip: GrupTipi;
}

/** Yoklama oturumu yaniti — backend SessionResponse. */
export interface SessionResponse {
  id: number;
  tarih: string; // YYYY-MM-DD
  notu: string | null;
  grup: AttendanceGroupRef | null;
  entries: AttendanceEntryView[];
}

/** Yoklama oturumu olusturma govdesi. Backend aktif kayitlar icin GELMEDI girisleri uretir. */
export interface CreateSessionInput {
  grupId: number;
  tarih: string; // YYYY-MM-DD
  programId?: number;
  notu?: string;
}

/** Yoklama giris guncelleme ogesi (PUT govdesi cipni eleman). */
export interface UpdateEntryItem {
  ogrenciId: number;
  durum: YoklamaDurumu;
}

// --- Finans modülü (Tahakkuk / Ödeme / Gider / Bakiye) — backend DTO'lari ile birebir aynalanir ---

/** Ödeme yöntemi (backend OdemeYontemi enum). */
export type OdemeYontemi = 'NAKIT' | 'KART' | 'HAVALE';

/** Finans yanitlarindaki öğrenci referansi (özet). */
export interface FinanceStudentRef {
  id: number;
  ad: string;
  soyad: string;
}

/** Finans yanitlarindaki grup referansi (özet). */
export interface FinanceGroupRef {
  id: number;
  ad: string;
}

/** Tahakkuk yaniti — backend AccrualResponse. Para alanlari number VEYA string gelebilir. */
export interface AccrualResponse {
  id: number;
  tutar: string | number;
  donem: string | null;
  aciklama: string | null;
  ogrenci: FinanceStudentRef;
  grup: FinanceGroupRef | null;
}

/**
 * Tahakkuk olusturma govdesi. Para alani (tutar) BigDecimal hassasiyetini korumak icin STRING
 * gonderilir. Bos opsiyonel alanlar gonderilmez.
 */
export interface AccrualInput {
  ogrenciId: number;
  grupId?: number;
  donem?: string;
  tutar: string;
  aciklama?: string;
}

/** Ödeme yanitindaki tahakkuk referansi (özet). */
export interface PaymentAccrualRef {
  id: number;
  tutar: string | number;
  donem: string | null;
}

/** Ödeme yaniti — backend PaymentResponse. Para alanlari number VEYA string gelebilir. */
export interface PaymentResponse {
  id: number;
  tutar: string | number;
  odemeTarihi: string;
  odemeYontemi: OdemeYontemi;
  aciklama: string | null;
  ogrenci: FinanceStudentRef;
  grup: FinanceGroupRef | null;
  accrual: PaymentAccrualRef | null;
}

/**
 * Ödeme olusturma govdesi. Para alani (tutar) STRING gonderilir. odemeTarihi gonderilmezse
 * backend bugunu kullanir. Bos opsiyonel alanlar gonderilmez.
 */
export interface PaymentInput {
  ogrenciId: number;
  accrualId?: number;
  grupId?: number;
  tutar: string;
  odemeTarihi?: string;
  odemeYontemi: OdemeYontemi;
  aciklama?: string;
}

/** Gider yaniti — backend ExpenseResponse. Para alani number VEYA string gelebilir. */
export interface ExpenseResponse {
  id: number;
  tutar: string | number;
  giderTarihi: string;
  kategori: string | null;
  aciklama: string | null;
}

/**
 * Gider olusturma govdesi. Para alani (tutar) STRING gonderilir. giderTarihi gonderilmezse
 * backend bugunu kullanir. Bos opsiyonel alanlar gonderilmez.
 */
export interface ExpenseInput {
  tutar: string;
  giderTarihi?: string;
  kategori?: string;
  aciklama?: string;
}

/** Öğrenci bakiyesi — backend BalanceResponse. Para alanlari number VEYA string gelebilir. */
export interface BalanceResponse {
  ogrenciId: number;
  toplamTahakkuk: string | number;
  toplamOdeme: string | number;
  bakiye: string | number;
}

/** Öğrenci finans özeti — backend FinanceSummaryResponse. */
export interface FinanceSummaryResponse {
  ogrenciId: number;
  tahakkuklar: AccrualResponse[];
  odemeler: PaymentResponse[];
  bakiye: string | number;
}

/** Otomatik aylik tahakkuk uretim/onizleme sonucu — backend AccrualGenerationResult. */
export interface AccrualGenerationResult {
  donem: string;
  uretilenSayisi: number;
  atlananSayisi: number;
  toplamTutar: string | number;
  ozet: { ogrenciId: number; grupId: number; tutar: string | number }[];
}

// --- Hakediş (Payout) modülü — backend DTO'lari ile birebir aynalanir. YALNIZCA ADMIN. ---

/** Hakediş durumu: hesaplandi ya da odendi. */
export type PayoutDurumu = 'HESAPLANDI' | 'ODENDI';

/** Hakediş yanitindaki öğretmen referansi (özet). */
export interface PayoutOgretmenRef {
  id: number;
  ad: string;
  soyad: string;
}

/**
 * Hakediş hesaplama dökümü — backend PayoutDokum. Tipe gore alanlar dolar:
 * SAATLIK → dersSayisi/birimUcret; CIRO_ORANI → toplamTahsilat/kdvOrani/netCiro/oran.
 * Para alanlari number VEYA string gelebilir.
 */
export interface PayoutDokum {
  dersSayisi: number | null;
  birimUcret: string | number | null;
  toplamTahsilat: string | number | null;
  kdvOrani: string | number | null;
  netCiro: string | number | null;
  oran: string | number | null;
}

/** Hakediş yaniti — backend PayoutResponse. Para alanlari number VEYA string gelebilir. */
export interface PayoutResponse {
  id: number;
  ogretmen: PayoutOgretmenRef;
  donem: string;
  hakedisTipi: HakedisTipi;
  hesaplananTutar: string | number;
  durum: PayoutDurumu;
  odemeTarihi: string | null;
  dokum: PayoutDokum;
}

/**
 * Hakediş hesaplama govdesi. kdvOrani BigDecimal hassasiyetini korumak icin STRING
 * gonderilir; bos ise gonderilmez (yalnizca CIRO_ORANI öğretmenlerde anlamli).
 */
export interface CalculatePayoutInput {
  ogretmenId: number;
  donem: string;
  kdvOrani?: string;
}

// --- Stok / Ürün Satışı (Inventory) modülü — backend DTO'lari ile birebir aynalanir ---

/** Ürün yaniti — backend ProductResponse. Para alani number VEYA string gelebilir. */
export interface ProductResponse {
  id: number;
  ad: string;
  satisFiyati: string | number;
  stokAdedi: number;
  aciklama: string | null;
  aktif: boolean;
  olusturulmaTarihi?: string;
  guncellenmeTarihi?: string;
}

/**
 * Ürün olusturma govdesi — backend CreateProductRequest. satisFiyati BigDecimal hassasiyeti
 * icin STRING gonderilir; stokAdedi opsiyonel (null -> 0). aktif GONDERILMEZ (yeni kayit aktif).
 */
export interface ProductInput {
  ad: string;
  satisFiyati: string;
  stokAdedi?: number;
  aciklama?: string;
}

/**
 * Ürün guncelleme govdesi — backend UpdateProductRequest. Stok ve aktiflik BURADA degismez
 * (kendi PATCH uclari var). satisFiyati STRING gonderilir.
 */
export interface UpdateProductInput {
  ad: string;
  satisFiyati: string;
  aciklama?: string;
}

/** Stok guncelleme govdesi — backend UpdateStockRequest. MUTLAK ATAMA (yeni stok degeri). */
export interface StockInput {
  stokAdedi: number;
}

/** Satis yanitindaki ürün referansi (özet). */
export interface SaleUrunRef {
  id: number;
  ad: string;
}

/** Satis yanitindaki öğrenci referansi (özet). */
export interface SaleOgrenciRef {
  id: number;
  ad: string;
  soyad: string;
}

/** Satis yaniti — backend SaleResponse. Para alanlari number VEYA string gelebilir. */
export interface SaleResponse {
  id: number;
  urun: SaleUrunRef;
  ogrenci: SaleOgrenciRef | null;
  adet: number;
  birimFiyat: string | number;
  toplamTutar: string | number;
  satisTarihi: string;
  aciklama: string | null;
  olusturulmaTarihi?: string;
  guncellenmeTarihi?: string;
}

/**
 * Satis olusturma govdesi — backend CreateSaleRequest. urunId zorunlu; ogrenciId opsiyonel.
 * birimFiyat/toplamTutar GONDERILMEZ: backend satis aninda urunun fiyatindan kopyalar.
 * satisTarihi verilmezse backend bugunu kullanir.
 */
export interface SaleInput {
  urunId: number;
  ogrenciId?: number;
  adet: number;
  satisTarihi?: string;
  aciklama?: string;
}
