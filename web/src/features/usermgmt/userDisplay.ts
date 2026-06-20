import { ROLE_LABEL, type Role } from '../../auth/roles';

/**
 * Atanabilir roller — SUPER_ADMIN BİLİNÇLİ OLARAK YOK (admin atayamaz, backend de reddeder).
 * Form checkbox'ları ve liste filtresi bu sırayı kullanır.
 */
export const ASSIGNABLE_ROLES = ['ADMIN', 'FRONTDESK', 'FRONTDESK_ACCOUNTING', 'TEACHER'] as const;

// design-reference.html .badge sistemi: ADMIN ahududu, FD gri, FD/Muh mavi, Öğretmen amber.
const ROLE_BADGE: Record<string, string> = {
  ADMIN: 'b-rasp',
  FRONTDESK: 'b-gray',
  FRONTDESK_ACCOUNTING: 'b-blue',
  TEACHER: 'b-amber',
  SUPER_ADMIN: 'b-rasp',
};

/** Ham rol string'i için .badge sınıfı (bilinmeyen rol -> nötr gri). */
export function roleBadgeClass(role: string): string {
  return ROLE_BADGE[role] ?? 'b-gray';
}

/** Ham rol string'i için insana okunur etiket (auth/roles ROLE_LABEL'i tekrar kullanır). */
export function roleLabel(role: string): string {
  return ROLE_LABEL[role as Role] ?? role;
}
