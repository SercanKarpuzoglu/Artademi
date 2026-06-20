import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiException } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { Role } from '../../auth/roles';
import { useDebounce } from '../../lib/useDebounce';
import { useRooms, useSetRoomActive } from './useRooms';

const PAGE_SIZE = 20;

const AKTIF_TABS: { label: string; value: boolean | undefined }[] = [
  { label: 'Tümü', value: undefined },
  { label: 'Aktif', value: true },
  { label: 'Pasif', value: false },
];

export default function RoomListPage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole(Role.ADMIN);
  const [q, setQ] = useState('');
  const [aktif, setAktif] = useState<boolean | undefined>(undefined);
  const [page, setPage] = useState(0);
  const debouncedQ = useDebounce(q, 300);
  const navigate = useNavigate();

  useEffect(() => {
    setPage(0);
  }, [debouncedQ, aktif]);

  const query = useRooms({
    q: debouncedQ.trim() || undefined,
    aktif,
    page,
    size: PAGE_SIZE,
  });
  const setActiveMut = useSetRoomActive();

  const rooms = query.data?.data ?? [];
  const meta = query.data?.meta;
  const filtered = Boolean(debouncedQ.trim()) || aktif !== undefined;

  return (
    <>
      <div className="topbar">
        <div>
          <h1>Salonlar</h1>
          <div className="sub">Ders salonu tanımları</div>
        </div>
        <div className="top-actions">
          <input
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Salon ara…"
            aria-label="Salon ara"
            className="rounded-[10px] border border-line bg-card px-3 py-2 text-[13px] focus-visible:outline focus-visible:outline-2 focus-visible:outline-rasp"
          />
          {isAdmin && (
            <button type="button" className="btn btn-primary" onClick={() => navigate('/salonlar/yeni')}>
              + Yeni Salon
            </button>
          )}
        </div>
      </div>

      <div className="tabs mb-[18px]">
        {AKTIF_TABS.map((tab) => (
          <button
            key={tab.label}
            type="button"
            onClick={() => setAktif(tab.value)}
            className={`tab${aktif === tab.value ? ' active' : ''}`}
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
      ) : rooms.length === 0 ? (
        <div className="card text-center text-ink-soft">
          {filtered ? 'Sonuç bulunamadı' : 'Henüz salon yok'}
        </div>
      ) : (
        <>
          <div className="card">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Ad</th>
                  <th className="t-right">Kapasite</th>
                  <th>Aktif</th>
                  {isAdmin && <th className="t-right">Aksiyon</th>}
                </tr>
              </thead>
              <tbody>
                {rooms.map((r) => (
                  <tr key={r.id}>
                    <td>
                      <b>{r.ad}</b>
                    </td>
                    <td className="t-right text-ink-soft">{r.kapasite ?? '—'}</td>
                    <td>
                      <span className={`badge ${r.aktif ? 'b-green' : 'b-gray'}`}>
                        {r.aktif ? 'Aktif' : 'Pasif'}
                      </span>
                    </td>
                    {isAdmin && (
                      <td className="t-right">
                        <div className="inline-flex gap-2">
                          <Link to={`/salonlar/${r.id}/duzenle`} className="btn btn-ghost">
                            Düzenle
                          </Link>
                          <button
                            type="button"
                            className="btn btn-ghost"
                            disabled={setActiveMut.isPending}
                            onClick={() => setActiveMut.mutate({ id: r.id, aktif: !r.aktif })}
                          >
                            {r.aktif ? 'Pasifleştir' : 'Aktifleştir'}
                          </button>
                        </div>
                      </td>
                    )}
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
