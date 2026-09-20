import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { ApiException } from '../../api/client';
import { getKasalar } from '../../api/kasa';
import { getSatisIadeOnizleme, iadeEt } from '../../api/sales';
import { useAuth } from '../../auth/AuthContext';
import { Role } from '../../auth/roles';
import { formatMoney } from '../../lib/format';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

/**
 * "İade" düğmesi + onaylı modal (ürün iadesi, ADMIN + muhasebe). Zaten bir iade olan satırda
 * render edilmez.
 *
 * Silmeden farkı: silmek de stoğu geri ekler ama satışı defterden kaldırır; iade satışı yerinde
 * bırakıp negatif bir satır yazar ve kısmi (3 adetten 1'i) yapılabilir.
 */
export default function SatisIadeButonu({
  satisId,
  iadeEdilenSatisId,
}: {
  satisId: number;
  iadeEdilenSatisId?: number | null;
}) {
  const { hasRole } = useAuth();
  const [acik, setAcik] = useState(false);
  const yetkili = hasRole(Role.ADMIN) || hasRole(Role.FRONTDESK_ACCOUNTING);
  if (!yetkili || iadeEdilenSatisId) return null;
  return (
    <>
      <button
        type="button"
        className="btn btn-ghost"
        onClick={(e) => {
          e.stopPropagation();
          setAcik(true);
        }}
      >
        İade
      </button>
      {acik && <SatisIadeModal satisId={satisId} onClose={() => setAcik(false)} />}
    </>
  );
}

function SatisIadeModal({ satisId, onClose }: { satisId: number; onClose: () => void }) {
  const qc = useQueryClient();
  const onizleme = useQuery({
    queryKey: ['satis-iade-onizleme', satisId],
    queryFn: () => getSatisIadeOnizleme(satisId),
  });
  const kasalar = useQuery({ queryKey: ['kasalar', true], queryFn: () => getKasalar(true) });

  const [adet, setAdet] = useState('');
  const [kasaId, setKasaId] = useState('');
  const [aciklama, setAciklama] = useState('');
  const [hata, setHata] = useState<string | null>(null);
  const [alanHatasi, setAlanHatasi] = useState<string | null>(null);

  const o = onizleme.data;

  // Önizleme gelince kalan adedi varsayılan yaz (yalnız ilk gelişte).
  const [varsayilanYazildi, setVarsayilanYazildi] = useState(false);
  useEffect(() => {
    if (o && !o.engel && !varsayilanYazildi) {
      setAdet(String(o.iadeEdilebilir));
      setVarsayilanYazildi(true);
    }
  }, [o, varsayilanYazildi]);

  const mut = useMutation({
    mutationFn: () =>
      iadeEt(satisId, {
        adet: Number(adet),
        kasaId: kasaId ? Number(kasaId) : undefined,
        aciklama: aciklama.trim() || undefined,
      }),
    onSuccess: async () => {
      await qc.invalidateQueries();
      onClose();
    },
    onError: (e) => {
      if (e instanceof ApiException) {
        setAlanHatasi(e.fields?.adet ?? null);
        setHata(e.fields?.adet ? null : e.message);
      } else {
        setHata('İade kaydedilemedi.');
      }
    },
  });

  const iadeTutari = o ? Number(o.birimFiyat) * (Number(adet) || 0) : 0;

  return (
    <div
      className="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4"
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="satis-iade-baslik"
      onClick={(e) => {
        e.stopPropagation();
        onClose();
      }}
    >
      <div className="card w-full max-w-md space-y-4" onClick={(e) => e.stopPropagation()}>
        <h3 id="satis-iade-baslik">Ürün iadesi</h3>

        {onizleme.isLoading ? (
          <p className="text-[13px] text-ink-soft">Kontrol ediliyor…</p>
        ) : onizleme.isError ? (
          <p className="text-[13px] text-red">
            {onizleme.error instanceof ApiException ? onizleme.error.message : 'Önizleme alınamadı'}
          </p>
        ) : o?.engel ? (
          <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-3 text-[13px]">
            <p className="font-semibold text-red">İade yapılamaz</p>
            <p className="mt-1">{o.engel}</p>
          </div>
        ) : (
          o && (
            <>
              <div className="text-[13px] text-ink-soft">
                Satılan <b className="text-ink">{o.satilanAdet} adet</b>
                {o.iadeEdilenAdet > 0 && (
                  <> · daha önce iade edilen <b className="text-ink">{o.iadeEdilenAdet}</b></>
                )}{' '}
                · iade edilebilir <b className="text-ink">{o.iadeEdilebilir} adet</b>
              </div>

              <label className="block">
                <span className="mb-1 block text-[13px] font-semibold text-ink">İade adedi</span>
                <input
                  className={inputClass}
                  type="number"
                  step="1"
                  min="1"
                  value={adet}
                  onChange={(e) => {
                    setAdet(e.target.value);
                    setAlanHatasi(null);
                  }}
                />
                {alanHatasi && <span className="mt-1 block text-[12.5px] text-red">{alanHatasi}</span>}
              </label>

              <div className="rounded-[12px] border border-line bg-bg px-4 py-3 text-[13px]">
                Geri verilecek tutar <b>{formatMoney(iadeTutari)} ₺</b> ({formatMoney(o.birimFiyat)} ₺ ×{' '}
                {Number(adet) || 0}) — ürünün güncel fiyatı değil, <b>satın alındığı fiyat</b>.
              </div>

              <label className="block">
                <span className="mb-1 block text-[13px] font-semibold text-ink">Kasa</span>
                <select className={inputClass} value={kasaId} onChange={(e) => setKasaId(e.target.value)}>
                  <option value="">Satışın kasası</option>
                  {(kasalar.data ?? []).map((k) => (
                    <option key={k.id} value={k.id}>
                      {k.ad}
                    </option>
                  ))}
                </select>
              </label>

              <label className="block">
                <span className="mb-1 block text-[13px] font-semibold text-ink">Açıklama</span>
                <input
                  className={inputClass}
                  value={aciklama}
                  onChange={(e) => setAciklama(e.target.value)}
                  placeholder="Örn. beden uymadı"
                />
              </label>

              <p className="text-[13px] text-ink-soft">
                Satış silinmez; iade edilen adet stoğa geri eklenir ve Gelirler'den düşer.
              </p>
            </>
          )
        )}

        {hata && (
          <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
            {hata}
          </div>
        )}

        <div className="flex justify-end gap-3">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            {o?.engel ? 'Kapat' : 'Vazgeç'}
          </button>
          {o && !o.engel && (
            <button
              type="button"
              className="btn btn-primary"
              disabled={mut.isPending || !adet}
              onClick={() => mut.mutate()}
            >
              {mut.isPending ? 'İade ediliyor…' : 'İade et'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
