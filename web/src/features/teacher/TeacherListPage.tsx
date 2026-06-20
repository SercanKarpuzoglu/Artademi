import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiException } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { Role } from '../../auth/roles';
import { formatMoney } from '../../lib/format';
import { useDebounce } from '../../lib/useDebounce';
import { useBranches } from '../branch/useBranches';
import { HAKEDIS_BADGE, HAKEDIS_LABEL } from './teacherDisplay';
import { useSetTeacherActive, useTeachers } from './useTeachers';

const PAGE_SIZE = 20;

const AKTIF_TABS: { label: string; value: boolean | undefined }[] = [
  { label: 'Tümü', value: undefined },
  { label: 'Aktif', value: true },
  { label: 'Pasif', value: false },
];

export default function TeacherListPage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole(Role.ADMIN);
  const [q, setQ] = useState('');
  const [aktif, setAktif] = useState<boolean | undefined>(undefined);
  const [bransId, setBransId] = useState<number | undefined>(undefined);
  const [page, setPage] = useState(0);
  const debouncedQ = useDebounce(q, 300);
  const navigate = useNavigate();

  useEffect(() => {
    setPage(0);
  }, [debouncedQ, aktif, bransId]);

  const query = useTeachers({
    q: debouncedQ.trim() || undefined,
    aktif,
    bransId,
    page,
    size: PAGE_SIZE,
  });
  const setActiveMut = useSetTeacherActive();

  // Branş filtre dropdown'u icin aktif branşlar.
  const branchQuery = useBranches({ aktif: true, size: 200 });
  const branchOptions = branchQuery.data?.data ?? [];

  const teachers = query.data?.data ?? [];
  const meta = query.data?.meta;
  const filtered = Boolean(debouncedQ.trim()) || aktif !== undefined || bransId !== undefined;

  return (
    <>
      <div className="topbar">
        <div>
          <h1>Öğretmenler</h1>
          <div className="sub">Öğretmen kayıtları, branşlar ve hakediş</div>
        </div>
        <div className="top-actions">
          <select
            value={bransId ?? ''}
            onChange={(e) => setBransId(e.target.value ? Number(e.target.value) : undefined)}
            aria-label="Branşa göre filtrele"
            className="rounded-[10px] border border-line bg-card px-3 py-2 text-[13px] focus-visible:outline focus-visible:outline-2 focus-visible:outline-rasp"
          >
            <option value="">Tüm branşlar</option>
            {branchOptions.map((b) => (
              <option key={b.id} value={b.id}>
                {b.ad}
              </option>
            ))}
          </select>
          <input
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Ad veya soyad ara…"
            aria-label="Öğretmen ara"
            className="rounded-[10px] border border-line bg-card px-3 py-2 text-[13px] focus-visible:outline focus-visible:outline-2 focus-visible:outline-rasp"
          />
          {isAdmin && (
            <button type="button" className="btn btn-primary" onClick={() => navigate('/ogretmenler/yeni')}>
              + Yeni Öğretmen
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
      ) : teachers.length === 0 ? (
        <div className="card text-center text-ink-soft">
          {filtered ? 'Sonuç bulunamadı' : 'Henüz öğretmen yok'}
        </div>
      ) : (
        <>
          <div className="card">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Ad Soyad</th>
                  <th>Branşlar</th>
                  <th>Hakediş Tipi</th>
                  <th>Aktif</th>
                  {isAdmin && <th className="t-right">Aksiyon</th>}
                </tr>
              </thead>
              <tbody>
                {teachers.map((t) => (
                  <tr key={t.id}>
                    <td>
                      <b>
                        {t.ad} {t.soyad}
                      </b>
                    </td>
                    <td className="text-ink-soft">
                      {t.branslar.length > 0 ? t.branslar.map((b) => b.ad).join(', ') : '—'}
                    </td>
                    <td>
                      <span className={`badge ${HAKEDIS_BADGE[t.hakedisTipi]}`}>
                        {HAKEDIS_LABEL[t.hakedisTipi]}
                      </span>
                      {t.hakedisTipi === 'SAATLIK' ? (
                        <span className="amount ml-2">{formatMoney(t.saatlikUcret)} ₺</span>
                      ) : (
                        <span className="amount ml-2">%{formatMoney(t.ciroOrani)}</span>
                      )}
                    </td>
                    <td>
                      <span className={`badge ${t.aktif ? 'b-green' : 'b-gray'}`}>
                        {t.aktif ? 'Aktif' : 'Pasif'}
                      </span>
                    </td>
                    {isAdmin && (
                      <td className="t-right">
                        <div className="inline-flex gap-2">
                          <Link to={`/ogretmenler/${t.id}/duzenle`} className="btn btn-ghost">
                            Düzenle
                          </Link>
                          <button
                            type="button"
                            className="btn btn-ghost"
                            disabled={setActiveMut.isPending}
                            onClick={() => setActiveMut.mutate({ id: t.id, aktif: !t.aktif })}
                          >
                            {t.aktif ? 'Pasifleştir' : 'Aktifleştir'}
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
