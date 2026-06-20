import {
  BarChart3,
  ClipboardCheck,
  Coins,
  DoorOpen,
  GraduationCap,
  LayoutDashboard,
  Package,
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
}

const HEPSI: readonly Role[] = [
  Role.ADMIN,
  Role.FRONTDESK,
  Role.FRONTDESK_ACCOUNTING,
  Role.TEACHER,
  Role.SUPER_ADMIN,
];

const OFIS: readonly Role[] = [Role.ADMIN, Role.FRONTDESK, Role.FRONTDESK_ACCOUNTING];

export const MENU: readonly MenuItem[] = [
  { label: 'Genel Bakış', path: '/dashboard', icon: LayoutDashboard, section: 'Genel', roles: HEPSI, hazir: true },
  { label: 'Öğrenciler', path: '/ogrenciler', icon: Users, section: 'Eğitim', roles: OFIS, hazir: true },
  { label: 'Gruplar / Kayıt', path: '/gruplar', icon: GraduationCap, section: 'Eğitim', roles: OFIS, hazir: true },
  {
    label: 'Yoklama',
    path: '/yoklama',
    icon: ClipboardCheck,
    section: 'Eğitim',
    roles: [Role.ADMIN, Role.FRONTDESK, Role.FRONTDESK_ACCOUNTING, Role.TEACHER],
    hazir: true,
  },
  { label: 'Branşlar', path: '/branslar', icon: Tags, section: 'Tanımlar', roles: OFIS, hazir: true },
  { label: 'Salonlar', path: '/salonlar', icon: DoorOpen, section: 'Tanımlar', roles: OFIS, hazir: true },
  { label: 'Öğretmenler', path: '/ogretmenler', icon: UserCog, section: 'Tanımlar', roles: OFIS, hazir: true },
  {
    label: 'Finans',
    path: '/finans',
    icon: Wallet,
    section: 'İşletme',
    roles: [Role.ADMIN, Role.FRONTDESK_ACCOUNTING],
  },
  { label: 'Hakediş', path: '/hakedis', icon: Coins, section: 'İşletme', roles: [Role.ADMIN] },
  {
    label: 'Stok / Satış',
    path: '/stok',
    icon: Package,
    section: 'İşletme',
    roles: [Role.ADMIN, Role.FRONTDESK_ACCOUNTING],
  },
  {
    label: 'Raporlar',
    path: '/raporlar',
    icon: BarChart3,
    section: 'İşletme',
    roles: [Role.ADMIN, Role.FRONTDESK_ACCOUNTING, Role.FRONTDESK],
  },
];
