import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { getIslemKaydi, type IslemKaydi } from '../../api/audit';
import { ApiException } from '../../api/client';

const SAYFA_BOYUTU = 25;

/** İşlem tipine göre rozet — silme/değişiklik göz ile ayrışsın. */
function rozet(metot: string): string {
  if (metot === 'DELETE') return 'b-red';
  if (metot === 'POST') return 'b-green';
  return 'b-amber';
}

/**
 * Kurum içi işlem kaydı (SADECE ADMIN): kim, ne zaman, ne yaptı.
 *
 * Kayıtlar otomatik tutulur (her başarılı değiştirici istek) ve salt-okunurdur;
 * kurum yöneticisi bile silemez — izin değeri değiştirilemez olmasından gelir.
 */
export default function IslemKaydiPage() {
  const [sayfa, setSayfa] = useState(0);
  const q = useQuery({
    queryKey: ['audit', sayfa],
    queryFn: () => getIslemKaydi({ page: sayfa, size: SAYFA_BOYUTU }),
    placeholderData: keepPreviousData,
  });

  return (
    <div>
      <div className="topbar">
        <div>
          <h1>İşlem Kaydı</h1>
          <div className="sub">Kurumunuzda kim, ne zaman, ne yaptı</div>
        </div>
      </div>

      <div className="card">
        {q.isLoading ? (
          <p className="py-8 text-center text-ink-soft">Yükleniyor…</p>
        ) : q.isError ? (
          <p className="py-8 text-center text-red">
            {q.error instanceof ApiException ? q.error.message : 'İşlem kaydı yüklenemedi'}
          </p>
        ) : q.data && q.data.rows.length === 0 ? (
          <p className="py-8 text-center text-[13.5px] text-ink-soft">
            Henüz kayıtlı işlem yok. Öğrenci ekleme, yoklama alma, tahsilat girme gibi işlemler
            burada iz bırakır.
          </p>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>İşlem</th>
                    <th>Kayıt</th>
                    <th>Yapan</th>
                    <th>Tarih</th>
                  </tr>
                </thead>
                <tbody>
                  {q.data?.rows.map((r) => (
                    <Satir key={r.id} r={r} />
                  ))}
                </tbody>
              </table>
            </div>

            {(q.data?.totalPages ?? 1) > 1 && (
              <div className="mt-3 flex items-center justify-between text-[13px]">
                <span className="text-ink-soft">
                  Sayfa {sayfa + 1} / {q.data?.totalPages} · {q.data?.totalElements} kayıt
                </span>
                <div className="flex gap-2">
                  <button
                    type="button"
                    className="btn btn-ghost"
                    disabled={sayfa === 0}
                    onClick={() => setSayfa((s) => Math.max(0, s - 1))}
                  >
                    Önceki
                  </button>
                  <button
                    type="button"
                    className="btn btn-ghost"
                    disabled={sayfa + 1 >= (q.data?.totalPages ?? 1)}
                    onClick={() => setSayfa((s) => s + 1)}
                  >
                    Sonraki
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

function Satir({ r }: { r: IslemKaydi }) {
  return (
    <tr>
      <td>
        <span className={`badge ${rozet(r.metot)}`}>{r.eylem}</span>
      </td>
      <td className="text-ink-soft">{r.kayitId ? `#${r.kayitId}` : '—'}</td>
      <td>
        <span className="font-semibold">{r.actorAd ?? r.actor}</span>
        {r.actorAd && <span className="ml-1 text-[12px] text-ink-soft">({r.actor})</span>}
      </td>
      <td className="whitespace-nowrap text-ink-soft">{zaman(r.createdAt)}</td>
    </tr>
  );
}

function zaman(iso: string): string {
  return new Date(iso).toLocaleString('tr-TR', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
