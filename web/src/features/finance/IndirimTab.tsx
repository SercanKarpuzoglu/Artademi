import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ApiException } from '../../api/client';
import { createIndirim, getIndirimler, indirimDurum, updateIndirim } from '../../api/indirim';
import type { IndirimInput, IndirimResponse, IndirimTipi } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Role } from '../../auth/roles';
import SilButonu from '../../components/SilButonu';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

/**
 * İndirim / kampanya tanımları (Dalga D): "Kardeş indirimi %15", "Nakit ödeme %10", "Burs 500 ₺".
 * Tanım burada; öğrenciye özel uygulama öğrenci sayfasındaki Finans kartından. Tahakkuk üretiminde
 * brüt − indirim = net yazılır.
 */
export default function IndirimTab() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole(Role.ADMIN);
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['indirimler'], queryFn: () => getIndirimler() });
  const [form, setForm] = useState(false);
  const [editing, setEditing] = useState<IndirimResponse | null>(null);
  const durum = useMutation({
    mutationFn: ({ id, aktif }: { id: number; aktif: boolean }) => indirimDurum(id, aktif),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['indirimler'] }),
  });
  const liste = q.data ?? [];

  return (
    <div className="space-y-4">
      <div className="card space-y-2">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3>İndirim / Kampanya Tanımları</h3>
            <p className="text-[13px] text-ink-soft">
              Tanımı burada yapın; öğrenciye özel uygulamak için öğrenci sayfasındaki Finans kartını
              kullanın. Otomatik tahakkukta brüt ücretten düşülür; oranlar toplanır, tutarlar eklenir.
            </p>
          </div>
          {isAdmin && (
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => {
                setEditing(null);
                setForm((v) => !v);
              }}
            >
              {form && !editing ? 'Kapat' : '+ Yeni İndirim'}
            </button>
          )}
        </div>
      </div>

      {isAdmin && form && (
        <IndirimForm
          editing={editing}
          onDone={() => {
            setForm(false);
            setEditing(null);
          }}
        />
      )}

      {q.isLoading ? (
        <div className="card text-center text-ink-soft">Yükleniyor…</div>
      ) : q.isError ? (
        <div className="card text-center text-red">
          {q.error instanceof ApiException ? q.error.message : 'Bir hata oluştu'}
        </div>
      ) : liste.length === 0 ? (
        <div className="card text-center text-ink-soft">Henüz indirim tanımı yok</div>
      ) : (
        <div className="card">
          <table className="data-table">
            <thead>
              <tr>
                <th>Ad</th>
                <th>Tip</th>
                <th className="t-right">Değer</th>
                <th>Durum</th>
                {isAdmin && <th className="t-right">İşlem</th>}
              </tr>
            </thead>
            <tbody>
              {liste.map((i) => (
                <tr key={i.id} className={i.aktif ? '' : 'opacity-60'}>
                  <td>
                    <b>{i.ad}</b>
                    {i.aciklama && <div className="text-[12px] text-ink-soft">{i.aciklama}</div>}
                  </td>
                  <td className="text-ink-soft">{i.tip === 'ORAN' ? 'Oran (%)' : 'Tutar (₺)'}</td>
                  <td className="t-right">
                    <span className="amount">{i.etiket}</span>
                  </td>
                  <td>
                    <span className={`badge ${i.aktif ? 'b-green' : 'b-gray'}`}>{i.aktif ? 'Aktif' : 'Pasif'}</span>
                  </td>
                  {isAdmin && (
                    <td className="t-right">
                      <div className="flex justify-end gap-2">
                        <button
                          type="button"
                          className="btn btn-ghost"
                          onClick={() => {
                            setEditing(i);
                            setForm(true);
                          }}
                        >
                          Düzenle
                        </button>
                        <button
                          type="button"
                          className="btn btn-ghost"
                          disabled={durum.isPending}
                          onClick={() => durum.mutate({ id: i.id, aktif: !i.aktif })}
                        >
                          {i.aktif ? 'Pasifleştir' : 'Aktifleştir'}
                        </button>
                        <SilButonu tur="indirim" id={i.id} ad={i.ad} />
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function IndirimForm({ editing, onDone }: { editing: IndirimResponse | null; onDone: () => void }) {
  const qc = useQueryClient();
  const [ad, setAd] = useState(editing?.ad ?? '');
  const [tip, setTip] = useState<IndirimTipi>(editing?.tip ?? 'ORAN');
  const [deger, setDeger] = useState(editing ? String(editing.deger) : '');
  const [aciklama, setAciklama] = useState(editing?.aciklama ?? '');
  const [hata, setHata] = useState<string | null>(null);
  const mut = useMutation({
    mutationFn: (payload: IndirimInput) =>
      editing ? updateIndirim(editing.id, payload) : createIndirim(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['indirimler'] });
      onDone();
    },
    onError: (e) => setHata(e instanceof ApiException ? e.message : 'Kaydedilemedi.'),
  });

  function submit(e: React.FormEvent) {
    e.preventDefault();
    setHata(null);
    const d = Number(deger.replace(',', '.'));
    if (!ad.trim()) return setHata('Ad zorunlu');
    if (!deger.trim() || Number.isNaN(d) || d <= 0) return setHata('Değer 0\'dan büyük olmalı');
    if (tip === 'ORAN' && d > 100) return setHata('Oran %100\'ü aşamaz');
    mut.mutate({ ad: ad.trim(), tip, deger: deger.replace(',', '.'), aciklama: aciklama.trim() || undefined });
  }

  return (
    <form onSubmit={submit} className="card space-y-4" noValidate>
      <h3>{editing ? 'İndirimi Düzenle' : 'Yeni İndirim'}</h3>
      {hata && (
        <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
          {hata}
        </div>
      )}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-gray-700">Ad <span className="text-red">*</span></span>
          <input className={inputClass} value={ad} onChange={(e) => setAd(e.target.value)} placeholder="Kardeş indirimi" />
        </label>
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-gray-700">Tip</span>
          <select className={inputClass} value={tip} onChange={(e) => setTip(e.target.value as IndirimTipi)}>
            <option value="ORAN">Oran (%)</option>
            <option value="TUTAR">Tutar (₺)</option>
          </select>
        </label>
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-gray-700">
            {tip === 'ORAN' ? 'Oran (%)' : 'Tutar (₺)'} <span className="text-red">*</span>
          </span>
          <input className={inputClass} inputMode="decimal" value={deger} onChange={(e) => setDeger(e.target.value)} placeholder={tip === 'ORAN' ? '15' : '500'} />
        </label>
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-gray-700">Açıklama</span>
          <input className={inputClass} value={aciklama} onChange={(e) => setAciklama(e.target.value)} />
        </label>
      </div>
      <div className="flex justify-end gap-3">
        <button type="button" className="btn btn-ghost" onClick={onDone}>İptal</button>
        <button type="submit" className="btn btn-primary" disabled={mut.isPending}>
          {mut.isPending ? 'Kaydediliyor…' : 'Kaydet'}
        </button>
      </div>
    </form>
  );
}
