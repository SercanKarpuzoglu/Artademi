import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiException } from '../../api/client';
import type { BranchResponse } from '../../api/types';
import { BranchFormValues, branchSchema, toPayload } from './branchSchema';
import { useBranch, useCreateBranch, useUpdateBranch } from './useBranches';

const EMPTY: BranchFormValues = { ad: '', aciklama: '' };

function toFormValues(b: BranchResponse): BranchFormValues {
  return { ad: b.ad, aciklama: b.aciklama ?? '' };
}

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

export default function BranchForm() {
  const params = useParams<{ id?: string }>();
  const id = params.id ? Number(params.id) : undefined;
  const isEdit = id !== undefined;
  const navigate = useNavigate();

  const [formError, setFormError] = useState<string | null>(null);

  const branchQuery = useBranch(id);
  const createMut = useCreateBranch();
  const updateMut = useUpdateBranch(id ?? 0);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<BranchFormValues>({
    resolver: zodResolver(branchSchema),
    defaultValues: EMPTY,
  });

  useEffect(() => {
    if (isEdit && branchQuery.data) {
      reset(toFormValues(branchQuery.data));
    }
  }, [isEdit, branchQuery.data, reset]);

  async function onSubmit(values: BranchFormValues) {
    setFormError(null);
    try {
      const payload = toPayload(values);
      if (isEdit) {
        await updateMut.mutateAsync(payload);
      } else {
        await createMut.mutateAsync(payload);
      }
      navigate('/branslar');
    } catch (e) {
      if (e instanceof ApiException) {
        if (e.code === 'VALIDATION_ERROR' && e.fields) {
          for (const [field, message] of Object.entries(e.fields)) {
            setError(field as keyof BranchFormValues, { message });
          }
          setFormError('Lütfen işaretli alanları düzeltin.');
        } else {
          setFormError(`${e.message}`);
        }
      } else {
        setFormError('Beklenmeyen bir hata oluştu.');
      }
    }
  }

  if (isEdit && branchQuery.isLoading) {
    return <p className="py-12 text-center text-gray-500">Yükleniyor…</p>;
  }
  if (isEdit && branchQuery.isError) {
    return (
      <p className="py-12 text-center text-red-700">
        {branchQuery.error instanceof ApiException ? branchQuery.error.message : 'Branş yüklenemedi'}
      </p>
    );
  }

  return (
    <div className="mx-auto max-w-3xl">
      <div className="topbar">
        <div>
          <h1>{isEdit ? 'Branş Düzenle' : 'Yeni Branş'}</h1>
          <div className="sub">Branş bilgileri</div>
        </div>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {formError && (
          <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
            {formError}
          </div>
        )}

        <section className="card space-y-4">
          <h2 className="text-sm font-semibold text-gray-700">Branş Bilgileri</h2>
          <div className="grid grid-cols-1 gap-4">
            <Field label="Ad" required error={errors.ad?.message}>
              <input className={inputClass} {...register('ad')} />
            </Field>
            <Field label="Açıklama" error={errors.aciklama?.message}>
              <textarea className={inputClass} rows={3} {...register('aciklama')} />
            </Field>
          </div>
        </section>

        <div className="flex justify-end gap-3">
          <button type="button" className="btn btn-ghost" onClick={() => navigate('/branslar')}>
            İptal
          </button>
          <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
            {isSubmitting ? 'Kaydediliyor…' : 'Kaydet'}
          </button>
        </div>
      </form>
    </div>
  );
}

interface FieldProps {
  label: string;
  error?: string;
  required?: boolean;
  children: React.ReactNode;
}

function Field({ label, error, required, children }: FieldProps) {
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
