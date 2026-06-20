import { useState } from 'react';
import { ApiException } from '../../api/client';
import { formatMoney } from '../../lib/format';
import { useStudentBalances } from './useReports';

const PAGE_SIZE = 20;

/** Öğrenci Borç sekmesi — ADMIN + FRONTDESK_ACCOUNTING. Salt okunur. */
export default function StudentBalancesTab() {
  const [sadeceBorclu, setSadeceBorclu] = useState(false);
  const [page, setPage] = useState(0);

  const query = useStudentBalances({ sadeceBorclu, page, size: PAGE_SIZE });
  const rows = query.data?.data ?? [];
  const meta = query.data?.meta;

  return (
    <div className="space-y-4">
      <label className="flex items-center gap-2 text-[13.5px] text-ink">
        <input
          type="checkbox"
          checked={sadeceBorclu}
          onChange={(e) => {
            setSadeceBorclu(e.target.checked);
            setPage(0);
          }}
        />
        Yalnızca borçlu öğrenciler
      </label>

      {query.isLoading ? (
        <div className="card text-center text-ink-soft">Yükleniyor…</div>
      ) : query.isError ? (
        <div className="card text-center text-red">
          {query.error instanceof ApiException ? query.error.message : 'Bir hata oluştu'}
        </div>
      ) : rows.length === 0 ? (
        <div className="card text-center text-ink-soft">Kayıt bulunamadı</div>
      ) : (
        <>
          <div className="card">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Öğrenci</th>
                  <th className="t-right">Toplam Tahakkuk</th>
                  <th className="t-right">Toplam Ödeme</th>
                  <th className="t-right">Bakiye</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.ogrenciId}>
                    <td>
                      <b>
                        {r.ad} {r.soyad}
                      </b>
                    </td>
                    <td className="t-right">
                      <span className="amount">{formatMoney(r.toplamTahakkuk)} ₺</span>
                    </td>
                    <td className="t-right">
                      <span className="amount">{formatMoney(r.toplamOdeme)} ₺</span>
                    </td>
                    <td className="t-right">
                      <span
                        className={`amount ${Number(r.bakiye) > 0 ? 'text-red' : 'text-ink-soft'}`}
                      >
                        {formatMoney(r.bakiye)} ₺
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {meta && (
            <div className="flex items-center justify-between text-[13px] text-ink-soft">
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
    </div>
  );
}
