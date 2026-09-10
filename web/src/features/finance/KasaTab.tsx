import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import SilButonu from '../../components/SilButonu';
import { useState } from 'react';
import { ApiException } from '../../api/client';
import {
  createKasa,
  duzeltme,
  getHareketler,
  getKasalar,
  hareketSil,
  kasaDurum,
  transfer,
} from '../../api/kasa';
import type { HareketYonu, KasaResponse, KasaTipi } from '../../api/types';
import { formatDate, formatMoney } from '../../lib/format';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none';

/**
 * Kasa yönetimi: tanım, bakiye, transfer ve elle düzeltme.
 *
 * ⚠️ Bakiye backend'de HESAPLANIR (açılış + tahsilatlar − giderler + hareketler). Burada
 * ayrıca hesap yapılmaz; iki yerde hesap iki farklı sonuç demektir.
 */
export default function KasaTab() {
  const qc = useQueryClient();
  const [form, setForm] = useState(false);
  const [transferForm, setTransferForm] = useState(false);
  const [acikKasa, setAcikKasa] = useState<number | null>(null);
  const [hata, setHata] = useState<string | null>(null);

  const q = useQuery({ queryKey: ['kasalar'], queryFn: () => getKasalar() });
  const kasalar = q.data ?? [];

  const tazele = () => {
    qc.invalidateQueries({ queryKey: ['kasalar'] });
    qc.invalidateQueries({ queryKey: ['kasa-hareket'] });
  };

  const durum = useMutation({
    mutationFn: ({ id, aktif }: { id: number; aktif: boolean }) => kasaDurum(id, aktif),
    onSuccess: tazele,
    onError: (e) => setHata(e instanceof ApiException ? e.message : 'Durum değiştirilemedi'),
  });

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2">
        <button type="button" className="btn btn-primary" onClick={() => setForm((v) => !v)}>
          {form ? 'Vazgeç' : 'Yeni Kasa'}
        </button>
        <button
          type="button"
          className="btn btn-ghost"
          disabled={kasalar.filter((k) => k.aktif).length < 2}
          onClick={() => setTransferForm((v) => !v)}
        >
          {transferForm ? 'Vazgeç' : 'Kasalar Arası Transfer'}
        </button>
        {kasalar.filter((k) => k.aktif).length < 2 && (
          <span className="self-center text-[12px] text-ink-soft">
            Transfer için en az iki aktif kasa gerekir
          </span>
        )}
      </div>

      {hata && <div className="card border-red/40 bg-red/10 text-[13px] text-red">{hata}</div>}

      {form && <KasaForm onBitti={() => { setForm(false); tazele(); }} onHata={setHata} />}
      {transferForm && (
        <TransferForm
          kasalar={kasalar.filter((k) => k.aktif)}
          onBitti={() => { setTransferForm(false); tazele(); }}
          onHata={setHata}
        />
      )}

      {q.isLoading ? (
        <div className="card py-8 text-center text-ink-soft">Yükleniyor…</div>
      ) : kasalar.length === 0 ? (
        <div className="card py-8 text-center text-[13.5px] text-ink-soft">
          Henüz kasa tanımlanmamış. Kasa kullanmak zorunlu değildir; tanımlarsanız tahsilat ve
          giderleri kasaya bağlayıp bakiye takibi yapabilirsiniz.
        </div>
      ) : (
        <div className="card">
          <div className="overflow-x-auto">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Kasa</th>
                  <th>Tip</th>
                  <th className="t-right">Açılış</th>
                  <th className="t-right">Bakiye</th>
                  <th>Durum</th>
                  <th className="t-right">İşlem</th>
                </tr>
              </thead>
              <tbody>
                {kasalar.map((k) => (
                  <tr key={k.id} className={k.aktif ? '' : 'opacity-60'}>
                    <td>
                      <b>{k.ad}</b>
                      {k.iban && <div className="text-[12px] text-ink-soft">{k.iban}</div>}
                    </td>
                    <td className="text-ink-soft">{k.tip === 'BANKA' ? 'Banka' : 'Nakit'}</td>
                    <td className="t-right text-ink-soft">{formatMoney(k.acilisBakiyesi)} ₺</td>
                    <td className="t-right">
                      <span className="amount">{formatMoney(k.bakiye)} ₺</span>
                    </td>
                    <td>
                      <span className={`badge ${k.aktif ? 'b-green' : 'b-red'}`}>
                        {k.aktif ? 'Aktif' : 'Pasif'}
                      </span>
                    </td>
                    <td className="t-right">
                      <div className="flex justify-end gap-2">
                        <button
                          type="button"
                          className="btn btn-ghost"
                          onClick={() => setAcikKasa(acikKasa === k.id ? null : k.id)}
                        >
                          {acikKasa === k.id ? 'Gizle' : 'Hareketler'}
                        </button>
                        <button
                          type="button"
                          className="btn btn-ghost"
                          disabled={durum.isPending}
                          onClick={() => durum.mutate({ id: k.id, aktif: !k.aktif })}
                        >
                          {k.aktif ? 'Pasifleştir' : 'Aktifleştir'}
                        </button>
                        <SilButonu tur="kasa" id={k.id} ad={k.ad} />
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {acikKasa !== null && (
        <HareketListesi
          kasaId={acikKasa}
          kasaAdi={kasalar.find((k) => k.id === acikKasa)?.ad ?? ''}
          onDegisti={tazele}
          onHata={setHata}
        />
      )}
    </div>
  );
}

function KasaForm({ onBitti, onHata }: { onBitti: () => void; onHata: (m: string) => void }) {
  const [ad, setAd] = useState('');
  const [tip, setTip] = useState<KasaTipi>('NAKIT');
  const [iban, setIban] = useState('');
  const [acilis, setAcilis] = useState('0');

  const m = useMutation({
    mutationFn: createKasa,
    onSuccess: onBitti,
    onError: (e) => onHata(e instanceof ApiException ? e.message : 'Kasa oluşturulamadı'),
  });

  return (
    <form
      className="card space-y-3"
      onSubmit={(e) => {
        e.preventDefault();
        m.mutate({ ad, tip, iban: iban || undefined, acilisBakiyesi: acilis || '0' });
      }}
    >
      <h3 className="text-[15px] font-semibold">Yeni Kasa</h3>
      <div className="flex flex-wrap gap-3">
        <label className="block flex-1 text-[13px]">
          <span className="mb-1 block text-ink-soft">Kasa Adı *</span>
          <input className={inputClass} value={ad} onChange={(e) => setAd(e.target.value)} required
            maxLength={150} placeholder="Merkez Nakit" />
        </label>
        <label className="block text-[13px]">
          <span className="mb-1 block text-ink-soft">Tip</span>
          <select className={inputClass} value={tip}
            onChange={(e) => setTip(e.target.value as KasaTipi)}>
            <option value="NAKIT">Nakit</option>
            <option value="BANKA">Banka</option>
          </select>
        </label>
      </div>
      {tip === 'BANKA' && (
        <label className="block text-[13px]">
          <span className="mb-1 block text-ink-soft">IBAN</span>
          <input className={inputClass} value={iban} onChange={(e) => setIban(e.target.value)}
            maxLength={34} />
        </label>
      )}
      <label className="block text-[13px]">
        <span className="mb-1 block text-ink-soft">Açılış Bakiyesi</span>
        <input className={inputClass} type="number" step="0.01" value={acilis}
          onChange={(e) => setAcilis(e.target.value)} />
        <span className="mt-1 block text-[12px] text-ink-soft">
          Sisteme geçmeden önceki mevcut tutar. Geçmiş hareketleri girmenize gerek kalmaz.
        </span>
      </label>
      <button type="submit" className="btn btn-primary" disabled={m.isPending}>
        {m.isPending ? 'Kaydediliyor…' : 'Kaydet'}
      </button>
    </form>
  );
}

function TransferForm({
  kasalar,
  onBitti,
  onHata,
}: {
  kasalar: KasaResponse[];
  onBitti: () => void;
  onHata: (m: string) => void;
}) {
  const [kaynak, setKaynak] = useState<string>('');
  const [hedef, setHedef] = useState<string>('');
  const [tutar, setTutar] = useState('');
  const [aciklama, setAciklama] = useState('');

  const m = useMutation({
    mutationFn: transfer,
    onSuccess: onBitti,
    onError: (e) => onHata(e instanceof ApiException ? e.message : 'Transfer yapılamadı'),
  });

  return (
    <form
      className="card space-y-3"
      onSubmit={(e) => {
        e.preventDefault();
        m.mutate({
          kaynakKasaId: Number(kaynak),
          hedefKasaId: Number(hedef),
          tutar,
          aciklama: aciklama || undefined,
        });
      }}
    >
      <h3 className="text-[15px] font-semibold">Kasalar Arası Transfer</h3>
      <div className="flex flex-wrap gap-3">
        <label className="block flex-1 text-[13px]">
          <span className="mb-1 block text-ink-soft">Kaynak *</span>
          <select className={inputClass} value={kaynak} required
            onChange={(e) => setKaynak(e.target.value)}>
            <option value="">Seçiniz</option>
            {kasalar.map((k) => (
              <option key={k.id} value={k.id}>{k.ad}</option>
            ))}
          </select>
        </label>
        <label className="block flex-1 text-[13px]">
          <span className="mb-1 block text-ink-soft">Hedef *</span>
          <select className={inputClass} value={hedef} required
            onChange={(e) => setHedef(e.target.value)}>
            <option value="">Seçiniz</option>
            {kasalar.filter((k) => String(k.id) !== kaynak).map((k) => (
              <option key={k.id} value={k.id}>{k.ad}</option>
            ))}
          </select>
        </label>
        <label className="block text-[13px]">
          <span className="mb-1 block text-ink-soft">Tutar *</span>
          <input className={inputClass} type="number" step="0.01" min="0.01" required
            value={tutar} onChange={(e) => setTutar(e.target.value)} />
        </label>
      </div>
      <label className="block text-[13px]">
        <span className="mb-1 block text-ink-soft">Açıklama</span>
        <input className={inputClass} value={aciklama} maxLength={500}
          onChange={(e) => setAciklama(e.target.value)} placeholder="Kasadan bankaya yatırıldı" />
      </label>
      <button type="submit" className="btn btn-primary" disabled={m.isPending}>
        {m.isPending ? 'Aktarılıyor…' : 'Transfer Et'}
      </button>
    </form>
  );
}

function HareketListesi({
  kasaId,
  kasaAdi,
  onDegisti,
  onHata,
}: {
  kasaId: number;
  kasaAdi: string;
  onDegisti: () => void;
  onHata: (m: string) => void;
}) {
  const [duzeltmeAcik, setDuzeltmeAcik] = useState(false);
  const [yon, setYon] = useState<HareketYonu>('CIKIS');
  const [tutar, setTutar] = useState('');
  const [aciklama, setAciklama] = useState('');

  const q = useQuery({
    queryKey: ['kasa-hareket', kasaId],
    queryFn: () => getHareketler(kasaId),
  });

  const duz = useMutation({
    mutationFn: () => duzeltme(kasaId, { yon, tutar, aciklama: aciklama || undefined }),
    onSuccess: () => {
      setDuzeltmeAcik(false);
      setTutar('');
      setAciklama('');
      q.refetch();
      onDegisti();
    },
    onError: (e) => onHata(e instanceof ApiException ? e.message : 'Düzeltme eklenemedi'),
  });

  const sil = useMutation({
    mutationFn: hareketSil,
    onSuccess: () => {
      q.refetch();
      onDegisti();
    },
    onError: (e) => onHata(e instanceof ApiException ? e.message : 'Hareket silinemedi'),
  });

  const hareketler = q.data ?? [];

  return (
    <div className="card space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-[15px] font-semibold">{kasaAdi} — Hareketler</h3>
        <button type="button" className="btn btn-ghost" onClick={() => setDuzeltmeAcik((v) => !v)}>
          {duzeltmeAcik ? 'Vazgeç' : 'Düzeltme Ekle'}
        </button>
      </div>

      <p className="text-[12px] text-ink-soft">
        Burada yalnızca transfer ve elle düzeltmeler görünür. Tahsilat ve giderler kendi
        sekmelerinde durur; bakiyeye onlar da dahildir.
      </p>

      {duzeltmeAcik && (
        <form
          className="flex flex-wrap items-end gap-3 rounded-[10px] border border-line p-3"
          onSubmit={(e) => {
            e.preventDefault();
            duz.mutate();
          }}
        >
          <label className="block text-[13px]">
            <span className="mb-1 block text-ink-soft">Yön</span>
            <select className={inputClass} value={yon}
              onChange={(e) => setYon(e.target.value as HareketYonu)}>
              <option value="CIKIS">Çıkış</option>
              <option value="GIRIS">Giriş</option>
            </select>
          </label>
          <label className="block text-[13px]">
            <span className="mb-1 block text-ink-soft">Tutar</span>
            <input className={inputClass} type="number" step="0.01" min="0.01" required
              value={tutar} onChange={(e) => setTutar(e.target.value)} />
          </label>
          <label className="block flex-1 text-[13px]">
            <span className="mb-1 block text-ink-soft">Açıklama</span>
            <input className={inputClass} value={aciklama} maxLength={500}
              onChange={(e) => setAciklama(e.target.value)} placeholder="Sayım farkı" />
          </label>
          <button type="submit" className="btn btn-primary" disabled={duz.isPending}>
            Ekle
          </button>
        </form>
      )}

      {q.isLoading ? (
        <p className="py-4 text-center text-ink-soft">Yükleniyor…</p>
      ) : hareketler.length === 0 ? (
        <p className="py-4 text-center text-[13px] text-ink-soft">Hareket yok.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead>
              <tr>
                <th>Tarih</th>
                <th>Tip</th>
                <th>Açıklama</th>
                <th className="t-right">Tutar</th>
                <th className="t-right">İşlem</th>
              </tr>
            </thead>
            <tbody>
              {hareketler.map((h) => (
                <tr key={h.id}>
                  <td className="text-ink-soft">{formatDate(h.tarih)}</td>
                  <td>
                    <span className={`badge ${h.yon === 'GIRIS' ? 'b-green' : 'b-red'}`}>
                      {h.tip === 'TRANSFER' ? 'Transfer' : 'Düzeltme'}
                      {h.yon === 'GIRIS' ? ' ↓' : ' ↑'}
                    </span>
                  </td>
                  <td className="text-ink-soft">{h.aciklama ?? '—'}</td>
                  <td className="t-right">
                    <span className={h.yon === 'GIRIS' ? 'text-green' : 'text-red'}>
                      {h.yon === 'GIRIS' ? '+' : '−'}
                      {formatMoney(h.tutar)} ₺
                    </span>
                  </td>
                  <td className="t-right">
                    <button
                      type="button"
                      className="btn btn-ghost"
                      disabled={sil.isPending}
                      onClick={() => {
                        const uyari = h.transferGrubu
                          ? 'Bu bir transferdir; iki kasadaki bacağı da silinecek. Devam?'
                          : 'Düzeltme silinecek. Devam?';
                        if (window.confirm(uyari)) sil.mutate(h.id);
                      }}
                    >
                      Sil
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
