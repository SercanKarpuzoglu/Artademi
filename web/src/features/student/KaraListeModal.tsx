import { useState } from 'react';
import { ApiException } from '../../api/client';
import { formatDateTime } from '../../lib/format';
import { useKaraListe } from './useKaraListe';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

export interface KaraListeHedef {
  id: number;
  ad: string;
  soyad: string;
  karaListe: boolean;
  karaListeAciklama: string | null;
  karaListeTarihi?: string | null;
  karaListeEkleyen?: string | null;
}

/**
 * Kara listeye alma (açıklama zorunlu) / çıkarma onayı. Öğrenci silinmez, statüsü değişmez;
 * gruba yazılırken uyarı çıkar ve açıklama gösterilir.
 */
export default function KaraListeModal({
  hedef,
  onClose,
}: {
  hedef: KaraListeHedef;
  onClose: () => void;
}) {
  const mut = useKaraListe(hedef.id);
  const [aciklama, setAciklama] = useState('');
  const [hata, setHata] = useState<string | null>(null);
  const ekleme = !hedef.karaListe;

  async function onay() {
    setHata(null);
    if (ekleme && !aciklama.trim()) {
      setHata('Neden kara listeye alındığını yazın; gruba yazılırken bu metin gösterilecek.');
      return;
    }
    try {
      await mut.mutateAsync(
        ekleme ? { karaListe: true, aciklama: aciklama.trim() } : { karaListe: false },
      );
      onClose();
    } catch (e) {
      setHata(e instanceof ApiException ? e.message : 'Beklenmeyen bir hata oluştu.');
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="kara-liste-baslik"
      onClick={onClose}
    >
      <div className="card w-full max-w-md space-y-4" onClick={(e) => e.stopPropagation()}>
        <h3 id="kara-liste-baslik">
          {ekleme ? 'Kara listeye al' : 'Kara listeden çıkar'} — {hedef.ad} {hedef.soyad}
        </h3>
        {ekleme ? (
          <>
            <p className="text-[13px] text-ink-soft">
              Öğrenci silinmez ve statüsü değişmez. Bir gruba yazılmak istendiğinde uyarı çıkar ve
              buraya yazdığınız açıklama gösterilir.
            </p>
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-ink">
                Açıklama <span className="text-red">*</span>
              </span>
              <textarea
                className={inputClass}
                rows={3}
                value={aciklama}
                onChange={(e) => setAciklama(e.target.value)}
                placeholder="Örn. iki dönem ödeme yapmadan ayrıldı"
              />
            </label>
          </>
        ) : (
          <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-3 text-[13px]">
            <p className="font-semibold text-red">Kara liste sebebi</p>
            <p className="mt-1">{hedef.karaListeAciklama ?? '—'}</p>
            {(hedef.karaListeTarihi || hedef.karaListeEkleyen) && (
              <p className="mt-1 text-ink-soft">
                {hedef.karaListeTarihi ? formatDateTime(hedef.karaListeTarihi) : ''}
                {hedef.karaListeEkleyen ? ` · ${hedef.karaListeEkleyen}` : ''}
              </p>
            )}
          </div>
        )}
        {hata && (
          <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
            {hata}
          </div>
        )}
        <div className="flex justify-end gap-3">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Vazgeç
          </button>
          <button type="button" className="btn btn-primary" disabled={mut.isPending} onClick={onay}>
            {mut.isPending ? 'Kaydediliyor…' : ekleme ? 'Kara listeye al' : 'Listeden çıkar'}
          </button>
        </div>
      </div>
    </div>
  );
}
