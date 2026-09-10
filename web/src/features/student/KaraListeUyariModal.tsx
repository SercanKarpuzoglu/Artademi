/** Uyarının çıktığı işlem — metin ve düğme buna göre yazılır. */
export type KaraListeEylemi = 'grup' | 'kayit' | 'donustur';

const METIN: Record<KaraListeEylemi, { aciklama: string; dugme: string; bekleyen: string }> = {
  grup: { aciklama: 'Yine de gruba yazabilirsiniz; karar sizin.', dugme: 'Yine de ekle', bekleyen: 'Ekleniyor…' },
  kayit: { aciklama: 'Yine de yeni kayıt açabilirsiniz; karar sizin.', dugme: 'Yine de kaydet', bekleyen: 'Kaydediliyor…' },
  donustur: {
    aciklama: 'Yine de öğrenci kaydına dönüştürebilirsiniz; karar sizin.',
    dugme: 'Yine de dönüştür',
    bekleyen: 'Dönüştürülüyor…',
  },
};

/**
 * Kara liste uyarısı (backend 409 KARA_LISTE): gruba yazarken, aynı TC ile yeni kayıt açarken ve
 * başvuruyu dönüştürürken çıkar. Sebep backend mesajından gelir; onaylanırsa istek
 * karaListeOnayi=true ile tekrarlanır. Engel değil uyarıdır — karar kurumun.
 */
export default function KaraListeUyariModal({
  ogrenciAd,
  sebep,
  pending,
  eylem = 'grup',
  onVazgec,
  onYineDeEkle,
}: {
  ogrenciAd: string;
  sebep: string;
  pending: boolean;
  eylem?: KaraListeEylemi;
  onVazgec: () => void;
  onYineDeEkle: () => void;
}) {
  const metin = METIN[eylem];
  return (
    <div
      className="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4"
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="kara-liste-uyari-baslik"
      onClick={onVazgec}
    >
      <div className="card w-full max-w-md space-y-4" onClick={(e) => e.stopPropagation()}>
        <h3 id="kara-liste-uyari-baslik" className="text-red">
          Bu öğrenci kara listede
        </h3>
        <p className="text-[13.5px]">
          <b>{ogrenciAd}</b> daha önce kara listeye alınmış. {metin.aciklama}
        </p>
        <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-3 text-[13px]">
          {sebep}
        </div>
        <div className="flex justify-end gap-3">
          <button type="button" className="btn btn-ghost" onClick={onVazgec}>
            Vazgeç
          </button>
          <button type="button" className="btn btn-primary" disabled={pending} onClick={onYineDeEkle}>
            {pending ? metin.bekleyen : metin.dugme}
          </button>
        </div>
      </div>
    </div>
  );
}
