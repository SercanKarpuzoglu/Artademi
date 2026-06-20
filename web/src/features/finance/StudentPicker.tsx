import { useState } from 'react';
import type { StudentResponse } from '../../api/types';
import { useDebounce } from '../../lib/useDebounce';
import { useStudents } from '../student/useStudents';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

/**
 * Aramali öğrenci seçici (tüm listeyi çekmeden ?q= ile). Seçilince {@code onSelect} cagrilir;
 * seçili öğrenci varsa adi gösterilir ve "Değiştir" ile temizlenir.
 */
export default function StudentPicker({
  selected,
  onSelect,
  disabled,
}: {
  selected: StudentResponse | null;
  onSelect: (s: StudentResponse | null) => void;
  disabled?: boolean;
}) {
  const [q, setQ] = useState('');
  const debouncedQ = useDebounce(q, 300);
  const studentsQuery = useStudents({ q: debouncedQ.trim() || undefined, size: 10 });
  const matches = debouncedQ.trim() ? studentsQuery.data?.data ?? [] : [];

  if (selected) {
    return (
      <div className="flex items-center justify-between rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px]">
        <span>
          {selected.ad} {selected.soyad}
        </span>
        <button
          type="button"
          className="btn btn-ghost"
          disabled={disabled}
          onClick={() => {
            onSelect(null);
            setQ('');
          }}
        >
          Değiştir
        </button>
      </div>
    );
  }

  return (
    <div className="relative">
      <input
        type="search"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder="Öğrenci ara: ad, soyad veya TC…"
        aria-label="Öğrenci ara"
        className={inputClass}
        disabled={disabled}
      />
      {matches.length > 0 && (
        <ul className="absolute z-10 mt-1 max-h-64 w-full overflow-auto rounded-[10px] border border-line bg-card shadow-lg">
          {matches.map((s) => (
            <li key={s.id}>
              <button
                type="button"
                className="flex w-full items-center justify-between px-3 py-2 text-left text-[13.5px] hover:bg-gray-50"
                onClick={() => {
                  onSelect(s);
                  setQ('');
                }}
              >
                <span>
                  {s.ad} {s.soyad}
                </span>
                <span className="font-mono text-xs text-ink-soft">{s.tcKimlikNo}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
