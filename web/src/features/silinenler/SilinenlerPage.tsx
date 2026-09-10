import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ApiException } from '../../api/client';
import { geriAl, silinenler, TUR_ETIKET, type SilinebilirTur } from '../../api/silme';
import { formatDateTime } from '../../lib/format';

const TURLER = Object.keys(TUR_ETIKET) as SilinebilirTur[];

/**
 * Silinenler (SADECE ADMIN): yumuşak silinen kayıtlar tür tür listelenir, tek tıkla geri alınır.
 * Geri alınan kayıt eski yerinde görünür; ayrılan grup kayıtları AYRILDI kalır (elle yeniden eklenir).
 */
export default function SilinenlerPage() {
  const [tur, setTur] = useState<SilinebilirTur>('ogrenci');
  const qc = useQueryClient();
  const [hata, setHata] = useState<string | null>(null);
  const q = useQuery({ queryKey: ['silinenler', tur], queryFn: () => silinenler(tur) });
  const geri = useMutation({
    mutationFn: (id: number) => geriAl(tur, id),
    onSuccess: async () => {
      setHata(null);
      await qc.invalidateQueries();
    },
    onError: (e) => setHata(e instanceof ApiException ? e.message : 'Geri alınamadı.'),
  });
  const liste = q.data ?? [];

  return (
    <>
      <div className="topbar">
        <div>
          <h1>Silinenler</h1>
          <div className="sub">Yumuşak silinen kayıtlar; geri alınca eski yerine döner</div>
        </div>
      </div>

      <div className="tabs mb-[18px] flex-wrap">
        {TURLER.map((t) => (
          <button
            key={t}
            type="button"
            className={`tab${tur === t ? ' active' : ''}`}
            onClick={() => setTur(t)}
          >
            {TUR_ETIKET[t]}
          </button>
        ))}
      </div>

      {hata && (
        <div className="mb-4 rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
          {hata}
        </div>
      )}

      {q.isLoading ? (
        <div className="card text-center text-ink-soft">Yükleniyor…</div>
      ) : q.isError ? (
        <div className="card text-center text-red">
          {q.error instanceof ApiException ? q.error.message : 'Liste yüklenemedi'}
        </div>
      ) : liste.length === 0 ? (
        <div className="card text-center text-ink-soft">Silinmiş {TUR_ETIKET[tur].toLowerCase()} yok</div>
      ) : (
        <div className="card">
          <table className="data-table">
            <thead>
              <tr>
                <th>Kayıt</th>
                <th>Silinme</th>
                <th>Silen</th>
                <th className="t-right">İşlem</th>
              </tr>
            </thead>
            <tbody>
              {liste.map((k) => (
                <tr key={k.id}>
                  <td>
                    <b>{k.ad}</b> <span className="text-xs text-ink-soft">#{k.id}</span>
                  </td>
                  <td className="text-ink-soft">{formatDateTime(k.silindiTarihi)}</td>
                  <td className="text-ink-soft">{k.silen ?? '—'}</td>
                  <td className="t-right">
                    <button
                      type="button"
                      className="btn btn-ghost"
                      disabled={geri.isPending}
                      onClick={() => geri.mutate(k.id)}
                    >
                      Geri al
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
