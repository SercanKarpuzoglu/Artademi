import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ApiException } from '../api/client';
import { sil, silmeOnizle, type SilinebilirTur } from '../api/silme';
import { useAuth } from '../auth/AuthContext';
import { Role } from '../auth/roles';

/**
 * "Sil" düğmesi + uyarılı onay modalı (yumuşak silme, yalnız yönetici; diğer roller için hiç render
 * edilmez). Modal önce önizlemeyi çeker: engel varsa silme kapalı; yoksa etkiler ve bağlı kayıtlar
 * listelenir. Başarıda tüm sorgular tazelenir; sayfa detaysa {@code onSilindi} ile listeye dönülür.
 */
export default function SilButonu({
  tur,
  id,
  ad,
  onSilindi,
  className,
}: {
  tur: SilinebilirTur;
  id: number;
  ad: string;
  onSilindi?: () => void;
  className?: string;
}) {
  const { hasRole } = useAuth();
  const [acik, setAcik] = useState(false);
  if (!hasRole(Role.ADMIN)) return null;
  return (
    <>
      <button
        type="button"
        className={className ?? 'btn btn-ghost text-red'}
        onClick={(e) => {
          e.stopPropagation();
          setAcik(true);
        }}
      >
        Sil
      </button>
      {acik && (
        <SilOnayModal
          tur={tur}
          id={id}
          ad={ad}
          onClose={() => setAcik(false)}
          onSilindi={onSilindi}
        />
      )}
    </>
  );
}

function SilOnayModal({
  tur,
  id,
  ad,
  onClose,
  onSilindi,
}: {
  tur: SilinebilirTur;
  id: number;
  ad: string;
  onClose: () => void;
  onSilindi?: () => void;
}) {
  const qc = useQueryClient();
  const onizleme = useQuery({
    queryKey: ['silme-onizleme', tur, id],
    queryFn: () => silmeOnizle(tur, id),
  });
  const [hata, setHata] = useState<string | null>(null);
  const mut = useMutation({
    mutationFn: () => sil(tur, id),
    onSuccess: async () => {
      await qc.invalidateQueries();
      onClose();
      onSilindi?.();
    },
    onError: (e) => setHata(e instanceof ApiException ? e.message : 'Silinemedi.'),
  });
  const o = onizleme.data;

  return (
    <div
      className="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4"
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="sil-baslik"
      onClick={(e) => {
        e.stopPropagation();
        onClose();
      }}
    >
      <div className="card w-full max-w-md space-y-4" onClick={(e) => e.stopPropagation()}>
        <h3 id="sil-baslik">{ad} silinsin mi?</h3>

        {onizleme.isLoading ? (
          <p className="text-[13px] text-ink-soft">Bağlı kayıtlar kontrol ediliyor…</p>
        ) : onizleme.isError ? (
          <p className="text-[13px] text-red">
            {onizleme.error instanceof ApiException ? onizleme.error.message : 'Önizleme alınamadı'}
          </p>
        ) : o && !o.silinebilir ? (
          <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-3 text-[13px]">
            <p className="font-semibold text-red">Silinemez</p>
            <p className="mt-1">{o.engel}</p>
          </div>
        ) : (
          o && (
            <>
              {o.etkiler.length > 0 && (
                <div className="rounded-[12px] border border-amber/40 bg-amber-soft px-4 py-3 text-[13px]">
                  <p className="font-semibold text-amber">Silinince</p>
                  <ul className="mt-1 list-disc pl-5">
                    {o.etkiler.map((e) => (
                      <li key={e}>{e}</li>
                    ))}
                  </ul>
                </div>
              )}
              {o.bagliKayitlar.length > 0 && (
                <div className="text-[13px] text-ink-soft">
                  <p className="font-semibold text-ink">Bağlı kayıtlar (silinmez, gizli kalır)</p>
                  <ul className="mt-1 flex flex-wrap gap-1.5">
                    {o.bagliKayitlar.map((b) => (
                      <li key={b.ad} className="badge b-gray">
                        {b.ad}: {b.sayi}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              <p className="text-[13px] text-ink-soft">
                Kayıt listelerden kalkar, İşlem Kaydı'na "silindi" olarak düşer. Yanlışlıkla silinirse
                <b> Sistem → Silinenler</b>'den geri alınabilir.
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
            {o && !o.silinebilir ? 'Kapat' : 'Vazgeç'}
          </button>
          {o?.silinebilir && (
            <button
              type="button"
              className="btn btn-primary"
              disabled={mut.isPending}
              onClick={() => mut.mutate()}
            >
              {mut.isPending ? 'Siliniyor…' : 'Sil'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
