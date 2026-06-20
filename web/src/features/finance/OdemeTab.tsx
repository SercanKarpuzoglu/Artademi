import { useState } from 'react';
import { ApiException } from '../../api/client';
import type { OdemeYontemi, PaymentInput, StudentResponse } from '../../api/types';
import { formatDate, formatMoney } from '../../lib/format';
import StudentPicker from './StudentPicker';
import { YONTEM_BADGE, YONTEM_LABEL } from './financeDisplay';
import { useAccruals, useCreatePayment, usePayments } from './useFinance';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

const PAGE_SIZE = 20;

const YONTEM_TABS: { label: string; value: OdemeYontemi | undefined }[] = [
  { label: 'Tümü', value: undefined },
  { label: 'Nakit', value: 'NAKIT' },
  { label: 'Kart', value: 'KART' },
  { label: 'Havale', value: 'HAVALE' },
];

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

export default function OdemeTab() {
  const [yontem, setYontem] = useState<OdemeYontemi | undefined>(undefined);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [page, setPage] = useState(0);

  const query = usePayments({
    yontem,
    from: from || undefined,
    to: to || undefined,
    page,
    size: PAGE_SIZE,
  });
  const payments = query.data?.data ?? [];
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
            {showForm ? 'Kapat' : '+ Yeni Ödeme'}
          </button>
        </div>
      </div>

      <div className="tabs">
        {YONTEM_TABS.map((t) => (
          <button
            key={t.label}
            type="button"
            onClick={() => {
              setYontem(t.value);
              setPage(0);
            }}
            className={`tab${yontem === t.value ? ' active' : ''}`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {showForm && <PaymentForm onDone={() => setShowForm(false)} />}

      {query.isLoading ? (
        <div className="card text-center text-ink-soft">Yükleniyor…</div>
      ) : query.isError ? (
        <div className="card text-center text-red">
          {query.error instanceof ApiException ? query.error.message : 'Bir hata oluştu'}
        </div>
      ) : payments.length === 0 ? (
        <div className="card text-center text-ink-soft">Ödeme bulunamadı</div>
      ) : (
        <>
          <div className="card">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Öğrenci</th>
                  <th>Tarih</th>
                  <th>Yöntem</th>
                  <th className="t-right">Tutar</th>
                  <th>Açıklama</th>
                </tr>
              </thead>
              <tbody>
                {payments.map((p) => (
                  <tr key={p.id}>
                    <td>
                      <b>
                        {p.ogrenci.ad} {p.ogrenci.soyad}
                      </b>
                    </td>
                    <td className="text-ink-soft">{formatDate(p.odemeTarihi)}</td>
                    <td>
                      <span className={`badge ${YONTEM_BADGE[p.odemeYontemi]}`}>
                        {YONTEM_LABEL[p.odemeYontemi]}
                      </span>
                    </td>
                    <td className="t-right">
                      <span className="amount">{formatMoney(p.tutar)} ₺</span>
                    </td>
                    <td className="text-ink-soft">{p.aciklama ?? '—'}</td>
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

function PaymentForm({ onDone }: { onDone: () => void }) {
  const createMut = useCreatePayment();

  const [student, setStudent] = useState<StudentResponse | null>(null);
  const [accrualId, setAccrualId] = useState('');
  const [tutar, setTutar] = useState('');
  const [odemeYontemi, setOdemeYontemi] = useState<OdemeYontemi>('NAKIT');
  const [odemeTarihi, setOdemeTarihi] = useState(today());
  const [aciklama, setAciklama] = useState('');

  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  // Öğrenci seçilince o öğrencinin tahakkuklari (opsiyonel eslestirme icin).
  const accrualsQuery = useAccruals(student ? { ogrenciId: student.id, size: 100 } : {});
  const studentAccruals = student ? accrualsQuery.data?.data ?? [] : [];

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    setFieldErrors({});

    if (!student) {
      setFieldErrors({ ogrenciId: 'Öğrenci seçin' });
      return;
    }
    if (!tutar.trim()) {
      setFieldErrors({ tutar: 'Tutar zorunlu' });
      return;
    }

    const payload: PaymentInput = {
      ogrenciId: student.id,
      tutar: tutar.trim(),
      odemeYontemi,
      accrualId: accrualId ? Number(accrualId) : undefined,
      odemeTarihi: odemeTarihi || undefined,
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
      <h3>Yeni Ödeme</h3>
      {formError && (
        <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
          {formError}
        </div>
      )}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Öğrenci" required error={fieldErrors.ogrenciId}>
          <StudentPicker
            selected={student}
            onSelect={(s) => {
              setStudent(s);
              setAccrualId('');
            }}
            disabled={createMut.isPending}
          />
        </Field>
        <Field label="Tahakkuk (opsiyonel)" error={fieldErrors.accrualId}>
          <select
            className={inputClass}
            value={accrualId}
            disabled={!student}
            onChange={(e) => setAccrualId(e.target.value)}
          >
            <option value="">—</option>
            {studentAccruals.map((a) => (
              <option key={a.id} value={a.id}>
                {(a.donem ?? 'Dönemsiz') + ' — ' + formatMoney(a.tutar) + ' ₺'}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Tutar (₺)" required error={fieldErrors.tutar}>
          <input className={inputClass} inputMode="decimal" value={tutar} onChange={(e) => setTutar(e.target.value)} />
        </Field>
        <Field label="Yöntem" required error={fieldErrors.odemeYontemi}>
          <select
            className={inputClass}
            value={odemeYontemi}
            onChange={(e) => setOdemeYontemi(e.target.value as OdemeYontemi)}
          >
            <option value="NAKIT">Nakit</option>
            <option value="KART">Kart</option>
            <option value="HAVALE">Havale</option>
          </select>
        </Field>
        <Field label="Ödeme Tarihi" error={fieldErrors.odemeTarihi}>
          <input
            type="date"
            className={inputClass}
            value={odemeTarihi}
            onChange={(e) => setOdemeTarihi(e.target.value)}
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
