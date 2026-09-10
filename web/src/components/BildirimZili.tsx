import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { bildirimlerOkundu, bildirimOkundu, getZil } from '../api/bildirim';
import type { UygulamaBildirimi } from '../api/types';
import { formatDateTime } from '../lib/format';

const SORGU_ARALIGI_MS = 30_000;

/**
 * Üst bardaki zil: 30 sn'de bir /api/bildirimler sorgulanır; yeni bir bildirim geldiğinde sağ üstte
 * kısa bir toast çıkar ("yoklama alındı" popup'ı). Açılır listede tıklanan bildirim okundu olur ve
 * bağlantısına gider. WebSocket yok; küçük kurum için polling yeterli ve sunucuya nazik.
 */
export default function BildirimZili() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [acik, setAcik] = useState(false);
  const [toast, setToast] = useState<UygulamaBildirimi | null>(null);
  const enBuyukId = useRef<number | null>(null);

  const q = useQuery({
    queryKey: ['zil'],
    queryFn: getZil,
    refetchInterval: SORGU_ARALIGI_MS,
    refetchIntervalInBackground: false,
  });

  // Yeni id gördüğümüzde toast; ilk yüklemede (sayfa açılışı) toast gösterme.
  useEffect(() => {
    const liste = q.data?.bildirimler ?? [];
    if (liste.length === 0) return;
    const maxId = Math.max(...liste.map((b) => b.id));
    if (enBuyukId.current === null) {
      enBuyukId.current = maxId;
      return;
    }
    if (maxId > enBuyukId.current) {
      const yeni = liste.find((b) => b.id === maxId) ?? null;
      enBuyukId.current = maxId;
      if (yeni && !yeni.okundu) {
        setToast(yeni);
        const t = window.setTimeout(() => setToast(null), 8000);
        return () => window.clearTimeout(t);
      }
    }
    return undefined;
  }, [q.data]);

  const okunduMut = useMutation({
    mutationFn: (id: number) => bildirimOkundu(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['zil'] }),
  });
  const hepsiMut = useMutation({
    mutationFn: () => bildirimlerOkundu(),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['zil'] }),
  });

  function ac(b: UygulamaBildirimi) {
    if (!b.okundu) okunduMut.mutate(b.id);
    setAcik(false);
    setToast(null);
    if (b.baglanti) navigate(b.baglanti);
  }

  const okunmamis = q.data?.okunmamis ?? 0;
  const liste = q.data?.bildirimler ?? [];

  return (
    <div className="relative">
      <button
        type="button"
        className="btn btn-ghost relative"
        aria-label={okunmamis > 0 ? `${okunmamis} okunmamış bildirim` : 'Bildirimler'}
        aria-expanded={acik}
        onClick={() => setAcik((v) => !v)}
      >
        <Bell size={17} strokeWidth={1.75} />
        {okunmamis > 0 && (
          <span className="absolute -right-1 -top-1 grid h-[18px] min-w-[18px] place-items-center rounded-full bg-rasp px-1 text-[10.5px] font-bold text-white">
            {okunmamis > 99 ? '99+' : okunmamis}
          </span>
        )}
      </button>

      {acik && (
        <div
          className="absolute right-0 z-40 mt-2 w-[360px] max-w-[90vw] rounded-[12px] border border-line bg-card shadow-lg"
          role="dialog"
          aria-label="Bildirimler"
        >
          <div className="flex items-center justify-between border-b border-line px-4 py-2.5">
            <b className="text-[13.5px]">Bildirimler</b>
            {okunmamis > 0 && (
              <button type="button" className="text-[12.5px] text-rasp hover:underline" onClick={() => hepsiMut.mutate()}>
                Tümünü okundu say
              </button>
            )}
          </div>
          {liste.length === 0 ? (
            <p className="px-4 py-6 text-center text-[13px] text-ink-soft">Bildirim yok</p>
          ) : (
            <ul className="max-h-[420px] overflow-y-auto">
              {liste.map((b) => (
                <li key={b.id}>
                  <button
                    type="button"
                    className={`flex w-full flex-col items-start gap-0.5 px-4 py-2.5 text-left hover:bg-gray-50 ${b.okundu ? '' : 'bg-rasp/5'}`}
                    onClick={() => ac(b)}
                  >
                    <span className={`text-[13.5px] ${b.okundu ? '' : 'font-semibold'}`}>{b.baslik}</span>
                    {b.metin && <span className="text-[12.5px] text-ink-soft">{b.metin}</span>}
                    <span className="text-[11.5px] text-ink-soft">{formatDateTime(b.olusturulmaTarihi)}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {toast && (
        <div
          role="status"
          className="fixed right-5 top-5 z-50 w-[340px] max-w-[90vw] cursor-pointer rounded-[12px] border border-rasp/30 bg-card p-4 shadow-lg"
          onClick={() => ac(toast)}
        >
          <div className="flex items-start gap-3">
            <span className="mt-0.5 text-rasp">
              <Bell size={18} strokeWidth={1.75} />
            </span>
            <div className="min-w-0">
              <b className="block text-[13.5px]">{toast.baslik}</b>
              {toast.metin && <span className="block text-[12.5px] text-ink-soft">{toast.metin}</span>}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
