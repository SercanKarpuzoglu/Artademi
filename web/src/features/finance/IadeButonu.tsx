import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { ApiException } from '../../api/client';
import { getKasalar } from '../../api/kasa';
import { getIadeOnizleme, iadeEt } from '../../api/payments';
import { useAuth } from '../../auth/AuthContext';
import { Role } from '../../auth/roles';
import { formatMoney } from '../../lib/format';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

/**
 * "İade" düğmesi + onaylı modal (tahsilat iadesi, ADMIN + muhasebe; diğer roller için hiç render
 * edilmez). Zaten bir iade olan satırda da render edilmez — iadenin iadesi yoktur.
 *
 * İptalden farkı: iade kaydı SİLMEZ, negatif tutarlı yeni bir satır yazar. Para alındı VE geri
 * verildi; ikisi de defterde kalır.
 */
export default function IadeButonu({
  odemeId,
  iadeEdilenOdemeId,
}: {
  odemeId: number;
  iadeEdilenOdemeId?: number | null;
}) {
  const { hasRole } = useAuth();
  const [acik, setAcik] = useState(false);
  const yetkili = hasRole(Role.ADMIN) || hasRole(Role.FRONTDESK_ACCOUNTING);
  if (!yetkili || iadeEdilenOdemeId) return null;
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
      {acik && <IadeModal odemeId={odemeId} onClose={() => setAcik(false)} />}
    </>
  );
}

function IadeModal({ odemeId, onClose }: { odemeId: number; onClose: () => void }) {
  const qc = useQueryClient();
  const onizleme = useQuery({
    queryKey: ['iade-onizleme', odemeId],
    queryFn: () => getIadeOnizleme(odemeId),
  });
  const kasalar = useQuery({ queryKey: ['kasalar', true], queryFn: () => getKasalar(true) });

  const [tutar, setTutar] = useState('');
  const [kasaId, setKasaId] = useState('');
  const [aciklama, setAciklama] = useState('');
  const [hata, setHata] = useState<string | null>(null);
  const [alanHatasi, setAlanHatasi] = useState<string | null>(null);

  const o = onizleme.data;

  // Önizleme gelince kalan tutarı varsayılan yaz: en sık yapılan iş tamamını iade etmek.
  // Yalnız ilk gelişte yazılır; kullanıcı tutarı değiştirdiyse üstüne yazılmaz.
  const [varsayilanYazildi, setVarsayilanYazildi] = useState(false);
  useEffect(() => {
    if (o && !o.engel && !varsayilanYazildi) {
      setTutar(String(o.iadeEdilebilir));
      setVarsayilanYazildi(true);
    }
  }, [o, varsayilanYazildi]);

  const mut = useMutation({
    mutationFn: () =>
      iadeEt(odemeId, {
        tutar,
        kasaId: kasaId ? Number(kasaId) : undefined,
        aciklama: aciklama.trim() || undefined,
      }),
    onSuccess: async () => {
      await qc.invalidateQueries();
      onClose();
    },
    onError: (e) => {
      if (e instanceof ApiException) {
        setAlanHatasi(e.fields?.tutar ?? null);
        setHata(e.fields?.tutar ? null : e.message);
      } else {
        setHata('İade kaydedilemedi.');
      }
    },
  });

  return (
    <div
      className="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4"
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="iade-baslik"
      onClick={(e) => {
        e.stopPropagation();
        onClose();
      }}
    >
      <div className="card w-full max-w-md space-y-4" onClick={(e) => e.stopPropagation()}>
        <h3 id="iade-baslik">Tahsilat iadesi</h3>

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
                Tahsil edilen <b className="text-ink">{formatMoney(o.odenenTutar)} ₺</b>
                {Number(o.iadeEdilenTutar) > 0 && (
                  <> · daha önce iade edilen <b className="text-ink">{formatMoney(o.iadeEdilenTutar)} ₺</b></>
                )}{' '}
                · iade edilebilir <b className="text-ink">{formatMoney(o.iadeEdilebilir)} ₺</b>
              </div>

              {o.iptalEdilecekKontor > 0 && (
                <div className="rounded-[12px] border border-amber/40 bg-amber-soft px-4 py-3 text-[13px]">
                  <p className="font-semibold text-amber">İade edilince</p>
                  <p className="mt-1">
                    Öğrencinin kalan <b>{o.iptalEdilecekKontor} kontörü</b> ({o.iptalEdilecekPaket} paket)
                    iptal edilir — kısmi iadede de tamamı gider. Gerekirse sonradan Ders Paketi
                    ekranından yeniden satabilirsiniz.
                  </p>
                </div>
              )}

              <label className="block">
                <span className="mb-1 block text-[13px] font-semibold text-ink">İade tutarı (₺)</span>
                <input
                  className={inputClass}
                  type="number"
                  step="0.01"
                  min="0"
                  value={tutar}
                  onChange={(e) => {
                    setTutar(e.target.value);
                    setAlanHatasi(null);
                  }}
                />
                {alanHatasi && <span className="mt-1 block text-[12.5px] text-red">{alanHatasi}</span>}
              </label>

              <label className="block">
                <span className="mb-1 block text-[13px] font-semibold text-ink">Kasa</span>
                <select className={inputClass} value={kasaId} onChange={(e) => setKasaId(e.target.value)}>
                  <option value="">Tahsilatın kasası</option>
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
                  placeholder="Örn. veli kaydını iptal etti"
                />
              </label>

              <p className="text-[13px] text-ink-soft">
                Tahsilat silinmez: defterde hem alınan hem geri verilen para görünür. İade satırı
                öğrenci bakiyesine, kasaya ve Gelirler'e eksi olarak yansır.
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
              disabled={mut.isPending || !tutar}
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
