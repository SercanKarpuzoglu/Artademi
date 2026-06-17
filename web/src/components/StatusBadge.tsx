import type { StudentStatus } from '../api/types';

const LABEL: Record<StudentStatus, string> = {
  AKTIF: 'Aktif',
  DENEME: 'Deneme',
  PASIF: 'Pasif',
  DONDURULMUS: 'Dondurulmuş',
};

// AKTIF yesil, DENEME sari, PASIF gri, DONDURULMUS mavi.
const STYLE: Record<StudentStatus, string> = {
  AKTIF: 'bg-green-100 text-green-800',
  DENEME: 'bg-yellow-100 text-yellow-800',
  PASIF: 'bg-gray-100 text-gray-700',
  DONDURULMUS: 'bg-blue-100 text-blue-800',
};

export default function StatusBadge({ status }: { status: StudentStatus }) {
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${STYLE[status]}`}>
      {LABEL[status]}
    </span>
  );
}
