/**
 * Gruba yazarken kara liste uyarısı (backend 409 KARA_LISTE). Sebep backend mesajından gelir;
 * kullanıcı "Yine de ekle" derse istek karaListeOnayi=true ile tekrarlanır.
 */
export default function KaraListeUyariModal({
  ogrenciAd,
  sebep,
  pending,
  onVazgec,
  onYineDeEkle,
}: {
  ogrenciAd: string;
  sebep: string;
  pending: boolean;
  onVazgec: () => void;
  onYineDeEkle: () => void;
}) {
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
          <b>{ogrenciAd}</b> daha önce kara listeye alınmış. Yine de gruba yazabilirsiniz; karar
          sizin.
        </p>
        <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-3 text-[13px]">
          {sebep}
        </div>
        <div className="flex justify-end gap-3">
          <button type="button" className="btn btn-ghost" onClick={onVazgec}>
            Vazgeç
          </button>
          <button type="button" className="btn btn-primary" disabled={pending} onClick={onYineDeEkle}>
            {pending ? 'Ekleniyor…' : 'Yine de ekle'}
          </button>
        </div>
      </div>
    </div>
  );
}
