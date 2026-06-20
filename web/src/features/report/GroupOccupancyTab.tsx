import { useState } from 'react';
import { ApiException } from '../../api/client';
import { TIP_BADGE, TIP_LABEL } from '../group/groupDisplay';
import { useGroupOccupancy } from './useReports';

type AktifFilter = 'all' | 'aktif' | 'pasif';

/** all -> undefined, aktif -> true, pasif -> false (backend aktifMi param). */
function toAktifMi(f: AktifFilter): boolean | undefined {
  if (f === 'aktif') return true;
  if (f === 'pasif') return false;
  return undefined;
}

const FILTERS: { key: AktifFilter; label: string }[] = [
  { key: 'all', label: 'Tümü' },
  { key: 'aktif', label: 'Aktif' },
  { key: 'pasif', label: 'Pasif' },
];

/** Grup Doluluk sekmesi — ADMIN + FRONTDESK + FRONTDESK_ACCOUNTING. Salt okunur. */
export default function GroupOccupancyTab() {
  const [filter, setFilter] = useState<AktifFilter>('all');
  const query = useGroupOccupancy(toAktifMi(filter));
  const rows = query.data?.data ?? [];

  return (
    <div className="space-y-4">
      <div className="tabs">
        {FILTERS.map((f) => (
          <button
            key={f.key}
            type="button"
            onClick={() => setFilter(f.key)}
            className={`tab${filter === f.key ? ' active' : ''}`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {query.isLoading ? (
        <div className="card text-center text-ink-soft">Yükleniyor…</div>
      ) : query.isError ? (
        <div className="card text-center text-red">
          {query.error instanceof ApiException ? query.error.message : 'Bir hata oluştu'}
        </div>
      ) : rows.length === 0 ? (
        <div className="card text-center text-ink-soft">Grup bulunamadı</div>
      ) : (
        <div className="card">
          <table className="data-table">
            <thead>
              <tr>
                <th>Grup</th>
                <th>Tip</th>
                <th>Öğretmen</th>
                <th className="t-right">Aktif Öğrenci Sayısı</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.grupId}>
                  <td>
                    <b>{r.ad}</b>
                  </td>
                  <td>
                    <span className={`badge ${TIP_BADGE[r.tip]}`}>{TIP_LABEL[r.tip]}</span>
                  </td>
                  <td className="text-ink-soft">{r.ogretmenAd}</td>
                  <td className="t-right">{r.aktifOgrenciSayisi}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
