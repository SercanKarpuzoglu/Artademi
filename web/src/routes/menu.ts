import {
  BarChart3,
  BellRing,
  CalendarClock,
  CalendarDays,
  CalendarRange,
  ClipboardCheck,
  ClipboardList,
  Coins,
  CreditCard,
  DoorOpen,
  GraduationCap,
  LayoutDashboard,
  MessageSquare,
  ScrollText,
  Package,
  RotateCcw,
  ShieldCheck,
  SlidersHorizontal,
  Store,
  Tags,
  UserCog,
  Users,
  Wallet,
  type LucideIcon,
} from 'lucide-react';
import { Role } from '../auth/roles';

/**
 * Sol menü öğeleri — tek kaynak. Hem sidebar (rol bazlı gizleme) hem route guard'ları
 * aynı {@code roles} listesini kullanır, böylece menü görünürlüğü ile erişim tutarlı kalır.
 *
 * Not: Bu job'da yalnızca "Öğrenciler" gerçek sayfaya bağlı; diğerleri "Yakında" placeholder.
 */
export interface MenuItem {
  label: string;
  path: string;
  icon: LucideIcon;
  /** Sidebar bölüm başlığı (nav-label) — design-reference.html'deki gruplama. */
  section: string;
  roles: readonly Role[];
  /** true ise gerçek sayfa; false ise "Yakında" placeholder (bu job kapsamı). */
  hazir?: boolean;
  /**
   * Açılır alt menü: aynı {@code grup} adını taşıyan ardışık öğeler tek başlık altında toplanır
   * ("Yönetici Paneli"); başlığa tıklayınca açılır, içindeki sayfa aktifse kendiliğinden açık.
   */
  grup?: string;
}

/** Açılır grup başlıklarının ikonları (menü tek kaynak). */
export const GRUP_IKON: Record<string, LucideIcon> = {
  'Yönetici Paneli': SlidersHorizontal,
};

const HEPSI: readonly Role[] = [
  Role.ADMIN,
  Role.FRONTDESK,
  Role.FRONTDESK_ACCOUNTING,
  Role.TEACHER,
  Role.SUPER_ADMIN,
];

const OFIS: readonly Role[] = [Role.ADMIN, Role.FRONTDESK, Role.FRONTDESK_ACCOUNTING];

const OFIS_VE_EGITMEN: readonly Role[] = [
  Role.ADMIN,
  Role.FRONTDESK,
  Role.FRONTDESK_ACCOUNTING,
  Role.TEACHER,
];

const YONETICI_PANELI = 'Yönetici Paneli';

export const MENU: readonly MenuItem[] = [
  // Eğitmen girişinde yalnız Yoklama + Haftalık Program görünür (Genel Bakış ofis rollerine).
  { label: 'Genel Bakış', path: '/dashboard', icon: LayoutDashboard, section: 'Genel', roles: OFIS, hazir: true },
  { label: 'Öğrenciler', path: '/ogrenciler', icon: Users, section: 'Eğitim', roles: OFIS, hazir: true },
  { label: 'Ön Kayıt', path: '/basvurular', icon: ClipboardList, section: 'Eğitim', roles: OFIS, hazir: true },
  {
    label: 'Yoklama',
    path: '/yoklama',
    icon: ClipboardCheck,
    section: 'Eğitim',
    roles: OFIS_VE_EGITMEN,
    hazir: true,
  },
  {
    label: 'Haftalık Program',
    path: '/program',
    icon: CalendarDays,
    section: 'Eğitim',
    roles: OFIS_VE_EGITMEN,
    hazir: true,
  },
  { label: 'Telafi Dersleri', path: '/telafi', icon: CalendarClock, section: 'Eğitim', roles: OFIS, hazir: true },
  // Yönetici Paneli — açılır alt menü (9 Eylül talebi): Eğitmenler, Ders Ücretleri / Gruplar.
  {
    label: 'Eğitmenler',
    path: '/egitmenler',
    icon: UserCog,
    section: YONETICI_PANELI,
    grup: YONETICI_PANELI,
    roles: OFIS,
    hazir: true,
  },
  {
    label: 'Yoklama Listesi',
    path: '/yoklama-listesi',
    icon: ClipboardList,
    section: YONETICI_PANELI,
    grup: YONETICI_PANELI,
    roles: OFIS_VE_EGITMEN,
    hazir: true,
  },
  {
    label: 'Ders Ücretleri / Gruplar',
    path: '/gruplar',
    icon: GraduationCap,
    section: YONETICI_PANELI,
    grup: YONETICI_PANELI,
    roles: OFIS,
    hazir: true,
  },
  { label: 'Dönemler', path: '/donemler', icon: CalendarRange, section: 'Tanımlar', roles: OFIS, hazir: true },
  { label: 'Şubeler', path: '/subeler', icon: Store, section: 'Tanımlar', roles: OFIS, hazir: true },
  { label: 'Branşlar', path: '/branslar', icon: Tags, section: 'Tanımlar', roles: OFIS, hazir: true },
  { label: 'Salonlar', path: '/salonlar', icon: DoorOpen, section: 'Tanımlar', roles: OFIS, hazir: true },
  {
    label: 'Finans',
    path: '/finans',
    icon: Wallet,
    section: 'İşletme',
    roles: [Role.ADMIN, Role.FRONTDESK_ACCOUNTING],
    hazir: true,
  },
  {
    label: 'Bildirim Ayarları',
    path: '/bildirim-ayarlari',
    icon: BellRing,
    section: 'Sistem',
    roles: [Role.ADMIN],
    hazir: true,
  },
  {
    label: 'Borç Hatırlatma',
    path: '/borc-hatirlatma',
    icon: BellRing,
    section: 'İşletme',
    roles: [Role.ADMIN, Role.FRONTDESK_ACCOUNTING],
    hazir: true,
  },
  { label: 'Hakediş', path: '/hakedis', icon: Coins, section: 'İşletme', roles: [Role.ADMIN], hazir: true },
  {
    label: 'Stok / Satış',
    path: '/stok',
    icon: Package,
    section: 'İşletme',
    roles: [Role.ADMIN, Role.FRONTDESK_ACCOUNTING],
    hazir: true,
  },
  {
    label: 'Raporlar',
    path: '/raporlar',
    icon: BarChart3,
    section: 'İşletme',
    roles: [Role.ADMIN, Role.FRONTDESK_ACCOUNTING, Role.FRONTDESK],
    hazir: true,
  },
  {
    label: 'İşlem Kaydı',
    path: '/islem-kaydi',
    icon: ScrollText,
    section: 'Sistem',
    roles: [Role.ADMIN],
    hazir: true,
  },
  {
    label: 'Silinenler',
    path: '/silinenler',
    icon: RotateCcw,
    section: 'Sistem',
    roles: [Role.ADMIN],
    hazir: true,
  },
  {
    label: 'Geri Bildirim',
    path: '/geri-bildirim',
    icon: MessageSquare,
    section: 'Sistem',
    roles: HEPSI,
    hazir: true,
  },
  {
    label: 'Kullanıcılar',
    path: '/kullanicilar',
    icon: ShieldCheck,
    section: 'Sistem',
    roles: [Role.ADMIN],
    hazir: true,
  },
  {
    label: 'Abonelik',
    path: '/abonelik',
    icon: CreditCard,
    section: 'Sistem',
    roles: [Role.ADMIN],
    hazir: true,
  },
];
