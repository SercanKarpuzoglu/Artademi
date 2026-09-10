import { useState } from 'react';
import { ApiException } from '../../api/client';
import type { GroupResponse, OdemePlani } from '../../api/types';
import { useDebounce } from '../../lib/useDebounce';
import KayitPlaniModal from '../enrollment/KayitPlaniModal';
import { useGroups } from '../group/useGroups';
import { useEnrollStudent } from './useStudentEnrollments';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

/**
 * Yeni öğrenci formunda "gruba da yaz" adımı: kayıt açıldıktan sonra seçilen gruba yazar.
 * GRUP tipinde plan (Aylık / Dönemlik) sorulur — kredi ve tahakkuk plana göre açılır.
 * Grup seçilmezse hiçbir şey yapmaz; kullanıcı "Atla" ile geçebilir.
 */
export default function KayitSonrasiGrup({
  studentId,
  ogrenciAd,
  onTamam,
}: {
  studentId: number;
  ogrenciAd: string;
  onTamam: () => void;
}) {
  const [q, setQ] = useState('');
  const debouncedQ = useDebounce(q, 300);
  const [secili, setSecili] = useState<GroupResponse | null>(null);
  const [hata, setHata] = useState<string | null>(null);
  const gruplar = useGroups({ q: debouncedQ.trim() || undefined, aktif: true, size: 10 });
  const adaylar = debouncedQ.trim() && !secili ? gruplar.data?.data ?? [] : [];
  const ekleMut = useEnrollStudent(studentId);

  async function yaz(grup: GroupResponse, odemePlani?: OdemePlani) {
    setHata(null);
    try {
      await ekleMut.mutateAsync({ ogrenciId: studentId, grupId: grup.id, odemePlani });
      onTamam();
    } catch (e) {
      setSecili(null);
      setHata(e instanceof ApiException ? e.message : 'Gruba yazılamadı.');
    }
  }

  function sec(grup: GroupResponse) {
    if (grup.tip === 'GRUP') {
      setSecili(grup); // plan modalı açılır
    } else {
      void yaz(grup);
    }
  }

  return (
    <div
      className="fixed inset-0 z-40 grid place-items-center bg-black/40 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="kayit-sonrasi-grup-baslik"
    >
      <div className="card w-full max-w-md space-y-4">
        <div>
          <h3 id="kayit-sonrasi-grup-baslik">{ogrenciAd} kaydedildi</h3>
          <p className="text-[13px] text-ink-soft">
            İsterseniz şimdi bir gruba yazın; sonra da öğrenci sayfasından ekleyebilirsiniz.
          </p>
        </div>

        <div className="relative">
          <input
            type="search"
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setHata(null);
            }}
            placeholder="Grup adı ara…"
            aria-label="Grup ara"
            className={inputClass}
            disabled={ekleMut.isPending}
          />
          {adaylar.length > 0 && (
            <ul className="absolute z-10 mt-1 max-h-56 w-full overflow-auto rounded-[10px] border border-line bg-card shadow-lg">
              {adaylar.map((g) => (
                <li key={g.id}>
                  <button
                    type="button"
                    className="flex w-full items-center justify-between px-3 py-2 text-left text-[13.5px] hover:bg-gray-50"
                    disabled={ekleMut.isPending}
                    onClick={() => sec(g)}
                  >
                    <span>{g.ad}</span>
                    <span className="text-xs text-ink-soft">
                      {g.brans?.ad ?? ''}
                      {g.ogretmen ? ` · ${g.ogretmen.ad} ${g.ogretmen.soyad}` : ''}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
          {hata && <p className="mt-1 text-xs text-red">{hata}</p>}
        </div>

        <div className="flex justify-end">
          <button type="button" className="btn btn-ghost" onClick={onTamam}>
            Atla
          </button>
        </div>
      </div>

      {secili && (
        <KayitPlaniModal
          grupId={secili.id}
          grupAd={secili.ad}
          ogrenciAd={ogrenciAd}
          pending={ekleMut.isPending}
          onVazgec={() => setSecili(null)}
          onOnayla={(plan) => yaz(secili, plan)}
        />
      )}
    </div>
  );
}
