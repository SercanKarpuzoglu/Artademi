import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiException } from '../../api/client';
import { useCreateDonem, useDonem, useUpdateDonem } from './useDonemler';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

/** Dönem oluştur / düzenle (YALNIZ ADMIN). Bitiş başlangıçtan sonra olmalı. */
export default function DonemForm() {
  const params = useParams<{ id: string }>();
  const id = params.id ? Number(params.id) : undefined;
  const navigate = useNavigate();
  const mevcut = useDonem(id);
  const createMut = useCreateDonem();
  const updateMut = useUpdateDonem(id ?? 0);
  const [ad, setAd] = useState('');
  const [baslangic, setBaslangic] = useState('');
  const [bitis, setBitis] = useState('');
  const [hata, setHata] = useState<string | null>(null);

  useEffect(() => {
    if (mevcut.data) {
      setAd(mevcut.data.ad);
      setBaslangic(mevcut.data.baslangic);
      setBitis(mevcut.data.bitis);
    }
  }, [mevcut.data]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setHata(null);
    if (!ad.trim()) return setHata('Ad zorunlu');
    if (!baslangic || !bitis) return setHata('Başlangıç ve bitiş zorunlu');
    if (bitis <= baslangic) return setHata('Bitiş başlangıçtan sonra olmalı');
    try {
      const payload = { ad: ad.trim(), baslangic, bitis };
      if (id) await updateMut.mutateAsync(payload);
      else await createMut.mutateAsync(payload);
      navigate('/donemler');
    } catch (err) {
      setHata(err instanceof ApiException ? err.message : 'Kaydedilemedi.');
    }
  }

  const pending = createMut.isPending || updateMut.isPending;

  return (
    <div className="mx-auto max-w-xl">
      <div className="topbar">
        <div>
          <h1>{id ? 'Dönemi Düzenle' : 'Yeni Dönem'}</h1>
          <div className="sub">Tarih aralığı, dönemlik kayıtta ders sayısını (krediyi) belirler</div>
        </div>
      </div>
      <form onSubmit={submit} className="card space-y-4" noValidate>
        {hata && (
          <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
            {hata}
          </div>
        )}
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-gray-700">Ad <span className="text-red">*</span></span>
          <input className={inputClass} value={ad} onChange={(e) => setAd(e.target.value)} placeholder="2026-27 Güz" />
        </label>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-gray-700">Başlangıç <span className="text-red">*</span></span>
            <input type="date" className={inputClass} value={baslangic} onChange={(e) => setBaslangic(e.target.value)} />
          </label>
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-gray-700">Bitiş <span className="text-red">*</span></span>
            <input type="date" className={inputClass} value={bitis} onChange={(e) => setBitis(e.target.value)} />
          </label>
        </div>
        <div className="flex justify-end gap-3">
          <button type="button" className="btn btn-ghost" onClick={() => navigate('/donemler')}>
            İptal
          </button>
          <button type="submit" className="btn btn-primary" disabled={pending}>
            {pending ? 'Kaydediliyor…' : 'Kaydet'}
          </button>
        </div>
      </form>
    </div>
  );
}
