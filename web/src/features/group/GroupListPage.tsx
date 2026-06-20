import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiException } from '../../api/client';
import type { GrupTipi } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Role } from '../../auth/roles';
import { formatMoney } from '../../lib/format';
import { useDebounce } from '../../lib/useDebounce';
import { TIP_BADGE, TIP_LABEL } from './groupDisplay';
import { useGroups, useSetGroupActive } from './useGroups';

const PAGE_SIZE = 20;

const TIP_TABS: { label: string; value: GrupTipi | undefined }[] = [
  { label: 'Tümü', value: undefined },
  { label: 'Grup', value: 'GRUP' },
  { label: 'Özel', value: 'OZEL' },
];

const AKTIF_TABS: { label: string; value: boolean | undefined }[] = [
  { label: 'Aktif', value: true },
  { label: 'Pasif', value: false },
  { label: 'Tümü', value: undefined },
];

export default function GroupListPage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole(Role.ADMIN);
  const [q, setQ] = useState('');
  const [tip, setTip] = useState<GrupTipi | undefined>(undefined);
  const [aktif, setAktif] = useState<boolean | undefined>(true);
  const [page, setPage] = useState(0);
  const debouncedQ = useDebounce(q, 300);
  const navigate = useNavigate();

  useEffect(() => {
    setPage(0);
  }, [debouncedQ, tip, aktif]);

  const query = useGroups({
    q: debouncedQ.trim() || undefined,
    tip,
    aktif,
    page,
    size: PAGE_SIZE,
  });
  const setActiveMut = useSetGroupActive();

  const groups = query.data?.data ?? [];
  const meta = query.data?.meta;
  const filtered = Boolean(debouncedQ.trim()) || tip !== undefined || aktif !== true;

  return (
    <>
      <div className="topbar">
        <div>
          <h1>Gruplar</h1>
          <div className="sub">Grup ve özel ders kayıtları, branş, öğretmen ve ücret</div>
        </div>
        <div className="top-actions">
          <input
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Grup adı ara…"
            aria-label="Grup ara"
            className="rounded-[10px] border border-line bg-card px-3 py-2 text-[13px] focus-visible:outline focus-visible:outline-2 focus-visible:outline-rasp"
          />
          {isAdmin && (
            <button type="button" className="btn btn-primary" onClick={() => navigate('/gruplar/yeni')}>
              + Yeni Grup
            </button>
          )}
        </div>
      </div>

      <div className="tabs mb-[14px]">
        {TIP_TABS.map((t) => (
          <button
            key={t.label}
            type="button"
            onClick={() => setTip(t.value)}
            className={`tab${tip === t.value ? ' active' : ''}`}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className="tabs mb-[18px]">
        {AKTIF_TABS.map((t) => (
          <button
            key={t.label}
            type="button"
            onClick={() => setAktif(t.value)}
            className={`tab${aktif === t.value ? ' active' : ''}`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {query.isLoading ? (
        <div className="card text-center text-ink-soft">Yükleniyor…</div>
      ) : query.isError ? (
        <div className="card text-center text-red">
          {query.error instanceof ApiException ? query.error.message : 'Bir hata oluştu'}
        </div>
      ) : groups.length === 0 ? (
        <div className="card text-center text-ink-soft">
          {filtered ? 'Sonuç bulunamadı' : 'Henüz grup yok'}
        </div>
      ) : (
        <>
          <div className="card">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Ad</th>
                  <th>Tip</th>
                  <th>Branş</th>
                  <th>Öğretmen</th>
                  <th>Salon</th>
                  <th className="t-right">Ücret</th>
                  <th>Aktif</th>
                  {isAdmin && <th className="t-right">Aksiyon</th>}
                </tr>
              </thead>
              <tbody>
                {groups.map((g) => (
                  <tr
                    key={g.id}
                    onClick={() => navigate(`/gruplar/${g.id}`)}
                    className="cursor-pointer"
                  >
                    <td>
                      <b>{g.ad}</b>
                    </td>
                    <td>
                      <span className={`badge ${TIP_BADGE[g.tip]}`}>{TIP_LABEL[g.tip]}</span>
                    </td>
                    <td className="text-ink-soft">{g.brans?.ad ?? '—'}</td>
                    <td className="text-ink-soft">
                      {g.ogretmen ? `${g.ogretmen.ad} ${g.ogretmen.soyad}` : '—'}
                    </td>
                    <td className="text-ink-soft">{g.salon?.ad ?? '—'}</td>
                    <td className="t-right">
                      <span className="amount">
                        {g.tip === 'GRUP'
                          ? formatMoney(g.aylikAidat)
                          : formatMoney(g.dersBasiUcret)}{' '}
                        ₺
                      </span>
                    </td>
                    <td>
                      <span className={`badge ${g.aktif ? 'b-green' : 'b-gray'}`}>
                        {g.aktif ? 'Aktif' : 'Pasif'}
                      </span>
                    </td>
                    {isAdmin && (
                      <td className="t-right">
                        <div className="inline-flex gap-2">
                          <Link
                            to={`/gruplar/${g.id}/duzenle`}
                            onClick={(e) => e.stopPropagation()}
                            className="btn btn-ghost"
                          >
                            Düzenle
                          </Link>
                          <button
                            type="button"
                            className="btn btn-ghost"
                            disabled={setActiveMut.isPending}
                            onClick={(e) => {
                              e.stopPropagation();
                              setActiveMut.mutate({ id: g.id, aktif: !g.aktif });
                            }}
                          >
                            {g.aktif ? 'Pasifleştir' : 'Aktifleştir'}
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
