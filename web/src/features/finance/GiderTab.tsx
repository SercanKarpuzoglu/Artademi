import { useState } from 'react';
import { ApiException } from '../../api/client';
import type { ExpenseInput } from '../../api/types';
import { formatDate, formatMoney } from '../../lib/format';
import { useCreateExpense, useExpenses } from './useFinance';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

const PAGE_SIZE = 20;

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

export default function GiderTab() {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [kategori, setKategori] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [page, setPage] = useState(0);

  const query = useExpenses({
    from: from || undefined,
    to: to || undefined,
    kategori: kategori.trim() || undefined,
    page,
    size: PAGE_SIZE,
  });
  const expenses = query.data?.data ?? [];
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
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-gray-700">Kategori</span>
          <input
            className={inputClass}
            value={kategori}
            onChange={(e) => {
              setKategori(e.target.value);
              setPage(0);
            }}
          />
        </label>
        <div className="ml-auto">
          <button type="button" className="btn btn-primary" onClick={() => setShowForm((v) => !v)}>
            {showForm ? 'Kapat' : '+ Yeni Gider'}
          </button>
        </div>
      </div>

      {showForm && <ExpenseForm onDone={() => setShowForm(false)} />}

      {query.isLoading ? (
        <div className="card text-center text-ink-soft">Yükleniyor…</div>
      ) : query.isError ? (
        <div className="card text-center text-red">
          {query.error instanceof ApiException ? query.error.message : 'Bir hata oluştu'}
        </div>
      ) : expenses.length === 0 ? (
        <div className="card text-center text-ink-soft">Gider bulunamadı</div>
      ) : (
        <>
          <div className="card">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Tarih</th>
                  <th>Kategori</th>
                  <th className="t-right">Tutar</th>
                  <th>Açıklama</th>
                </tr>
              </thead>
              <tbody>
                {expenses.map((x) => (
                  <tr key={x.id}>
                    <td className="text-ink-soft">{formatDate(x.giderTarihi)}</td>
                    <td>{x.kategori ?? '—'}</td>
                    <td className="t-right">
                      <span className="amount">{formatMoney(x.tutar)} ₺</span>
                    </td>
                    <td className="text-ink-soft">{x.aciklama ?? '—'}</td>
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

function ExpenseForm({ onDone }: { onDone: () => void }) {
  const createMut = useCreateExpense();

  const [tutar, setTutar] = useState('');
  const [kategori, setKategori] = useState('');
  const [giderTarihi, setGiderTarihi] = useState(today());
  const [aciklama, setAciklama] = useState('');

  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    setFieldErrors({});

    if (!tutar.trim()) {
      setFieldErrors({ tutar: 'Tutar zorunlu' });
      return;
    }

    const payload: ExpenseInput = {
      tutar: tutar.trim(),
      kategori: kategori.trim() || undefined,
      giderTarihi: giderTarihi || undefined,
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
          setFormError(err.message);
        }
      } else {
        setFormError('Beklenmeyen bir hata oluştu.');
      }
    }
  }

  return (
    <form onSubmit={onSubmit} className="card space-y-4" noValidate>
      <h3>Yeni Gider</h3>
      {formError && (
        <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
          {formError}
        </div>
      )}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Tutar (₺)" required error={fieldErrors.tutar}>
          <input className={inputClass} inputMode="decimal" value={tutar} onChange={(e) => setTutar(e.target.value)} />
        </Field>
        <Field label="Kategori" error={fieldErrors.kategori}>
          <input className={inputClass} value={kategori} onChange={(e) => setKategori(e.target.value)} />
        </Field>
        <Field label="Gider Tarihi" error={fieldErrors.giderTarihi}>
          <input
            type="date"
            className={inputClass}
            value={giderTarihi}
            onChange={(e) => setGiderTarihi(e.target.value)}
          />
        </Field>
        <Field label="Açıklama" error={fieldErrors.aciklama}>
          <input className={inputClass} value={aciklama} onChange={(e) => setAciklama(e.target.value)} />
        </Field>
      </div>
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
