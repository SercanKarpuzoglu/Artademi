import { useState } from 'react';
import { ApiException } from '../../api/client';
import type { BasvuruResponse } from '../../api/types';
import { useOgrenciyeDonustur } from './useBasvurular';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none';

/**
 * Başvuruyu öğrenci kaydına dönüştürür.
 *
 * ⚠️ Neden burada ek alan isteniyor: ön kayıt formu bilinçli olarak az veri toplar, ama
 * öğrenci kaydında TC ve doğum tarihi ZORUNLUDUR; ayrıca öğrenci yetişkin değilse anne
 * VEYA baba için ad+TC gerekir (öğrenci formundaki kuralın aynısı, backend'de tek
 * validator ile zorlanır).
 */
export default function DonusturModal({
  basvuru,
  onKapat,
  onTamam,
}: {
  basvuru: BasvuruResponse;
  onKapat: () => void;
  onTamam: (ogrenciId: number) => void;
}) {
  const mutation = useOgrenciyeDonustur();
  const [hata, setHata] = useState<string | null>(null);

  const [tcKimlikNo, setTc] = useState('');
  const [dogumTarihi, setDogum] = useState('');
  const [yetiskinMi, setYetiskin] = useState(false);
  // Veli adı formdan gelmiş olabilir; anne alanına önerilir (yönetici değiştirebilir).
  const [anneAd, setAnneAd] = useState(basvuru.veliAdi ?? '');
  const [anneTc, setAnneTc] = useState('');
  const [babaAd, setBabaAd] = useState('');
  const [babaTc, setBabaTc] = useState('');

  const gonder = (e: React.FormEvent) => {
    e.preventDefault();
    setHata(null);
    mutation.mutate(
      {
        id: basvuru.id,
        payload: {
          tcKimlikNo,
          dogumTarihi,
          yetiskinMi,
          anneAd: anneAd || undefined,
          anneTcKimlikNo: anneTc || undefined,
          anneTelefon: basvuru.telefon,
          babaAd: babaAd || undefined,
          babaTcKimlikNo: babaTc || undefined,
        },
      },
      {
        onSuccess: (sonuc) => {
          if (sonuc.ogrenciId) onTamam(sonuc.ogrenciId);
          else onKapat();
        },
        onError: (e) =>
          setHata(e instanceof ApiException ? e.message : 'Öğrenci kaydı oluşturulamadı'),
      },
    );
  };

  return (
    <div
      className="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4"
      role="dialog"
      aria-modal="true"
      onClick={onKapat}
    >
      {/* Kart içine tıklamak modalı kapatmamalı (mevcut modal kalıbı — bkz. GroupDetailPage). */}
      <div
        className="card max-h-[90vh] w-full max-w-md overflow-y-auto"
        onClick={(ev) => ev.stopPropagation()}
      >
        <h2 className="mb-1 text-[16px] font-semibold">Öğrenciye Dönüştür</h2>
        <p className="mb-4 text-[12.5px] text-ink-soft">
          <b>
            {basvuru.ad} {basvuru.soyad}
          </b>{' '}
          için öğrenci kaydı açılacak. Ad, soyad ve telefon başvurudan taşınır.
        </p>

        <form onSubmit={gonder} className="space-y-3">
          <label className="block text-[13px]">
            <span className="mb-1 block text-ink-soft">TC Kimlik No *</span>
            <input
              className={inputClass}
              value={tcKimlikNo}
              onChange={(e) => setTc(e.target.value)}
              required
              maxLength={11}
              inputMode="numeric"
            />
          </label>

          <label className="block text-[13px]">
            <span className="mb-1 block text-ink-soft">Doğum Tarihi *</span>
            <input
              type="date"
              className={inputClass}
              value={dogumTarihi}
              onChange={(e) => setDogum(e.target.value)}
              required
            />
          </label>

          <label className="flex items-center gap-2 text-[13.5px]">
            <input
              type="checkbox"
              checked={yetiskinMi}
              onChange={(e) => setYetiskin(e.target.checked)}
            />
            Öğrenci yetişkin (veli bilgisi gerekmez)
          </label>

          {!yetiskinMi && (
            <div className="space-y-3 rounded-[10px] border border-line p-3">
              <p className="text-[12px] text-ink-soft">
                Anne <b>veya</b> baba için ad ve TC zorunludur.
              </p>
              <div className="flex gap-2">
                <label className="block flex-1 text-[13px]">
                  <span className="mb-1 block text-ink-soft">Anne Adı</span>
                  <input
                    className={inputClass}
                    value={anneAd}
                    onChange={(e) => setAnneAd(e.target.value)}
                  />
                </label>
                <label className="block flex-1 text-[13px]">
                  <span className="mb-1 block text-ink-soft">Anne TC</span>
                  <input
                    className={inputClass}
                    value={anneTc}
                    onChange={(e) => setAnneTc(e.target.value)}
                    maxLength={11}
                    inputMode="numeric"
                  />
                </label>
              </div>
              <div className="flex gap-2">
                <label className="block flex-1 text-[13px]">
                  <span className="mb-1 block text-ink-soft">Baba Adı</span>
                  <input
                    className={inputClass}
                    value={babaAd}
                    onChange={(e) => setBabaAd(e.target.value)}
                  />
                </label>
                <label className="block flex-1 text-[13px]">
                  <span className="mb-1 block text-ink-soft">Baba TC</span>
                  <input
                    className={inputClass}
                    value={babaTc}
                    onChange={(e) => setBabaTc(e.target.value)}
                    maxLength={11}
                    inputMode="numeric"
                  />
                </label>
              </div>
            </div>
          )}

          {hata && (
            <div className="rounded-[10px] border border-red/40 bg-red/10 px-3 py-2 text-[13px] text-red">
              {hata}
            </div>
          )}

          <div className="flex justify-end gap-2 pt-1">
            <button type="button" className="btn btn-ghost" onClick={onKapat}>
              Vazgeç
            </button>
            <button type="submit" className="btn btn-primary" disabled={mutation.isPending}>
              {mutation.isPending ? 'Oluşturuluyor…' : 'Öğrenci Kaydı Oluştur'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
