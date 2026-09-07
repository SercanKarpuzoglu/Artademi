import { useState } from 'react';
import { ApiException } from '../../api/client';
import { useBasvuruSlug, useTenant } from './useBasvurular';

/** Paylaşılacak tam adres — kurum bunu web sitesine/sosyal medyaya koyar. */
function tamAdres(slug: string): string {
  return `${window.location.origin}/basvuru/${slug}`;
}

/**
 * Kurumun public ön kayıt bağlantısını yönetir (YALNIZCA ADMIN).
 *
 * Bağlantı adı boş bırakılırsa özellik kapanır ve form 404 döner — kurum istediğinde
 * dışarıya açılan bu yüzü kapatabilmelidir.
 */
export default function BaglantiKarti() {
  const tenant = useTenant();
  const mutation = useBasvuruSlug();
  const [duzenleniyor, setDuzenleniyor] = useState(false);
  const [slug, setSlug] = useState('');
  const [hata, setHata] = useState<string | null>(null);
  const [kopyalandi, setKopyalandi] = useState(false);

  const mevcut = tenant.data?.basvuruSlug ?? null;

  const kaydet = () => {
    setHata(null);
    mutation.mutate(slug.trim() || null, {
      onSuccess: () => setDuzenleniyor(false),
      onError: (e) =>
        setHata(e instanceof ApiException ? e.message : 'Bağlantı adı kaydedilemedi'),
    });
  };

  const kopyala = async () => {
    if (!mevcut) return;
    try {
      await navigator.clipboard.writeText(tamAdres(mevcut));
      setKopyalandi(true);
      setTimeout(() => setKopyalandi(false), 2000);
    } catch {
      // Pano izni yoksa sessizce geç; adres zaten ekranda görünüyor ve seçilebilir.
    }
  };

  if (tenant.isLoading) {
    return null;
  }

  return (
    <div className="card mb-4">
      <h2 className="mb-1 text-[15px] font-semibold">Başvuru Bağlantınız</h2>
      <p className="mb-3 text-[12.5px] text-ink-soft">
        Bu adresi web sitenize veya sosyal medyanıza koyun; dolduranlar bu listeye düşer.
      </p>

      {hata && (
        <div className="mb-3 rounded-[10px] border border-red/40 bg-red/10 px-3 py-2 text-[13px] text-red">
          {hata}
        </div>
      )}

      {!duzenleniyor && mevcut && (
        <div className="flex flex-wrap items-center gap-2">
          <code className="flex-1 overflow-x-auto rounded-[10px] border border-line bg-bg px-3 py-2 text-[13px]">
            {tamAdres(mevcut)}
          </code>
          <button type="button" className="btn btn-ghost" onClick={kopyala}>
            {kopyalandi ? 'Kopyalandı ✓' : 'Kopyala'}
          </button>
          <a
            href={tamAdres(mevcut)}
            target="_blank"
            rel="noreferrer"
            className="btn btn-ghost"
          >
            Önizle
          </a>
          <button
            type="button"
            className="btn btn-ghost"
            onClick={() => {
              setSlug(mevcut);
              setDuzenleniyor(true);
            }}
          >
            Değiştir
          </button>
        </div>
      )}

      {!duzenleniyor && !mevcut && (
        <div className="flex flex-wrap items-center gap-3">
          <span className="text-[13.5px] text-ink-soft">
            Henüz bir başvuru bağlantınız yok.
          </span>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => {
              setSlug('');
              setDuzenleniyor(true);
            }}
          >
            Bağlantı oluştur
          </button>
        </div>
      )}

      {duzenleniyor && (
        <div className="space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-[13px] text-ink-soft">{window.location.origin}/basvuru/</span>
            <input
              className="flex-1 rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none"
              value={slug}
              onChange={(e) => setSlug(e.target.value)}
              placeholder="bale-akademi"
              maxLength={60}
              autoFocus
            />
          </div>
          <p className="text-[12px] text-ink-soft">
            Sadece küçük harf, rakam ve tire kullanın. Boş bırakıp kaydederseniz form kapanır.
          </p>
          <div className="flex gap-2">
            <button
              type="button"
              className="btn btn-primary"
              disabled={mutation.isPending}
              onClick={kaydet}
            >
              {mutation.isPending ? 'Kaydediliyor…' : 'Kaydet'}
            </button>
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => {
                setDuzenleniyor(false);
                setHata(null);
              }}
            >
              Vazgeç
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
