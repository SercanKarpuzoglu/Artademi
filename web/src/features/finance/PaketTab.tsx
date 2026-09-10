import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import SilButonu from '../../components/SilButonu';
import { useState } from 'react';
import { ApiException } from '../../api/client';
import { getPaketler, paketIptal, paketSat } from '../../api/paket';
import type { PaketDurumu, StudentResponse } from '../../api/types';
import { formatDate, formatMoney } from '../../lib/format';
import StudentPicker from './StudentPicker';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none';

const DURUM_ETIKET: Record<PaketDurumu, string> = { AKTIF: 'Aktif', IPTAL: 'İptal' };

/**
 * Ders paketi (kontör) satışı.
 *
 * ⚠️ Kalan ders backend'de HESAPLANIR (toplam − tüketim satırı sayısı); burada ayrıca
 * hesap yapılmaz. Kontör düşümü yoklamada olur: GELDI/GELMEDI düşer, IZINLI düşmez.
 */
export default function PaketTab() {
  const qc = useQueryClient();
  const [form, setForm] = useState(false);
  const [hata, setHata] = useState<string | null>(null);

  const q = useQuery({ queryKey: ['paketler'], queryFn: () => getPaketler() });
  const liste = q.data ?? [];

  const tazele = () => {
    qc.invalidateQueries({ queryKey: ['paketler'] });
    // Satış tahakkuk ürettiği için finans listeleri de tazelenmeli.
    qc.invalidateQueries({ queryKey: ['accruals'] });
  };

  const iptal = useMutation({
    mutationFn: paketIptal,
    onSuccess: tazele,
    onError: (e) => setHata(e instanceof ApiException ? e.message : 'İptal edilemedi'),
  });

  return (
    <div className="space-y-4">
      <button type="button" className="btn btn-primary" onClick={() => setForm((v) => !v)}>
        {form ? 'Vazgeç' : 'Paket Sat'}
      </button>

      <div className="card">
        <p className="text-[12.5px] text-ink-soft">
          Paket satışı <b>peşin tek tahakkuk</b> üretir. Kontör yoklamada düşer:
          <b> geldi</b> ve <b>habersiz gelmedi</b> düşürür, <b>izinli</b> düşürmez. Kontör
          bittiğinde yoklama engellenmez; liste kalan dersi gösterir.
        </p>
      </div>

      {hata && <div className="card border-red/40 bg-red/10 text-[13px] text-red">{hata}</div>}

      {form && <PaketForm onBitti={() => { setForm(false); tazele(); }} onHata={setHata} />}

      {q.isLoading ? (
        <div className="card py-8 text-center text-ink-soft">Yükleniyor…</div>
      ) : liste.length === 0 ? (
        <div className="card py-8 text-center text-[13.5px] text-ink-soft">
          Henüz paket satılmamış. Paket, aylık aidat ve ders başı ücretin yanında üçüncü bir
          fiyatlandırma seçeneğidir.
        </div>
      ) : (
        <div className="card">
          <div className="overflow-x-auto">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Öğrenci</th>
                  <th>Paket</th>
                  <th className="t-right">Kalan / Toplam</th>
                  <th className="t-right">Tutar</th>
                  <th>Satış</th>
                  <th>Durum</th>
                  <th className="t-right">İşlem</th>
                </tr>
              </thead>
              <tbody>
                {liste.map((p) => (
                  <tr key={p.id} className={p.durum === 'IPTAL' ? 'opacity-60' : ''}>
                    <td>
                      <b>{p.ogrenciAdSoyad}</b>
                    </td>
                    <td>
                      {p.ad}
                      {p.grupAdi && <div className="text-[12px] text-ink-soft">{p.grupAdi}</div>}
                    </td>
                    <td className="t-right">
                      <span className={p.kalanDers === 0 ? 'text-red' : ''}>
                        <b>{p.kalanDers}</b> / {p.toplamDers}
                      </span>
                      {p.kalanDers === 0 && p.durum === 'AKTIF' && (
                        <div className="text-[12px] text-red">Kontör bitti</div>
                      )}
                    </td>
                    <td className="t-right">
                      <span className="amount">{formatMoney(p.tutar)} ₺</span>
                    </td>
                    <td className="text-ink-soft">
                      {formatDate(p.satisTarihi)}
                      {p.sonKullanmaTarihi && (
                        <div className="text-[12px]">
                          Son: {formatDate(p.sonKullanmaTarihi)}
                        </div>
                      )}
                    </td>
                    <td>
                      <span className={`badge ${p.durum === 'AKTIF' ? 'b-green' : 'b-red'}`}>
                        {DURUM_ETIKET[p.durum]}
                      </span>
                      {p.suresiDoldu && p.durum === 'AKTIF' && (
                        <div className="mt-1 text-[12px] text-red">Süresi doldu</div>
                      )}
                    </td>
                    <td className="t-right">
                      {p.durum === 'AKTIF' && (
                        <button
                          type="button"
                          className="btn btn-ghost"
                          disabled={iptal.isPending}
                          onClick={() => {
                            if (
                              window.confirm(
                                'Paket iptal edilecek. Oluşan tahakkuk SİLİNMEZ; ' +
                                  'iade/mahsup gerekiyorsa finanstan siz yapmalısınız. Devam?',
                              )
                            ) {
                              iptal.mutate(p.id);
                            }
                          }}
                        >
                          İptal
                        </button>
                      )}
                      <SilButonu tur="paket" id={p.id} ad={p.ad} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

function PaketForm({ onBitti, onHata }: { onBitti: () => void; onHata: (m: string) => void }) {
  const [ogrenci, setOgrenci] = useState<StudentResponse | null>(null);
  const [ad, setAd] = useState('');
  const [toplamDers, setToplamDers] = useState('10');
  const [tutar, setTutar] = useState('');
  const [sonKullanma, setSonKullanma] = useState('');
  const [aciklama, setAciklama] = useState('');

  const m = useMutation({
    mutationFn: paketSat,
    onSuccess: onBitti,
    onError: (e) => onHata(e instanceof ApiException ? e.message : 'Paket satılamadı'),
  });

  return (
    <form
      className="card space-y-3"
      onSubmit={(e) => {
        e.preventDefault();
        if (!ogrenci) {
          onHata('Öğrenci seçin');
          return;
        }
        m.mutate({
          ogrenciId: ogrenci.id,
          ad,
          toplamDers: Number(toplamDers),
          tutar,
          sonKullanmaTarihi: sonKullanma || undefined,
          aciklama: aciklama || undefined,
        });
      }}
    >
      <h3 className="text-[15px] font-semibold">Paket Sat</h3>

      <div className="text-[13px]">
        <span className="mb-1 block text-ink-soft">Öğrenci *</span>
        <StudentPicker selected={ogrenci} onSelect={setOgrenci} />
      </div>

      <div className="flex flex-wrap gap-3">
        <label className="block flex-1 text-[13px]">
          <span className="mb-1 block text-ink-soft">Paket Adı *</span>
          <input className={inputClass} value={ad} onChange={(e) => setAd(e.target.value)}
            required maxLength={150} placeholder="10 Derslik Bale Paketi" />
        </label>
        <label className="block text-[13px]">
          <span className="mb-1 block text-ink-soft">Ders Sayısı *</span>
          <input className={inputClass} type="number" min="1" required value={toplamDers}
            onChange={(e) => setToplamDers(e.target.value)} />
        </label>
        <label className="block text-[13px]">
          <span className="mb-1 block text-ink-soft">Tutar (₺) *</span>
          <input className={inputClass} type="number" step="0.01" min="0" required value={tutar}
            onChange={(e) => setTutar(e.target.value)} />
        </label>
      </div>

      <div className="flex flex-wrap gap-3">
        <label className="block text-[13px]">
          <span className="mb-1 block text-ink-soft">Son Kullanma</span>
          <input className={inputClass} type="date" value={sonKullanma}
            onChange={(e) => setSonKullanma(e.target.value)} />
          <span className="mt-1 block text-[12px] text-ink-soft">Boş = süresiz</span>
        </label>
        <label className="block flex-1 text-[13px]">
          <span className="mb-1 block text-ink-soft">Açıklama</span>
          <input className={inputClass} value={aciklama} maxLength={500}
            onChange={(e) => setAciklama(e.target.value)} />
        </label>
      </div>

      <button type="submit" className="btn btn-primary" disabled={m.isPending}>
        {m.isPending ? 'Kaydediliyor…' : 'Sat ve Tahakkuk Oluştur'}
      </button>
    </form>
  );
}
