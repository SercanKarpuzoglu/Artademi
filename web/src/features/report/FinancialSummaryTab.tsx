import { useState } from 'react';
import { ApiException } from '../../api/client';
import { formatMoney } from '../../lib/format';
import { currentMonth, useFinancialSummary } from './useReports';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

/** Finansal Özet sekmesi — YALNIZCA ADMIN (üst seviyede gating yapilir; uç de 403 verir). */
export default function FinancialSummaryTab() {
  const [donem, setDonem] = useState(currentMonth());
  const query = useFinancialSummary(donem.trim());
  const data = query.data;

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
      ) : !data ? (
        <div className="card text-center text-ink-soft">Kayıt bulunamadı</div>
      ) : (
        <>
          <div className="grid stats">
            <div className="card stat">
              <div className="label">Toplam Gelir</div>
              <div className="value text-green">
                <span className="amount">{formatMoney(data.gelir.toplamGelir)} ₺</span>
              </div>
            </div>
            <div className="card stat">
              <div className="label">Toplam Gider</div>
              <div className="value text-red">
                <span className="amount">{formatMoney(data.gider.toplamGider)} ₺</span>
              </div>
            </div>
            <div className="card stat">
              <div className="label">Net</div>
              <div className={`value ${Number(data.net) >= 0 ? 'text-green' : 'text-red'}`}>
                <span className="amount">{formatMoney(data.net)} ₺</span>
              </div>
            </div>
          </div>

          <div className="card space-y-2">
            <h3>Dağılım — {data.donem}</h3>
            <Row label="Tahsilat" value={data.gelir.tahsilat} />
            <Row label="Ürün Satışı" value={data.gelir.urunSatis} />
            <Row label="Toplam Gelir" value={data.gelir.toplamGelir} strong className="text-green" />
            <div className="h-px bg-line" />
            <Row label="Ofis Gideri" value={data.gider.ofisGideri} />
            <Row label="Hakediş" value={data.gider.hakedis} />
            <Row label="Toplam Gider" value={data.gider.toplamGider} strong className="text-red" />
            <div className="h-px bg-line" />
            <Row
              label="Net"
              value={data.net}
              strong
              className={Number(data.net) >= 0 ? 'text-green' : 'text-red'}
            />
          </div>
        </>
      )}
    </div>
  );
}

function Row({
  label,
  value,
  strong,
  className,
}: {
  label: string;
  value: string | number;
  strong?: boolean;
  className?: string;
}) {
  return (
    <div className="flex items-center justify-between text-[13.5px]">
      <span className={strong ? 'font-semibold text-ink' : 'text-ink-soft'}>{label}</span>
      <span className={`amount ${strong ? 'font-semibold' : ''} ${className ?? ''}`}>
        {formatMoney(value)} ₺
      </span>
    </div>
  );
}
