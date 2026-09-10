import { useState } from 'react';
import { ApiException } from '../../api/client';
import { formatMoney } from '../../lib/format';
import SalesTab from '../inventory/SalesTab';
import OdemeTab from './OdemeTab';
import { useGelirOzeti } from './useFinance';

const inputClass =
  'rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

/** "YYYY-MM" -> ayın ilk ve son günü (YYYY-MM-DD). */
function ayAraligi(donem: string): { from: string; to: string } {
  const [y, m] = donem.split('-').map(Number);
  const son = new Date(y, m, 0).getDate();
  return { from: `${donem}-01`, to: `${donem}-${String(son).padStart(2, '0')}` };
}

/**
 * Gelirler (eski adıyla Ödemeler): üstte seçilen ayın gelir özeti (öğrenci ödemeleri + ürün satışları),
 * altta iki liste. Ürün satış gelirleri Stok/Satış'tan buraya da yansır (Dalga A).
 */
export default function GelirTab() {
  const [donem, setDonem] = useState(() => new Date().toISOString().slice(0, 7));
  const [alt, setAlt] = useState<'odeme' | 'satis'>('odeme');
  const { from, to } = ayAraligi(donem);
  const ozet = useGelirOzeti(from, to);

  return (
    <div className="space-y-4">
      <div className="card space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h3>Gelir Özeti</h3>
          <label className="flex items-center gap-2 text-[13px] text-ink-soft">
            Ay
            <input
              type="month"
              className={inputClass}
              value={donem}
              onChange={(e) => e.target.value && setDonem(e.target.value)}
            />
          </label>
        </div>
        {ozet.isError ? (
          <p className="text-sm text-red">
            {ozet.error instanceof ApiException ? ozet.error.message : 'Özet yüklenemedi'}
          </p>
        ) : (
          <dl className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-3">
            <Rakam
              label="Öğrenci ödemeleri"
              value={ozet.data ? formatMoney(ozet.data.odemeToplam) : '…'}
            />
            <Rakam label="Ürün satışları" value={ozet.data ? formatMoney(ozet.data.satisToplam) : '…'} />
            <Rakam label="Toplam gelir" value={ozet.data ? formatMoney(ozet.data.toplam) : '…'} vurgu />
          </dl>
        )}
      </div>

      <div className="tabs">
        <button
          type="button"
          className={`tab${alt === 'odeme' ? ' active' : ''}`}
          onClick={() => setAlt('odeme')}
        >
          Öğrenci Ödemeleri
        </button>
        <button
          type="button"
          className={`tab${alt === 'satis' ? ' active' : ''}`}
          onClick={() => setAlt('satis')}
        >
          Ürün Satışları
        </button>
      </div>

      {alt === 'odeme' ? <OdemeTab /> : <SalesTab />}
    </div>
  );
}

function Rakam({ label, value, vurgu }: { label: string; value: string; vurgu?: boolean }) {
  return (
    <div>
      <dt className="text-xs text-gray-500">{label}</dt>
      <dd className={`amount text-[18px] ${vurgu ? 'font-semibold text-rasp' : 'text-ink'}`}>{value} ₺</dd>
    </div>
  );
}
