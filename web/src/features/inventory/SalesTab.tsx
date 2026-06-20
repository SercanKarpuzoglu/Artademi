import { useState } from 'react';
import { ApiException } from '../../api/client';
import type { ProductResponse, SaleInput, StudentResponse } from '../../api/types';
import { formatDate, formatMoney } from '../../lib/format';
import StudentPicker from '../finance/StudentPicker';
import { useCreateSale, useProducts, useSales } from './useInventory';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

const PAGE_SIZE = 20;

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

export default function SalesTab() {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [page, setPage] = useState(0);

  const query = useSales({
    from: from || undefined,
    to: to || undefined,
    page,
    size: PAGE_SIZE,
  });
  const sales = query.data?.data ?? [];
  const meta = query.data?.meta;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3">
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-gray-700">Başlangıç</span>
          <input
            type="date"
            className={inputClass}
            value={from}
            onChange={(e) => {
              setFrom(e.target.value);
              setPage(0);
            }}
          />
        </label>
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-gray-700">Bitiş</span>
          <input
            type="date"
            className={inputClass}
            value={to}
            onChange={(e) => {
              setTo(e.target.value);
              setPage(0);
            }}
          />
        </label>
        <div className="ml-auto">
          <button type="button" className="btn btn-primary" onClick={() => setShowForm((v) => !v)}>
            {showForm ? 'Kapat' : '+ Yeni Satış'}
          </button>
        </div>
      </div>

      {showForm && <SaleForm onDone={() => setShowForm(false)} />}

      {query.isLoading ? (
        <div className="card text-center text-ink-soft">Yükleniyor…</div>
      ) : query.isError ? (
        <div className="card text-center text-red">
          {query.error instanceof ApiException ? query.error.message : 'Bir hata oluştu'}
        </div>
      ) : sales.length === 0 ? (
        <div className="card text-center text-ink-soft">Satış bulunamadı</div>
      ) : (
        <>
          <div className="card">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Tarih</th>
                  <th>Ürün</th>
                  <th>Öğrenci</th>
                  <th className="t-right">Adet</th>
                  <th className="t-right">Birim Fiyat</th>
                  <th className="t-right">Toplam</th>
                </tr>
              </thead>
              <tbody>
                {sales.map((s) => (
                  <tr key={s.id}>
                    <td className="text-ink-soft">{formatDate(s.satisTarihi)}</td>
                    <td>
                      <b>{s.urun.ad}</b>
                    </td>
                    <td className="text-ink-soft">
                      {s.ogrenci ? `${s.ogrenci.ad} ${s.ogrenci.soyad}` : '—'}
                    </td>
                    <td className="t-right">{s.adet}</td>
                    <td className="t-right">
                      <span className="amount">{formatMoney(s.birimFiyat)} ₺</span>
                    </td>
                    <td className="t-right">
                      <span className="amount">{formatMoney(s.toplamTutar)} ₺</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {meta && (
            <div className="flex items-center justify-between text-[13px] text-ink-soft">
              <span>
                Toplam {meta.totalElements} · Sayfa {meta.page + 1}/{Math.max(meta.totalPages, 1)}
              </span>
              <div className="flex gap-2">
                <button
                  type="button"
                  className="btn btn-ghost disabled:opacity-40"
                  onClick={() => setPage((p) => Math.max(p - 1, 0))}
                  disabled={meta.page <= 0}
                >
                  Önceki
                </button>
                <button
                  type="button"
                  className="btn btn-ghost disabled:opacity-40"
                  onClick={() => setPage((p) => p + 1)}
                  disabled={meta.page + 1 >= meta.totalPages}
                >
                  Sonraki
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function SaleForm({ onDone }: { onDone: () => void }) {
  const createMut = useCreateSale();
  // Satis icin yalnizca aktif urunler secilebilir (genis sayfa: tek istek).
  const productsQuery = useProducts({ aktif: true, size: 200 });
  const products = productsQuery.data?.data ?? [];

  const [urunId, setUrunId] = useState('');
  const [student, setStudent] = useState<StudentResponse | null>(null);
  const [adet, setAdet] = useState('1');
  const [satisTarihi, setSatisTarihi] = useState(today());
  const [aciklama, setAciklama] = useState('');

  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const selectedProduct: ProductResponse | undefined = products.find(
    (p) => String(p.id) === urunId,
  );
  const adetNum = Number(adet);
  const tahminiToplam =
    selectedProduct && Number.isFinite(adetNum) && adetNum > 0
      ? Number(String(selectedProduct.satisFiyati).replace(',', '.')) * adetNum
      : null;

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    setFieldErrors({});

    const errs: Record<string, string> = {};
    if (!urunId) {
      errs.urunId = 'Ürün seçin';
    }
    if (!Number.isInteger(adetNum) || adetNum <= 0) {
      errs.adet = 'Adet pozitif tam sayı olmalı';
    }
    if (Object.keys(errs).length > 0) {
      setFieldErrors(errs);
      return;
    }

    const payload: SaleInput = {
      urunId: Number(urunId),
      ogrenciId: student?.id,
      adet: adetNum,
      satisTarihi: satisTarihi || undefined,
      aciklama: aciklama.trim() || undefined,
    };

    try {
      await createMut.mutateAsync(payload);
      onDone();
    } catch (err) {
      if (err instanceof ApiException) {
        if (err.code === 'VALIDATION_ERROR' && err.fields) {
          setFieldErrors(err.fields);
          setFormError('Lütfen işaretli alanları düzeltin.');
        } else {
          // Yetersiz stok 409 (CONFLICT) ve diger hatalar: backend mesaji gosterilir.
          setFormError(err.message);
        }
      } else {
        setFormError('Beklenmeyen bir hata oluştu.');
      }
    }
  }

  return (
    <form onSubmit={onSubmit} className="card space-y-4" noValidate>
      <h3>Yeni Satış</h3>
      {formError && (
        <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
          {formError}
        </div>
      )}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Ürün" required error={fieldErrors.urunId}>
          <select className={inputClass} value={urunId} onChange={(e) => setUrunId(e.target.value)}>
            <option value="">Ürün seçin…</option>
            {products.map((p) => (
              <option key={p.id} value={p.id}>
                {p.ad} — {formatMoney(p.satisFiyati)} ₺ ({p.stokAdedi} adet)
              </option>
            ))}
          </select>
        </Field>
        <Field label="Öğrenci (opsiyonel)" error={fieldErrors.ogrenciId}>
          <StudentPicker
            selected={student}
            onSelect={setStudent}
            disabled={createMut.isPending}
          />
        </Field>
        <Field label="Adet" required error={fieldErrors.adet}>
          <input
            type="number"
            min={1}
            step={1}
            className={inputClass}
            value={adet}
            onChange={(e) => setAdet(e.target.value)}
          />
        </Field>
        <Field label="Satış Tarihi" error={fieldErrors.satisTarihi}>
          <input
            type="date"
            className={inputClass}
            value={satisTarihi}
            onChange={(e) => setSatisTarihi(e.target.value)}
          />
        </Field>
        <Field label="Açıklama" error={fieldErrors.aciklama}>
          <input
            className={inputClass}
            value={aciklama}
            onChange={(e) => setAciklama(e.target.value)}
          />
        </Field>
      </div>
      {tahminiToplam !== null && (
        <div className="text-[13px] text-ink-soft">
          Tahmini toplam: <span className="amount">{formatMoney(tahminiToplam)} ₺</span>
        </div>
      )}
      <div className="flex justify-end gap-3">
        <button type="button" className="btn btn-ghost" onClick={onDone}>
          İptal
        </button>
        <button type="submit" className="btn btn-primary" disabled={createMut.isPending}>
          {createMut.isPending ? 'Kaydediliyor…' : 'Kaydet'}
        </button>
      </div>
    </form>
  );
}

function Field({
  label,
  error,
  required,
  children,
}: {
  label: string;
  error?: string;
  required?: boolean;
  children: React.ReactNode;
}) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-gray-700">
        {label}
        {required && <span className="text-red-600"> *</span>}
      </span>
      {children}
      {error && <span className="mt-1 block text-xs text-red-600">{error}</span>}
    </label>
  );
}
