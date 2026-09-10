import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiException } from '../../api/client';
import type { StudentListeSatiri, StudentStatus } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Role } from '../../auth/roles';
import StatusBadge from '../../components/StatusBadge';
import { formatMoney } from '../../lib/format';
import { useDebounce } from '../../lib/useDebounce';
import KaraListeModal, { type KaraListeHedef } from './KaraListeModal';
import { useStudentListe } from './useStudents';

const PAGE_SIZE = 20;

const STATUS_TABS: { label: string; value: StudentStatus | undefined }[] = [
  { label: 'Tümü', value: undefined },
  { label: 'Aktif', value: 'AKTIF' },
  { label: 'Deneme', value: 'DENEME' },
  { label: 'Pasif', value: 'PASIF' },
  { label: 'Dondurulmuş', value: 'DONDURULMUS' },
];

/**
 * Öğrenci listesi (Dalga B sütunları): ad soyad, gruplar, statü, ödeme durumu (yalnız para
 * görebilen roller), devam durumu (−N = N derstir gelmemiş), kara liste.
 */
export default function StudentListPage() {
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<StudentStatus | undefined>(undefined);
  const [page, setPage] = useState(0);
  const [karaListeHedef, setKaraListeHedef] = useState<KaraListeHedef | null>(null);
  const debouncedQ = useDebounce(q, 300);
  const navigate = useNavigate();
  const { hasAnyRole } = useAuth();
  // Backend ön büroya bakiyeyi hiç göndermez; sütunu da göstermeyiz.
  const paraGorebilir = hasAnyRole([Role.ADMIN, Role.FRONTDESK_ACCOUNTING]);

  // Arama/filtre degisince ilk sayfaya don.
  useEffect(() => {
    setPage(0);
  }, [debouncedQ, status]);

  const query = useStudentListe({
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
          <div className="sub">Öğrenci kayıtları, gruplar, ödeme ve devam durumu</div>
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
                  <th>Gruplar</th>
                  <th>Statü</th>
                  {paraGorebilir && <th>Ödeme Durumu</th>}
                  <th>Devam</th>
                  <th className="t-right">İşlem</th>
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
                      <div className="flex items-center gap-2">
                        <b>
                          {s.ad} {s.soyad}
                        </b>
                        {s.karaListe && (
                          <span className="badge b-red" title={s.karaListeAciklama ?? undefined}>
                            Kara liste
                          </span>
                        )}
                      </div>
                      <div className="font-mono text-[11.5px] text-ink-soft">{s.tcKimlikNo}</div>
                    </td>
                    <td>
                      {s.gruplar.length === 0 ? (
                        <span className="text-ink-soft">—</span>
                      ) : (
                        <div className="flex flex-wrap gap-1">
                          {s.gruplar.map((g) => (
                            <Link
                              key={g.id}
                              to={`/gruplar/${g.id}`}
                              onClick={(e) => e.stopPropagation()}
                              className="badge b-gray hover:underline"
                            >
                              {g.ad}
                            </Link>
                          ))}
                        </div>
                      )}
                    </td>
                    <td>
                      <StatusBadge status={s.status} />
                    </td>
                    {paraGorebilir && (
                      <td>
                        <OdemeDurumu bakiye={s.bakiye} />
                      </td>
                    )}
                    <td>
                      <DevamDurumu seri={s.devamsizlikSerisi} />
                    </td>
                    <td className="t-right">
                      <div className="inline-flex gap-2">
                        <button
                          type="button"
                          className="btn btn-ghost"
                          onClick={(e) => {
                            e.stopPropagation();
                            setKaraListeHedef({
                              id: s.id,
                              ad: s.ad,
                              soyad: s.soyad,
                              karaListe: s.karaListe,
                              karaListeAciklama: s.karaListeAciklama,
                            });
                          }}
                        >
                          {s.karaListe ? 'Kara listeden çıkar' : 'Kara listeye al'}
                        </button>
                        <Link
                          to={`/ogrenciler/${s.id}`}
                          onClick={(e) => e.stopPropagation()}
                          className="btn btn-ghost"
                        >
                          Detay
                        </Link>
                      </div>
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

      {karaListeHedef && (
        <KaraListeModal hedef={karaListeHedef} onClose={() => setKaraListeHedef(null)} />
      )}
    </>
  );
}

/** Bakiye: pozitif borç (kırmızı), sıfır ödendi (yeşil), negatif alacak (mavi). */
function OdemeDurumu({ bakiye }: { bakiye: StudentListeSatiri['bakiye'] }) {
  if (bakiye === null || bakiye === undefined) return <span className="text-ink-soft">—</span>;
  const n = Number(bakiye);
  if (n > 0) {
    return (
      <span className="badge b-red">
        Borç <span className="amount">{formatMoney(bakiye)} ₺</span>
      </span>
    );
  }
  if (n < 0) {
    return (
      <span className="badge b-blue">
        Alacak <span className="amount">{formatMoney(-n)} ₺</span>
      </span>
    );
  }
  return <span className="badge b-green">Ödendi</span>;
}

/** Devam: null yoklama yok; 0 son derse geldi; −N N derstir gelmemiş (2+ kırmızı). */
function DevamDurumu({ seri }: { seri: number | null }) {
  if (seri === null || seri === undefined) return <span className="text-ink-soft">—</span>;
  if (seri === 0) return <span className="badge b-green">Geliyor</span>;
  const n = -seri;
  return (
    <span className={`badge ${n >= 2 ? 'b-red' : 'b-amber'}`} title={`${n} derstir gelmemiş`}>
      {seri} · {n} derstir yok
    </span>
  );
}
