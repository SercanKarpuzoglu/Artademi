import { useEffect, useState } from 'react';
import { ApiException } from '../../api/client';
import type { StudentStatus } from '../../api/types';
import StatusBadge from '../../components/StatusBadge';
import { keycloak } from '../../lib/keycloak';
import { useDebounce } from '../../lib/useDebounce';
import { useStudents } from './useStudents';

const PAGE_SIZE = 20;

const STATUS_TABS: { label: string; value: StudentStatus | undefined }[] = [
  { label: 'Tümü', value: undefined },
  { label: 'Aktif', value: 'AKTIF' },
  { label: 'Deneme', value: 'DENEME' },
  { label: 'Pasif', value: 'PASIF' },
  { label: 'Dondurulmuş', value: 'DONDURULMUS' },
];

/** ISO tarih (YYYY-MM-DD) -> TR (gg.aa.yyyy). */
function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-');
  return y && m && d ? `${d}.${m}.${y}` : iso;
}

export default function StudentListPage() {
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<StudentStatus | undefined>(undefined);
  const [page, setPage] = useState(0);
  const debouncedQ = useDebounce(q, 300);

  // Arama/filtre degisince ilk sayfaya don.
  useEffect(() => {
    setPage(0);
  }, [debouncedQ, status]);

  const query = useStudents({
    q: debouncedQ.trim() || undefined,
    status,
    page,
    size: PAGE_SIZE,
  });

  const claims = keycloak.tokenParsed as
    | { preferred_username?: string; tenant_id?: string }
    | undefined;

  const students = query.data?.data ?? [];
  const meta = query.data?.meta;
  const filtered = Boolean(debouncedQ.trim()) || status !== undefined;

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="border-b bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
          <h1 className="text-lg font-semibold text-gray-800">Artademi · Öğrenciler</h1>
          <div className="flex items-center gap-3 text-sm text-gray-600">
            <span className="hidden text-right sm:block">
              <span className="block font-medium text-gray-700">{claims?.preferred_username}</span>
              <span className="block font-mono text-xs text-gray-400">{claims?.tenant_id}</span>
            </span>
            <button
              type="button"
              onClick={() => keycloak.logout()}
              className="rounded-lg border border-gray-300 px-3 py-1 hover:bg-gray-50"
            >
              Çıkış
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-5xl space-y-4 px-4 py-6">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <input
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Ad, soyad veya TC ara…"
            aria-label="Öğrenci ara"
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm sm:max-w-xs"
          />
          <button
            type="button"
            disabled
            title="Form sonraki adımda gelecek"
            className="cursor-not-allowed rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white opacity-50"
          >
            Yeni Öğrenci
          </button>
        </div>

        <div className="flex flex-wrap gap-2">
          {STATUS_TABS.map((tab) => {
            const active = status === tab.value;
            return (
              <button
                key={tab.label}
                type="button"
                onClick={() => setStatus(tab.value)}
                className={
                  active
                    ? 'rounded-full bg-indigo-600 px-3 py-1 text-sm text-white'
                    : 'rounded-full border border-gray-300 bg-white px-3 py-1 text-sm text-gray-600 hover:bg-gray-50'
                }
              >
                {tab.label}
              </button>
            );
          })}
        </div>

        {query.isLoading ? (
          <p className="py-12 text-center text-gray-500">Yükleniyor…</p>
        ) : query.isError ? (
          <p className="py-12 text-center text-red-700">
            {query.error instanceof ApiException ? query.error.message : 'Bir hata oluştu'}
          </p>
        ) : students.length === 0 ? (
          <p className="py-12 text-center text-gray-500">
            {filtered ? 'Sonuç bulunamadı' : 'Henüz öğrenci yok'}
          </p>
        ) : (
          <>
            <div className="overflow-x-auto rounded-lg border bg-white">
              <table className="min-w-full divide-y divide-gray-200 text-sm">
                <thead className="bg-gray-50 text-left text-xs uppercase tracking-wide text-gray-500">
                  <tr>
                    <th className="px-4 py-2 font-medium">Ad Soyad</th>
                    <th className="px-4 py-2 font-medium">TC</th>
                    <th className="px-4 py-2 font-medium">Doğum Tarihi</th>
                    <th className="px-4 py-2 font-medium">Statü</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {students.map((s) => (
                    <tr key={s.id} className="cursor-default hover:bg-gray-50">
                      <td className="px-4 py-2 text-gray-800">
                        {s.ad} {s.soyad}
                      </td>
                      <td className="px-4 py-2 font-mono text-gray-600">{s.tcKimlikNo}</td>
                      <td className="px-4 py-2 text-gray-600">{formatDate(s.dogumTarihi)}</td>
                      <td className="px-4 py-2">
                        <StatusBadge status={s.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {meta && (
              <div className="flex items-center justify-between text-sm text-gray-600">
                <span>
                  Toplam {meta.totalElements} · Sayfa {meta.page + 1}/{Math.max(meta.totalPages, 1)}
                </span>
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => setPage((p) => Math.max(p - 1, 0))}
                    disabled={meta.page <= 0}
                    className="rounded-lg border border-gray-300 px-3 py-1 hover:bg-gray-50 disabled:opacity-40"
                  >
                    Önceki
                  </button>
                  <button
                    type="button"
                    onClick={() => setPage((p) => p + 1)}
                    disabled={meta.page + 1 >= meta.totalPages}
                    className="rounded-lg border border-gray-300 px-3 py-1 hover:bg-gray-50 disabled:opacity-40"
                  >
                    Sonraki
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </main>
    </div>
  );
}
