import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { ApiException } from '../../api/client';
import { hesaplaPayout, onizlePayout } from '../../api/payouts';
import type { CalculatePayoutInput, PayoutDurumu, PayoutResponse } from '../../api/types';
import { formatDate, formatMoney } from '../../lib/format';
import { useTeachers } from '../teacher/useTeachers';
import { HAKEDIS_BADGE, HAKEDIS_LABEL } from '../teacher/teacherDisplay';
import { DURUM_BADGE, DURUM_LABEL } from './payoutDisplay';
import { useOdePayout, usePayouts } from './usePayouts';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

const PAGE_SIZE = 20;

export default function PayoutPage() {
  return (
    <>
      <div className="topbar">
        <div>
          <h1>Hakediş</h1>
          <div className="sub">Öğretmen hakediş hesaplama ve ödeme takibi</div>
        </div>
      </div>

      <div className="space-y-4">
        <HesaplaCard />
        <PayoutList />
      </div>
    </>
  );
}

// --- Hesapla / Önizle ---

function HesaplaCard() {
  const qc = useQueryClient();
  const teachersQuery = useTeachers({ aktif: true, size: 200 });
  const teachers = teachersQuery.data?.data ?? [];

  const [ogretmenId, setOgretmenId] = useState('');
  const [donem, setDonem] = useState('');
  const [kdvOrani, setKdvOrani] = useState('');

  const [onizleme, setOnizleme] = useState<PayoutResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const canSubmit = Boolean(ogretmenId) && Boolean(donem.trim());

  function buildPayload(): CalculatePayoutInput {
    const kdv = kdvOrani.trim();
    return {
      ogretmenId: Number(ogretmenId),
      donem: donem.trim(),
      ...(kdv ? { kdvOrani: kdv } : {}),
    };
  }

  async function onOnizle() {
    setError(null);
    setBusy(true);
    try {
      const data = await onizlePayout(buildPayload());
      setOnizleme(data);
      if (data.length === 0) {
        setError('Bu öğretmen için bu dönemde hesaplanacak grup/hakediş bulunamadı.');
      }
    } catch (e) {
      setOnizleme(null);
      setError(e instanceof ApiException ? e.message : 'Önizleme başarısız oldu.');
    } finally {
      setBusy(false);
    }
  }

  async function onHesapla() {
    setError(null);
    setBusy(true);
    try {
      await hesaplaPayout(buildPayload());
      setOnizleme(null);
      setOgretmenId('');
      setDonem('');
      setKdvOrani('');
      qc.invalidateQueries({ queryKey: ['payouts'] });
    } catch (e) {
      if (e instanceof ApiException) {
        setError(
          e.code === 'CONFLICT'
            ? 'Bu öğretmen için bu dönemde bu hakediş tipi zaten hesaplanmış.'
            : e.message,
        );
      } else {
        setError('Hesaplama başarısız oldu.');
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="card space-y-3">
      <h3>Hakediş Hesapla</h3>
      <p className="text-[13px] text-ink-soft">
        <b>Önizle</b> hiçbir kayıt yazmaz; yalnızca hesaplanan hakedişi gösterir.{' '}
        <b>Hesapla ve Kaydet</b> kaydı oluşturur. KDV oranı yalnızca <b>CIRO_ORANI</b> hakedişli
        öğretmenlerde net ciroyu çıkarmak için kullanılır.
      </p>

      <div className="flex flex-wrap items-end gap-3">
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-gray-700">Öğretmen</span>
          <select
            className={inputClass}
            value={ogretmenId}
            onChange={(e) => setOgretmenId(e.target.value)}
          >
            <option value="">Seçin…</option>
            {teachers.map((t) => (
              <option key={t.id} value={t.id}>
                {t.ad} {t.soyad}
              </option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-gray-700">Dönem (YYYY-MM)</span>
          <input
            className={inputClass}
            value={donem}
            placeholder="2026-06"
            onChange={(e) => setDonem(e.target.value)}
          />
        </label>
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-gray-700">KDV Oranı (%)</span>
          <input
            className={inputClass}
            value={kdvOrani}
            placeholder="20"
            inputMode="decimal"
            onChange={(e) => setKdvOrani(e.target.value)}
          />
        </label>
        <button
          type="button"
          className="btn btn-ghost"
          onClick={onOnizle}
          disabled={busy || !canSubmit}
        >
          Önizle
        </button>
        <button
          type="button"
          className="btn btn-primary"
          onClick={onHesapla}
          disabled={busy || !canSubmit}
        >
          {busy ? 'İşleniyor…' : 'Hesapla ve Kaydet'}
        </button>
      </div>

      {error && (
        <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
          {error}
        </div>
      )}

      {onizleme && onizleme.length > 0 && (
        <div className="space-y-2">
          {onizleme.map((p) => (
            <DokumBox key={p.hakedisTipi} payout={p} />
          ))}
        </div>
      )}
    </div>
  );
}

/** Önizleme dökümü — kayıt YAZILMAMIŞTIR. Model C: tip basina bir kutu; tipe gore farkli satir. */
function DokumBox({ payout }: { payout: PayoutResponse }) {
  const { dokum } = payout;
  const isSession = payout.hakedisTipi === 'SAATLIK' || payout.hakedisTipi === 'OZEL_DERS';
  return (
    <div className="rounded-[12px] border border-line bg-paper px-4 py-3 space-y-1.5">
      <div className="flex items-center gap-2">
        <span className={`badge ${HAKEDIS_BADGE[payout.hakedisTipi]}`}>
          {HAKEDIS_LABEL[payout.hakedisTipi]}
        </span>
        <span className="badge b-amber">Önizleme — kaydedilmedi</span>
      </div>

      {isSession ? (
        <p className="text-[13.5px] text-ink">
          Ders sayısı: {dokum.dersSayisi ?? '—'} ×{' '}
          <span className="amount">{formatMoney(dokum.birimUcret)} ₺</span> ={' '}
          <span className="amount">{formatMoney(payout.hesaplananTutar)} ₺</span>
        </p>
      ) : (
        <p className="text-[13.5px] text-ink">
          Toplam tahsilat: <span className="amount">{formatMoney(dokum.toplamTahsilat)} ₺</span> · KDV
          %{formatMoney(dokum.kdvOrani)} → net ciro{' '}
          <span className="amount">{formatMoney(dokum.netCiro)} ₺</span> · oran %
          {formatMoney(dokum.oran)} →{' '}
          <span className="amount">{formatMoney(payout.hesaplananTutar)} ₺</span>
        </p>
      )}
    </div>
  );
}

// --- Liste ---

function PayoutList() {
  const [donem, setDonem] = useState('');
  const [durum, setDurum] = useState<'' | PayoutDurumu>('');
  const [page, setPage] = useState(0);

  const query = usePayouts({
    donem: donem.trim() || undefined,
    durum: durum || undefined,
    page,
    size: PAGE_SIZE,
  });
  const payouts = query.data?.data ?? [];
  const meta = query.data?.meta;

  const odeMut = useOdePayout();

  async function onOde(id: number) {
    if (!window.confirm('Hakediş ödendi olarak işaretlensin mi?')) {
      return;
    }
    await odeMut.mutateAsync(id);
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3">
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-gray-700">Dönem (YYYY-MM)</span>
          <input
            className={inputClass}
            value={donem}
            placeholder="2026-06"
            onChange={(e) => {
              setDonem(e.target.value);
              setPage(0);
            }}
          />
        </label>
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-gray-700">Durum</span>
          <select
            className={inputClass}
            value={durum}
            onChange={(e) => {
              setDurum(e.target.value as '' | PayoutDurumu);
              setPage(0);
            }}
          >
            <option value="">Tümü</option>
            <option value="HESAPLANDI">Hesaplandı</option>
            <option value="ODENDI">Ödendi</option>
          </select>
        </label>
      </div>

      {query.isLoading ? (
        <div className="card text-center text-ink-soft">Yükleniyor…</div>
      ) : query.isError ? (
        <div className="card text-center text-red">
          {query.error instanceof ApiException ? query.error.message : 'Bir hata oluştu'}
        </div>
      ) : payouts.length === 0 ? (
        <div className="card text-center text-ink-soft">Hakediş bulunamadı</div>
      ) : (
        <>
          <div className="card">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Öğretmen</th>
                  <th>Dönem</th>
                  <th>Tip</th>
                  <th className="t-right">Tutar</th>
                  <th>Durum</th>
                  <th>Ödeme Tarihi</th>
                  <th className="t-right">Aksiyon</th>
                </tr>
              </thead>
              <tbody>
                {payouts.map((p) => (
                  <tr key={p.id}>
                    <td>
                      <b>
                        {p.ogretmen.ad} {p.ogretmen.soyad}
                      </b>
                    </td>
                    <td className="text-ink-soft">{p.donem}</td>
                    <td>
                      <span className={`badge ${HAKEDIS_BADGE[p.hakedisTipi]}`}>
                        {HAKEDIS_LABEL[p.hakedisTipi]}
                      </span>
                    </td>
                    <td className="t-right">
                      <span className="amount">{formatMoney(p.hesaplananTutar)} ₺</span>
                    </td>
                    <td>
                      <span className={`badge ${DURUM_BADGE[p.durum]}`}>{DURUM_LABEL[p.durum]}</span>
                    </td>
                    <td className="text-ink-soft">{formatDate(p.odemeTarihi)}</td>
                    <td className="t-right">
                      {p.durum === 'HESAPLANDI' && (
                        <button
                          type="button"
                          className="btn btn-ghost"
                          onClick={() => onOde(p.id)}
                          disabled={odeMut.isPending}
                        >
                          Öde
                        </button>
                      )}
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
