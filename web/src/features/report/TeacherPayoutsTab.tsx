import { useState } from 'react';
import { ApiException } from '../../api/client';
import { formatMoney } from '../../lib/format';
import { HAKEDIS_BADGE, HAKEDIS_LABEL } from '../teacher/teacherDisplay';
import { DURUM_BADGE, DURUM_LABEL } from '../payout/payoutDisplay';
import { currentMonth, useTeacherPayouts } from './useReports';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

/** Öğretmen Hakediş sekmesi — YALNIZCA ADMIN. Salt okunur. */
export default function TeacherPayoutsTab() {
  const [donem, setDonem] = useState(currentMonth());
  const query = useTeacherPayouts(donem.trim());
  const data = query.data;
  const kalemler = data?.kalemler ?? [];

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3">
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-gray-700">Dönem (YYYY-MM)</span>
          <input
            className={inputClass}
            value={donem}
            placeholder="2026-06"
            onChange={(e) => setDonem(e.target.value)}
          />
        </label>
      </div>

      {!donem.trim() ? (
        <div className="card text-center text-ink-soft">Dönem girin (YYYY-MM)</div>
      ) : query.isLoading ? (
        <div className="card text-center text-ink-soft">Yükleniyor…</div>
      ) : query.isError ? (
        <div className="card text-center text-red">
          {query.error instanceof ApiException ? query.error.message : 'Bir hata oluştu'}
        </div>
      ) : !data || kalemler.length === 0 ? (
        <div className="card text-center text-ink-soft">Hakediş bulunamadı</div>
      ) : (
        <>
          <div className="card flex items-center justify-between">
            <span className="text-[13.5px] font-semibold text-ink">Toplam Hakediş</span>
            <span className="amount font-semibold">{formatMoney(data.toplamHakedis)} ₺</span>
          </div>
          <div className="card">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Öğretmen</th>
                  <th>Tip</th>
                  <th className="t-right">Tutar</th>
                  <th>Durum</th>
                </tr>
              </thead>
              <tbody>
                {kalemler.map((k) => (
                  <tr key={`${k.ogretmenId}-${k.hakedisTipi}`}>
                    <td>
                      <b>
                        {k.ad} {k.soyad}
                      </b>
                    </td>
                    <td>
                      <span className={`badge ${HAKEDIS_BADGE[k.hakedisTipi]}`}>
                        {HAKEDIS_LABEL[k.hakedisTipi]}
                      </span>
                    </td>
                    <td className="t-right">
                      <span className="amount">{formatMoney(k.hesaplananTutar)} ₺</span>
                    </td>
                    <td>
                      <span className={`badge ${DURUM_BADGE[k.durum]}`}>{DURUM_LABEL[k.durum]}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
