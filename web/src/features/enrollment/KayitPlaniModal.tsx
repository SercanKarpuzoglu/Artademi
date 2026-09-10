import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { getKayitOnizleme } from '../../api/kredi';
import type { KayitOnizleme, OdemePlani } from '../../api/types';
import { formatDate, formatMoney } from '../../lib/format';

/**
 * Gruba yazarken plan seçimi (Dalga E): Aylık mı, Dönemlik mi? Her seçenek o anki hesabı gösterir
 * ("Dönem 14 Eyl–31 Oca · haftada 2 ders · 22 ders · 9.000 ₺" / "Bu ay 4 ders · 2.000 ₺").
 * Ücret gösterir; çağıran gating yapmaz — kayıt yapan ön büro da ücreti bilmeli.
 */
export default function KayitPlaniModal({
  grupId,
  grupAd,
  ogrenciAd,
  pending,
  onVazgec,
  onOnayla,
}: {
  grupId: number;
  grupAd: string;
  ogrenciAd: string;
  pending: boolean;
  onVazgec: () => void;
  onOnayla: (plan: OdemePlani) => void;
}) {
  const [plan, setPlan] = useState<OdemePlani>('AYLIK');
  const aylik = useQuery({ queryKey: ['kayit-onizleme', grupId, 'AYLIK'], queryFn: () => getKayitOnizleme(grupId, 'AYLIK') });
  const donemlik = useQuery({ queryKey: ['kayit-onizleme', grupId, 'DONEMLIK'], queryFn: () => getKayitOnizleme(grupId, 'DONEMLIK') });
  const secili = plan === 'AYLIK' ? aylik.data : donemlik.data;
  const uygun = secili?.uygun ?? false;

  return (
    <div
      className="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="kayit-plani-baslik"
      onClick={onVazgec}
    >
      <div className="card w-full max-w-lg space-y-4" onClick={(e) => e.stopPropagation()}>
        <div>
          <h3 id="kayit-plani-baslik">Kayıt planı</h3>
          <p className="text-[13px] text-ink-soft">
            <b>{ogrenciAd}</b> → <b>{grupAd}</b>. Plan, krediyi (ders sayısı) ve tahakkuku belirler.
          </p>
        </div>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <PlanKarti baslik="Aylık" aciklama="Her ay aidat tahakkuku; o ayın dersleri kadar kredi" secili={plan === 'AYLIK'} veri={aylik.data} yukleniyor={aylik.isLoading} onSec={() => setPlan('AYLIK')} />
          <PlanKarti baslik="Dönemlik" aciklama="Dönem ücreti tek tahakkuk; dönemdeki tüm dersler kredi" secili={plan === 'DONEMLIK'} veri={donemlik.data} yukleniyor={donemlik.isLoading} onSec={() => setPlan('DONEMLIK')} />
        </div>
        <div className="flex justify-end gap-3">
          <button type="button" className="btn btn-ghost" onClick={onVazgec}>
            Vazgeç
          </button>
          <button type="button" className="btn btn-primary" disabled={pending || !uygun} onClick={() => onOnayla(plan)}>
            {pending ? 'Kaydediliyor…' : plan === 'AYLIK' ? 'Aylık kaydet' : 'Dönemlik kaydet'}
          </button>
        </div>
      </div>
    </div>
  );
}

function PlanKarti({
  baslik,
  aciklama,
  secili,
  veri,
  yukleniyor,
  onSec,
}: {
  baslik: string;
  aciklama: string;
  secili: boolean;
  veri: KayitOnizleme | undefined;
  yukleniyor: boolean;
  onSec: () => void;
}) {
  const uygun = veri?.uygun ?? false;
  return (
    <button
      type="button"
      role="radio"
      aria-checked={secili}
      onClick={onSec}
      disabled={!yukleniyor && !uygun}
      className={`rounded-[12px] border-[1.5px] p-3 text-left transition ${
        secili ? 'border-rasp bg-rasp/5' : 'border-line bg-card'
      } disabled:cursor-not-allowed disabled:opacity-60`}
    >
      <div className="flex items-center justify-between">
        <b className="text-[14px]">{baslik}</b>
        {veri?.uygun && <span className="amount text-[15px] font-semibold text-rasp">{formatMoney(veri.ucret ?? 0)} ₺</span>}
      </div>
      <p className="mt-0.5 text-[12.5px] text-ink-soft">{aciklama}</p>
      {yukleniyor ? (
        <p className="mt-2 text-[12.5px] text-ink-soft">Hesaplanıyor…</p>
      ) : veri?.uygun ? (
        <p className="mt-2 text-[12.5px]">
          {veri.donemAd ? `${veri.donemAd} · ` : 'Bu ay · '}
          {veri.baslangic && formatDate(veri.baslangic)}–{veri.bitis && formatDate(veri.bitis)} · haftada {veri.haftalikDers} ders ·{' '}
          <b>{veri.dersSayisi} ders</b>
          {veri.dersSayisi === 0 && <span className="text-red"> (grubun ders saati yok)</span>}
        </p>
      ) : (
        <p className="mt-2 text-[12.5px] text-red">{veri?.neden ?? 'Hesaplanamadı'}</p>
      )}
    </button>
  );
}
