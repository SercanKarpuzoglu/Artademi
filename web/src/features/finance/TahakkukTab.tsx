import { useState } from 'react';
import { ApiException } from '../../api/client';
import type { AccrualInput, StudentResponse } from '../../api/types';
import { formatMoney } from '../../lib/format';
import { useGroups } from '../group/useGroups';
import StudentPicker from './StudentPicker';
import { useAccruals, useCreateAccrual } from './useFinance';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

const PAGE_SIZE = 20;

export default function TahakkukTab() {
  const [donem, setDonem] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [page, setPage] = useState(0);

  const query = useAccruals({ donem: donem.trim() || undefined, page, size: PAGE_SIZE });
  const accruals = query.data?.data ?? [];
  const meta = query.data?.meta;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3">
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-gray-700">Dönem (YYYY-MM)</span>
          <input
            className={inputClass}
            value={donem}
            placeholder="2026-06"
            onChange={(e) => {
              setDonem(e.target.value);
              setPage(0);
            }}
          />
        </label>
        <div className="ml-auto">
          <button type="button" className="btn btn-primary" onClick={() => setShowForm((v) => !v)}>
            {showForm ? 'Kapat' : '+ Yeni Tahakkuk'}
          </button>
        </div>
      </div>

      {showForm && <AccrualForm onDone={() => setShowForm(false)} />}

      {query.isLoading ? (
        <div className="card text-center text-ink-soft">Yükleniyor…</div>
      ) : query.isError ? (
        <div className="card text-center text-red">
          {query.error instanceof ApiException ? query.error.message : 'Bir hata oluştu'}
        </div>
      ) : accruals.length === 0 ? (
        <div className="card text-center text-ink-soft">Tahakkuk bulunamadı</div>
      ) : (
        <>
          <div className="card">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Öğrenci</th>
                  <th>Dönem</th>
                  <th>Grup</th>
                  <th className="t-right">Tutar</th>
                  <th>Açıklama</th>
                </tr>
              </thead>
              <tbody>
                {accruals.map((a) => (
                  <tr key={a.id}>
                    <td>
                      <b>
                        {a.ogrenci.ad} {a.ogrenci.soyad}
                      </b>
                    </td>
                    <td className="text-ink-soft">{a.donem ?? '—'}</td>
                    <td className="text-ink-soft">{a.grup?.ad ?? '—'}</td>
                    <td className="t-right">
                      <span className="amount">{formatMoney(a.tutar)} ₺</span>
                    </td>
                    <td className="text-ink-soft">{a.aciklama ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {meta && <Pager meta={meta} onPrev={() => setPage((p) => Math.max(p - 1, 0))} onNext={() => setPage((p) => p + 1)} />}
        </>
      )}
    </div>
  );
}

function Pager({
  meta,
  onPrev,
  onNext,
}: {
  meta: { page: number; totalElements: number; totalPages: number };
  onPrev: () => void;
  onNext: () => void;
}) {
  return (
    <div className="flex items-center justify-between text-[13px] text-ink-soft">
      <span>
        Toplam {meta.totalElements} · Sayfa {meta.page + 1}/{Math.max(meta.totalPages, 1)}
      </span>
      <div className="flex gap-2">
        <button type="button" className="btn btn-ghost disabled:opacity-40" onClick={onPrev} disabled={meta.page <= 0}>
          Önceki
        </button>
        <button
          type="button"
          className="btn btn-ghost disabled:opacity-40"
          onClick={onNext}
          disabled={meta.page + 1 >= meta.totalPages}
        >
          Sonraki
        </button>
      </div>
    </div>
  );
}

function AccrualForm({ onDone }: { onDone: () => void }) {
  const createMut = useCreateAccrual();
  const groupsQuery = useGroups({ aktif: true, size: 200 });
  const groups = groupsQuery.data?.data ?? [];

  const [student, setStudent] = useState<StudentResponse | null>(null);
  const [grupId, setGrupId] = useState('');
  const [donem, setDonem] = useState('');
  const [tutar, setTutar] = useState('');
  const [aciklama, setAciklama] = useState('');

  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

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

    const payload: AccrualInput = {
      ogrenciId: student.id,
      tutar: tutar.trim(),
      grupId: grupId ? Number(grupId) : undefined,
      donem: donem.trim() || undefined,
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
      <h3>Yeni Tahakkuk</h3>
      {formError && (
        <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
          {formError}
        </div>
      )}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Öğrenci" required error={fieldErrors.ogrenciId}>
          <StudentPicker selected={student} onSelect={setStudent} disabled={createMut.isPending} />
        </Field>
        <Field label="Grup (opsiyonel)" error={fieldErrors.grupId}>
          <select className={inputClass} value={grupId} onChange={(e) => setGrupId(e.target.value)}>
            <option value="">—</option>
            {groups.map((g) => (
              <option key={g.id} value={g.id}>
                {g.ad}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Dönem (YYYY-MM)" error={fieldErrors.donem}>
          <input className={inputClass} value={donem} placeholder="2026-06" onChange={(e) => setDonem(e.target.value)} />
        </Field>
        <Field label="Tutar (₺)" required error={fieldErrors.tutar}>
          <input className={inputClass} inputMode="decimal" value={tutar} onChange={(e) => setTutar(e.target.value)} />
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
