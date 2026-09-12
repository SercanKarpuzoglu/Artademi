import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { getKayitOnizleme } from '../../api/kredi';
import type { KayitOnizleme, OdemePlani } from '../../api/types';
import { formatDate, formatMoney } from '../../lib/format';

/**
 * Gruba yazarken plan seçimi: Aylık / Dönemlik / Deneme dersi. Aylık ya da Dönemlik seçilince öğrenci
 * AKTİF olur, kredi + tahakkuk açılır; Deneme dersi seçilince Deneme kalır, para yok, yoklama alınır
 * (sonra "Plana geçir"). Her seçenek o anki hesabı gösterir. Ücret gösterir; çağıran gating yapmaz —
 * kayıt yapan ön büro da ücreti bilmeli. {@code denemeSecenegi=false} plana geçirme modudur.
 */
export default function KayitPlaniModal({
  grupId,
  grupAd,
  ogrenciAd,
  pending,
  denemeSecenegi = true,
  onVazgec,
  onOnayla,
}: {
  grupId: number;
  grupAd: string;
  ogrenciAd: string;
  pending: boolean;
  /** Yeni kayıtta true (üç seçenek); deneme kaydını plana geçirirken false (yalnız Aylık/Dönemlik). */
  denemeSecenegi?: boolean;
  onVazgec: () => void;
  onOnayla: (plan: OdemePlani) => void;
}) {
  const [plan, setPlan] = useState<OdemePlani>('AYLIK');
  const aylik = useQuery({ queryKey: ['kayit-onizleme', grupId, 'AYLIK'], queryFn: () => getKayitOnizleme(grupId, 'AYLIK') });
  const donemlik = useQuery({ queryKey: ['kayit-onizleme', grupId, 'DONEMLIK'], queryFn: () => getKayitOnizleme(grupId, 'DONEMLIK') });
  const secili = plan === 'AYLIK' ? aylik.data : plan === 'DONEMLIK' ? donemlik.data : undefined;
  const uygun = plan === 'DENEME' ? true : (secili?.uygun ?? false);

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
          <h3 id="kayit-plani-baslik">{denemeSecenegi ? 'Kayıt planı' : 'Plana geçir'}</h3>
          <p className="text-[13px] text-ink-soft">
            <b>{ogrenciAd}</b> → <b>{grupAd}</b>. Aylık ya da Dönemlik seçilince öğrenci <b>Aktif</b> olur;
            kredi ve tahakkuk plana göre açılır.
          </p>
        </div>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <PlanKarti baslik="Aylık" aciklama="Her ay aidat tahakkuku; o ayın dersleri kadar kredi" secili={plan === 'AYLIK'} veri={aylik.data} yukleniyor={aylik.isLoading} onSec={() => setPlan('AYLIK')} />
          <PlanKarti baslik="Dönemlik" aciklama="Dönem ücreti tek tahakkuk; dönemdeki tüm dersler kredi" secili={plan === 'DONEMLIK'} veri={donemlik.data} yukleniyor={donemlik.isLoading} onSec={() => setPlan('DONEMLIK')} />
          {denemeSecenegi && (
            <button
              type="button"
              role="radio"
              aria-checked={plan === 'DENEME'}
              onClick={() => setPlan('DENEME')}
              className={`rounded-[12px] border-[1.5px] p-3 text-left transition sm:col-span-2 ${
                plan === 'DENEME' ? 'border-rasp bg-rasp/5' : 'border-line bg-card'
              }`}
            >
              <div className="flex items-center justify-between">
                <b className="text-[14px]">Deneme dersi</b>
                <span className="badge b-amber">Ücretsiz</span>
              </div>
              <p className="mt-0.5 text-[12.5px] text-ink-soft">
                Para ve kredi yok; öğrenci <b>Deneme</b> statüsünde kalır, yoklaması alınır. Karar verince
                kayıt satırından "Plana geçir" ile Aylık/Dönemlik'e çevrilir.
              </p>
            </button>
          )}
        </div>
        <div className="flex justify-end gap-3">
          <button type="button" className="btn btn-ghost" onClick={onVazgec}>
            Vazgeç
          </button>
          <button type="button" className="btn btn-primary" disabled={pending || !uygun} onClick={() => onOnayla(plan)}>
            {pending
              ? 'Kaydediliyor…'
              : plan === 'AYLIK'
                ? 'Aylık kaydet'
                : plan === 'DONEMLIK'
                  ? 'Dönemlik kaydet'
                  : 'Deneme dersi olarak kaydet'}
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
