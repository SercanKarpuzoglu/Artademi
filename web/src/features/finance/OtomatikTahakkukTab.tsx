import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiException } from '../../api/client';
import type { AccrualGenerationResult } from '../../api/types';
import { formatMoney } from '../../lib/format';
import { useUret, useUretOnizle } from './useFinance';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

/**
 * Otomatik aylik tahakkuk (YALNIZCA ADMIN). Önizle KAYIT YAZMAZ; Üret kayit olusturur (idempotent).
 */
export default function OtomatikTahakkukTab() {
  const [donem, setDonem] = useState('');
  const [result, setResult] = useState<{ data: AccrualGenerationResult; yazildi: boolean } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const onizleMut = useUretOnizle();
  const uretMut = useUret();
  const busy = onizleMut.isPending || uretMut.isPending;

  async function onOnizle() {
    setError(null);
    try {
      const data = await onizleMut.mutateAsync(donem.trim());
      setResult({ data, yazildi: false });
    } catch (e) {
      setResult(null);
      setError(e instanceof ApiException ? e.message : 'Önizleme başarısız oldu.');
    }
  }

  async function onUret() {
    setError(null);
    try {
      const data = await uretMut.mutateAsync(donem.trim());
      setResult({ data, yazildi: true });
    } catch (e) {
      setResult(null);
      setError(e instanceof ApiException ? e.message : 'Üretim başarısız oldu.');
    }
  }

  return (
    <div className="space-y-4">
      <div className="card space-y-3">
        <h3>Otomatik Aylık Tahakkuk</h3>
        <p className="text-[13px] text-ink-soft">
          <b>Önizle</b> hiçbir kayıt yazmaz; yalnızca üretilecek tahakkukları gösterir.{' '}
          <b>Üret</b> kayıtları oluşturur (idempotent — aynı dönem için tekrar çalıştırmak yeni kayıt
          eklemez, mevcutları atlar).
        </p>
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
          <button
            type="button"
            className="btn btn-ghost"
            onClick={onOnizle}
            disabled={busy || !donem.trim()}
          >
            {onizleMut.isPending ? 'Hesaplanıyor…' : 'Önizle'}
          </button>
          <button
            type="button"
            className="btn btn-primary"
            onClick={onUret}
            disabled={busy || !donem.trim()}
          >
            {uretMut.isPending ? 'Üretiliyor…' : 'Üret'}
          </button>
        </div>
        {error && (
          <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
            {error}
          </div>
        )}
      </div>

      {result && (
        <div className="card space-y-3">
          <div className="flex items-center gap-3">
            <h3>{result.yazildi ? 'Üretim Sonucu' : 'Önizleme'}</h3>
            <span className={`badge ${result.yazildi ? 'b-green' : 'b-amber'}`}>
              {result.yazildi ? 'Yazıldı' : 'Yazılmadı'}
            </span>
          </div>
          <dl className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-3">
            <div>
              <dt className="text-xs text-gray-500">Dönem</dt>
              <dd className="text-sm text-gray-800">{result.data.donem}</dd>
            </div>
            <div>
              <dt className="text-xs text-gray-500">{result.yazildi ? 'Üretilen' : 'Üretilecek'}</dt>
              <dd className="text-sm text-gray-800">{result.data.uretilenSayisi}</dd>
            </div>
            <div>
              <dt className="text-xs text-gray-500">Atlanan</dt>
              <dd className="text-sm text-gray-800">{result.data.atlananSayisi}</dd>
            </div>
            <div>
              <dt className="text-xs text-gray-500">Toplam Tutar</dt>
              <dd className="text-sm text-gray-800">
                <span className="amount">{formatMoney(result.data.toplamTutar)} ₺</span>
              </dd>
            </div>
          </dl>

          {result.data.atlananDenemeOgrenciler.length > 0 && (
            <div className="rounded-[12px] border border-amber/40 bg-amber-soft px-4 py-3 text-[13px]">
              <p className="font-semibold text-amber">
                {result.data.atlananDenemeOgrenciler.length} deneme öğrencisi aidat almayacak
              </p>
              <p className="mt-1 text-ink-soft">
                Bu öğrenciler aidatlı bir gruba kayıtlı ama statüleri <b>Deneme</b>; tahakkuk
                üretilmedi. Deneme dersi bittiyse öğrenciyi <b>Aktif</b> yapıp bu dönemi tekrar
                üretin (mevcut kayıtlar atlanır, yalnız eksikler eklenir).
              </p>
              <ul className="mt-2 space-y-1">
                {result.data.atlananDenemeOgrenciler.map((d) => (
                  <li key={`${d.ogrenciId}-${d.grupId}`} className="flex flex-wrap items-center gap-2">
                    <span>
                      <b>
                        {d.ad} {d.soyad}
                      </b>{' '}
                      <span className="text-ink-soft">· {d.grupAd}</span>
                    </span>
                    <Link to={`/ogrenciler/${d.ogrenciId}/duzenle`} className="text-rasp underline">
                      Aktif yap
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {result.data.ozet.length > 0 && (
            <table className="data-table">
              <thead>
                <tr>
                  <th>Öğrenci ID</th>
                  <th>Grup ID</th>
                  <th className="t-right">Brüt</th>
                  <th className="t-right">İndirim</th>
                  <th className="t-right">Net</th>
                </tr>
              </thead>
              <tbody>
                {result.data.ozet.map((o, i) => (
                  <tr key={`${o.ogrenciId}-${o.grupId}-${i}`}>
                    <td>{o.ogrenciId}</td>
                    <td>{o.grupId}</td>
                    <td className="t-right text-ink-soft">
                      <span className="amount">{formatMoney(o.brut)} ₺</span>
                    </td>
                    <td className="t-right" title={o.indirimAciklama ?? undefined}>
                      {o.indirim !== null && o.indirim !== undefined && Number(o.indirim) > 0 ? (
                        <span className="amount text-green">− {formatMoney(o.indirim)} ₺</span>
                      ) : (
                        <span className="text-ink-soft">—</span>
                      )}
                    </td>
                    <td className="t-right">
                      <span className="amount">{formatMoney(o.tutar)} ₺</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  );
}
