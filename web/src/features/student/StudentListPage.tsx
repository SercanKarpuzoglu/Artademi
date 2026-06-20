import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiException } from '../../api/client';
import type { StudentStatus } from '../../api/types';
import StatusBadge from '../../components/StatusBadge';
import { formatDate } from '../../lib/format';
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

export default function StudentListPage() {
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<StudentStatus | undefined>(undefined);
  const [page, setPage] = useState(0);
  const debouncedQ = useDebounce(q, 300);
  const navigate = useNavigate();

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

  const students = query.data?.data ?? [];
  const meta = query.data?.meta;
  const filtered = Boolean(debouncedQ.trim()) || status !== undefined;

  return (
    <>
      <div className="topbar">
        <div>
          <h1>Öğrenciler</h1>
          <div className="sub">Öğrenci kayıtları, statü ve veli iletişimi</div>
        </div>
        <div className="top-actions">
          <input
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Ad, soyad veya TC ara…"
            aria-label="Öğrenci ara"
            className="rounded-[10px] border border-line bg-card px-3 py-2 text-[13px] focus-visible:outline focus-visible:outline-2 focus-visible:outline-rasp"
          />
          <button type="button" className="btn btn-primary" onClick={() => navigate('/ogrenciler/yeni')}>
            + Yeni Öğrenci
          </button>
        </div>
      </div>

      <div className="tabs mb-[18px]">
        {STATUS_TABS.map((tab) => (
          <button
            key={tab.label}
            type="button"
            onClick={() => setStatus(tab.value)}
            className={`tab${status === tab.value ? ' active' : ''}`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {query.isLoading ? (
        <div className="card text-center text-ink-soft">Yükleniyor…</div>
      ) : query.isError ? (
        <div className="card text-center text-red">
          {query.error instanceof ApiException ? query.error.message : 'Bir hata oluştu'}
        </div>
      ) : students.length === 0 ? (
        <div className="card text-center text-ink-soft">
          {filtered ? 'Sonuç bulunamadı' : 'Henüz öğrenci yok'}
        </div>
      ) : (
        <>
          <div className="card">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Ad Soyad</th>
                  <th>TC</th>
                  <th>Doğum Tarihi</th>
                  <th>Statü</th>
                  <th className="sr-only">İşlem</th>
                </tr>
              </thead>
              <tbody>
                {students.map((s) => (
                  <tr
                    key={s.id}
                    onClick={() => navigate(`/ogrenciler/${s.id}`)}
                    className="cursor-pointer"
                  >
                    <td>
                      <b>
                        {s.ad} {s.soyad}
                      </b>
                    </td>
                    <td className="font-mono text-ink-soft">{s.tcKimlikNo}</td>
                    <td className="text-ink-soft">{formatDate(s.dogumTarihi)}</td>
                    <td>
                      <StatusBadge status={s.status} />
                    </td>
                    <td className="t-right">
                      <Link
                        to={`/ogrenciler/${s.id}/duzenle`}
                        onClick={(e) => e.stopPropagation()}
                        className="btn btn-ghost"
                      >
                        Detay
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {meta && (
            <div className="mt-4 flex items-center justify-between text-[13px] text-ink-soft">
              <span>
                Toplam {meta.totalElements} · Sayfa {meta.page + 1}/{Math.max(meta.totalPages, 1)}
              </span>
              <div className="flex gap-2">
                <button
                  type="button"
                  className="btn btn-ghost disabled:opacity-40"
                  onClick={() => setPage((p) => Math.max(p - 1, 0))}
                  disabled={meta.page <= 0}
                >
                  Önceki
                </button>
                <button
                  type="button"
                  className="btn btn-ghost disabled:opacity-40"
                  onClick={() => setPage((p) => p + 1)}
                  disabled={meta.page + 1 >= meta.totalPages}
                >
                  Sonraki
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </>
  );
}
