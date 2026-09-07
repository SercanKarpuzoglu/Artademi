import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ApiException } from '../../api/client';
import { createTedarikci, getTedarikciler, tedarikciDurum } from '../../api/tedarikci';
import { formatMoney } from '../../lib/format';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none';

/**
 * Tedarikçi tanımları ve "kime ne kadar ödedik" toplamı.
 *
 * ⚠️ Bu bir cari hesap değildir: fatura/borç-alacak takibi yok, yalnızca giderlerin kime
 * yapıldığı tutulur. Toplam da saklanmaz, giderlerden hesaplanır.
 */
export default function TedarikciTab() {
  const qc = useQueryClient();
  const [form, setForm] = useState(false);
  const [hata, setHata] = useState<string | null>(null);

  const q = useQuery({ queryKey: ['tedarikciler'], queryFn: () => getTedarikciler() });
  const liste = q.data ?? [];

  const tazele = () => qc.invalidateQueries({ queryKey: ['tedarikciler'] });

  const durum = useMutation({
    mutationFn: ({ id, aktif }: { id: number; aktif: boolean }) => tedarikciDurum(id, aktif),
    onSuccess: tazele,
    onError: (e) => setHata(e instanceof ApiException ? e.message : 'Durum değiştirilemedi'),
  });

  return (
    <div className="space-y-4">
      <button type="button" className="btn btn-primary" onClick={() => setForm((v) => !v)}>
        {form ? 'Vazgeç' : 'Yeni Tedarikçi'}
      </button>

      {hata && <div className="card border-red/40 bg-red/10 text-[13px] text-red">{hata}</div>}

      {form && <TedarikciForm onBitti={() => { setForm(false); tazele(); }} onHata={setHata} />}

      {q.isLoading ? (
        <div className="card py-8 text-center text-ink-soft">Yükleniyor…</div>
      ) : liste.length === 0 ? (
        <div className="card py-8 text-center text-[13.5px] text-ink-soft">
          Henüz tedarikçi tanımlanmamış. Tanımlarsanız giderleri tedarikçiye bağlayıp
          "kime ne kadar ödedik" sorusunu cevaplayabilirsiniz.
        </div>
      ) : (
        <div className="card">
          <div className="overflow-x-auto">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Tedarikçi</th>
                  <th>İletişim</th>
                  <th>Vergi No</th>
                  <th className="t-right">Toplam Ödenen</th>
                  <th>Durum</th>
                  <th className="t-right">İşlem</th>
                </tr>
              </thead>
              <tbody>
                {liste.map((t) => (
                  <tr key={t.id} className={t.aktif ? '' : 'opacity-60'}>
                    <td>
                      <b>{t.ad}</b>
                      {t.aciklama && (
                        <div className="text-[12px] text-ink-soft">{t.aciklama}</div>
                      )}
                    </td>
                    <td className="text-ink-soft">
                      {t.telefon ?? '—'}
                      {t.email && <div className="text-[12px]">{t.email}</div>}
                    </td>
                    <td className="text-ink-soft">{t.vergiNo ?? '—'}</td>
                    <td className="t-right">
                      <span className="amount">{formatMoney(t.toplamOdenen)} ₺</span>
                    </td>
                    <td>
                      <span className={`badge ${t.aktif ? 'b-green' : 'b-red'}`}>
                        {t.aktif ? 'Aktif' : 'Pasif'}
                      </span>
                    </td>
                    <td className="t-right">
                      <button
                        type="button"
                        className="btn btn-ghost"
                        disabled={durum.isPending}
                        onClick={() => durum.mutate({ id: t.id, aktif: !t.aktif })}
                      >
                        {t.aktif ? 'Pasifleştir' : 'Aktifleştir'}
                      </button>
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

function TedarikciForm({ onBitti, onHata }: { onBitti: () => void; onHata: (m: string) => void }) {
  const [ad, setAd] = useState('');
  const [telefon, setTelefon] = useState('');
  const [email, setEmail] = useState('');
  const [vergiNo, setVergiNo] = useState('');
  const [aciklama, setAciklama] = useState('');

  const m = useMutation({
    mutationFn: createTedarikci,
    onSuccess: onBitti,
    onError: (e) => onHata(e instanceof ApiException ? e.message : 'Tedarikçi oluşturulamadı'),
  });

  return (
    <form
      className="card space-y-3"
      onSubmit={(e) => {
        e.preventDefault();
        m.mutate({
          ad,
          telefon: telefon || undefined,
          email: email || undefined,
          vergiNo: vergiNo || undefined,
          aciklama: aciklama || undefined,
        });
      }}
    >
      <h3 className="text-[15px] font-semibold">Yeni Tedarikçi</h3>
      <div className="flex flex-wrap gap-3">
        <label className="block flex-1 text-[13px]">
          <span className="mb-1 block text-ink-soft">Ad *</span>
          <input className={inputClass} value={ad} onChange={(e) => setAd(e.target.value)}
            required maxLength={200} placeholder="Kırtasiye A.Ş." />
        </label>
        <label className="block text-[13px]">
          <span className="mb-1 block text-ink-soft">Telefon</span>
          <input className={inputClass} value={telefon} maxLength={30}
            onChange={(e) => setTelefon(e.target.value)} />
        </label>
      </div>
      <div className="flex flex-wrap gap-3">
        <label className="block flex-1 text-[13px]">
          <span className="mb-1 block text-ink-soft">E-posta</span>
          <input className={inputClass} type="email" value={email} maxLength={255}
            onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label className="block text-[13px]">
          <span className="mb-1 block text-ink-soft">Vergi No</span>
          <input className={inputClass} value={vergiNo} maxLength={20}
            onChange={(e) => setVergiNo(e.target.value)} />
        </label>
      </div>
      <label className="block text-[13px]">
        <span className="mb-1 block text-ink-soft">Açıklama</span>
        <input className={inputClass} value={aciklama} maxLength={500}
          onChange={(e) => setAciklama(e.target.value)} />
      </label>
      <button type="submit" className="btn btn-primary" disabled={m.isPending}>
        {m.isPending ? 'Kaydediliyor…' : 'Kaydet'}
      </button>
    </form>
  );
}
