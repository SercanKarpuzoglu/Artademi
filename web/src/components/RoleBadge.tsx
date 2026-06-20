import { ROLE_LABEL, type Role } from '../auth/roles';

// design-reference.html .badge sistemi.
const BADGE: Record<Role, string> = {
  ADMIN: 'b-rasp',
  FRONTDESK_ACCOUNTING: 'b-blue',
  FRONTDESK: 'b-gray',
  TEACHER: 'b-amber',
  SUPER_ADMIN: 'b-rasp',
};

/** Kullanıcının (birincil) rolünü gösteren rozet — referans .badge kalıbı. */
export default function RoleBadge({ role }: { role: Role }) {
  return <span className={`badge ${BADGE[role]}`}>{ROLE_LABEL[role]}</span>;
}
