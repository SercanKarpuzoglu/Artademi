import type { StudentStatus } from '../api/types';

const LABEL: Record<StudentStatus, string> = {
  AKTIF: 'Aktif',
  DENEME: 'Deneme',
  PASIF: 'Pasif',
  DONDURULMUS: 'Dondurulmuş',
};

// design-reference.html .badge sistemi: AKTIF yeşil, DENEME amber, PASIF gri, DONDURULMUS mavi.
const BADGE: Record<StudentStatus, string> = {
  AKTIF: 'b-green',
  DENEME: 'b-amber',
  PASIF: 'b-gray',
  DONDURULMUS: 'b-blue',
};

export default function StatusBadge({ status }: { status: StudentStatus }) {
  return <span className={`badge ${BADGE[status]}`}>{LABEL[status]}</span>;
}
