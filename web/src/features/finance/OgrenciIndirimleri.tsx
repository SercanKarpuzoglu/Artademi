import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ApiException } from '../../api/client';
import { getIndirimler, getOgrenciIndirimleri, ogrenciIndirimiBitir, ogrenciyeIndirimAta } from '../../api/indirim';
import type { OgrenciIndirimiInput } from '../../api/types';
import { formatDate } from '../../lib/format';
import { useStudentEnrollments } from '../student/useStudentEnrollments';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

function bugun(): string {
  return new Date().toISOString().slice(0, 10);
}

/**
 * Öğrenciye özel indirimler (Dalga D): tanımlı bir indirimi bu öğrenciye — isterseniz tek bir gruba —
 * tarih aralığıyla uygularsınız; sonraki otomatik tahakkuk brüt − indirim = net yazar.
 * Yalnız para gören roller (çağıran gating yapar).
 */
export default function OgrenciIndirimleri({ studentId }: { studentId: number }) {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['ogrenci-indirimleri', studentId], queryFn: () => getOgrenciIndirimleri(studentId) });
  const tanimlar = useQuery({ queryKey: ['indirimler', 'aktif'], queryFn: () => getIndirimler(true) });
  const kayitlar = useStudentEnrollments(studentId);
  const [form, setForm] = useState(false);
  const [indirimId, setIndirimId] = useState('');
  const [grupId, setGrupId] = useState('');
  const [baslangic, setBaslangic] = useState(bugun());
  const [bitis, setBitis] = useState('');
  const [aciklama, setAciklama] = useState('');
  const [hata, setHata] = useState<string | null>(null);

  const ata = useMutation({
    mutationFn: (payload: OgrenciIndirimiInput) => ogrenciyeIndirimAta(studentId, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['ogrenci-indirimleri', studentId] });
      setForm(false);
      setIndirimId('');
      setGrupId('');
      setBitis('');
      setAciklama('');
    },
    onError: (e) => setHata(e instanceof ApiException ? e.message : 'Atanamadı.'),
  });
  const bitir = useMutation({
    mutationFn: (id: number) => ogrenciIndirimiBitir(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['ogrenci-indirimleri', studentId] }),
  });

  const liste = q.data ?? [];
  const aktifGruplar = (kayitlar.data?.data ?? []).filter((e) => e.durum === 'AKTIF');

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <h4 className="text-[13.5px] font-semibold">İndirimler</h4>
        <button type="button" className="btn btn-ghost" onClick={() => setForm((v) => !v)}>
          {form ? 'Kapat' : '+ İndirim uygula'}
        </button>
      </div>

      {form && (
        <form
          className="space-y-3 rounded-[12px] border border-line p-3"
          onSubmit={(e) => {
            e.preventDefault();
            setHata(null);
            if (!indirimId) return setHata('İndirim seçin');
            ata.mutate({
              indirimId: Number(indirimId),
              grupId: grupId ? Number(grupId) : undefined,
              baslangic: baslangic || undefined,
              bitis: bitis || undefined,
              aciklama: aciklama.trim() || undefined,
            });
          }}
        >
          {hata && <p className="text-[13px] font-semibold text-red">{hata}</p>}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <label className="block">
              <span className="mb-1 block text-xs text-gray-500">İndirim</span>
              <select className={inputClass} value={indirimId} onChange={(e) => setIndirimId(e.target.value)}>
                <option value="">Seçin…</option>
                {(tanimlar.data ?? []).map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.ad} ({t.etiket})
                  </option>
                ))}
              </select>
              {tanimlar.data?.length === 0 && (
                <span className="mt-1 block text-xs text-ink-soft">Önce Finans → İndirimler'de tanım yapın.</span>
              )}
            </label>
            <label className="block">
              <span className="mb-1 block text-xs text-gray-500">Grup</span>
              <select className={inputClass} value={grupId} onChange={(e) => setGrupId(e.target.value)}>
                <option value="">Tüm grupları</option>
                {aktifGruplar.map((e) => (
                  <option key={e.grup.id} value={e.grup.id}>
                    {e.grup.ad}
                  </option>
                ))}
              </select>
            </label>
            <label className="block">
              <span className="mb-1 block text-xs text-gray-500">Başlangıç</span>
              <input type="date" className={inputClass} value={baslangic} onChange={(e) => setBaslangic(e.target.value)} />
            </label>
            <label className="block">
              <span className="mb-1 block text-xs text-gray-500">Bitiş (boş = sürekli)</span>
              <input type="date" className={inputClass} value={bitis} onChange={(e) => setBitis(e.target.value)} />
            </label>
            <label className="block sm:col-span-2">
              <span className="mb-1 block text-xs text-gray-500">Açıklama</span>
              <input className={inputClass} value={aciklama} onChange={(e) => setAciklama(e.target.value)} placeholder="Örn. kardeşi Ali de kayıtlı" />
            </label>
          </div>
          <div className="flex justify-end">
            <button type="submit" className="btn btn-primary" disabled={ata.isPending}>
              {ata.isPending ? 'Uygulanıyor…' : 'Uygula'}
            </button>
          </div>
        </form>
      )}

      {q.isLoading ? (
        <p className="text-sm text-ink-soft">Yükleniyor…</p>
      ) : liste.length === 0 ? (
        <p className="text-sm text-ink-soft">Tanımlı indirim yok</p>
      ) : (
        <ul className="divide-y divide-line">
          {liste.map((o) => (
            <li key={o.id} className={`flex flex-wrap items-center justify-between gap-2 py-2 text-[13.5px] ${o.aktif ? '' : 'opacity-60'}`}>
              <span>
                <b>{o.indirim.ad}</b> <span className="amount">{o.indirim.etiket}</span>
                <span className="ml-2 text-ink-soft">
                  {o.grupAd ?? 'tüm gruplar'} · {formatDate(o.baslangic)}–{o.bitis ? formatDate(o.bitis) : '∞'}
                </span>
                {o.aciklama && <span className="ml-2 text-ink-soft">({o.aciklama})</span>}
              </span>
              {o.aktif ? (
                <button type="button" className="btn btn-ghost" disabled={bitir.isPending} onClick={() => bitir.mutate(o.id)}>
                  Bitir
                </button>
              ) : (
                <span className="badge b-gray">Bitti</span>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
