import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiException } from '../../api/client';
import type { TeacherResponse } from '../../api/types';
import { useBranches } from '../branch/useBranches';
import { TeacherFormValues, teacherSchema, toPayload } from './teacherSchema';
import { useCreateTeacher, useTeacher, useUpdateTeacher } from './useTeachers';

const EMPTY: TeacherFormValues = {
  ad: '',
  soyad: '',
  telefon: '',
  email: '',
  keycloakUserId: '',
  hakedisTipi: 'SAATLIK',
  saatlikUcret: '',
  ciroOrani: '',
  bransIds: [],
};

function toFormValues(t: TeacherResponse): TeacherFormValues {
  const v = (x: string | null) => x ?? '';
  const money = (x: string | number | null) =>
    x === null || x === undefined ? '' : String(x);
  return {
    ad: t.ad,
    soyad: t.soyad,
    telefon: v(t.telefon),
    email: v(t.email),
    keycloakUserId: v(t.keycloakUserId),
    hakedisTipi: t.hakedisTipi,
    saatlikUcret: money(t.saatlikUcret),
    ciroOrani: money(t.ciroOrani),
    bransIds: t.branslar.map((b) => b.id),
  };
}

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

export default function TeacherForm() {
  const params = useParams<{ id?: string }>();
  const id = params.id ? Number(params.id) : undefined;
  const isEdit = id !== undefined;
  const navigate = useNavigate();

  const [formError, setFormError] = useState<string | null>(null);

  const teacherQuery = useTeacher(id);
  const createMut = useCreateTeacher();
  const updateMut = useUpdateTeacher(id ?? 0);

  // Branş seçimi icin aktif branşlar.
  const branchQuery = useBranches({ aktif: true, size: 200 });
  const branchOptions = branchQuery.data?.data ?? [];

  const {
    register,
    handleSubmit,
    reset,
    watch,
    control,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<TeacherFormValues>({
    resolver: zodResolver(teacherSchema),
    defaultValues: EMPTY,
  });

  useEffect(() => {
    if (isEdit && teacherQuery.data) {
      reset(toFormValues(teacherQuery.data));
    }
  }, [isEdit, teacherQuery.data, reset]);

  const hakedisTipi = watch('hakedisTipi');

  async function onSubmit(values: TeacherFormValues) {
    setFormError(null);
    try {
      const payload = toPayload(values);
      if (isEdit) {
        await updateMut.mutateAsync(payload);
      } else {
        await createMut.mutateAsync(payload);
      }
      navigate('/ogretmenler');
    } catch (e) {
      if (e instanceof ApiException) {
        if (e.code === 'VALIDATION_ERROR' && e.fields) {
          for (const [field, message] of Object.entries(e.fields)) {
            setError(field as keyof TeacherFormValues, { message });
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

  if (isEdit && teacherQuery.isLoading) {
    return <p className="py-12 text-center text-gray-500">Yükleniyor…</p>;
  }
  if (isEdit && teacherQuery.isError) {
    return (
      <p className="py-12 text-center text-red-700">
        {teacherQuery.error instanceof ApiException
          ? teacherQuery.error.message
          : 'Öğretmen yüklenemedi'}
      </p>
    );
  }

  return (
    <div className="mx-auto max-w-3xl">
      <div className="topbar">
        <div>
          <h1>{isEdit ? 'Öğretmen Düzenle' : 'Yeni Öğretmen'}</h1>
          <div className="sub">Öğretmen bilgileri, branşlar ve hakediş</div>
        </div>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {formError && (
          <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
            {formError}
          </div>
        )}

        <section className="card space-y-4">
          <h2 className="text-sm font-semibold text-gray-700">Kişisel Bilgiler</h2>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="Ad" required error={errors.ad?.message}>
              <input className={inputClass} {...register('ad')} />
            </Field>
            <Field label="Soyad" required error={errors.soyad?.message}>
              <input className={inputClass} {...register('soyad')} />
            </Field>
            <Field label="Telefon" error={errors.telefon?.message}>
              <input className={inputClass} {...register('telefon')} />
            </Field>
            <Field label="E-posta" error={errors.email?.message}>
              <input className={inputClass} type="email" {...register('email')} />
            </Field>
            <Field label="Keycloak Kullanıcı ID" error={errors.keycloakUserId?.message}>
              <input className={inputClass} {...register('keycloakUserId')} />
            </Field>
          </div>
        </section>

        <section className="card space-y-4">
          <h2 className="text-sm font-semibold text-gray-700">Hakediş</h2>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="Hakediş Tipi" required error={errors.hakedisTipi?.message}>
              <select className={inputClass} {...register('hakedisTipi')}>
                <option value="SAATLIK">Saatlik</option>
                <option value="CIRO_ORANI">Cirodan (Oran)</option>
              </select>
            </Field>
            {hakedisTipi === 'SAATLIK' ? (
              <Field label="Saatlik Ücret (₺)" required error={errors.saatlikUcret?.message}>
                <input className={inputClass} inputMode="decimal" {...register('saatlikUcret')} />
              </Field>
            ) : (
              <Field label="Ciro Oranı (%)" required error={errors.ciroOrani?.message}>
                <input className={inputClass} inputMode="decimal" {...register('ciroOrani')} />
              </Field>
            )}
          </div>
        </section>

        <section className="card space-y-4">
          <h2 className="text-sm font-semibold text-gray-700">Branşlar</h2>
          {branchQuery.isLoading ? (
            <p className="text-sm text-ink-soft">Branşlar yükleniyor…</p>
          ) : branchOptions.length === 0 ? (
            <p className="text-sm text-ink-soft">Tanımlı aktif branş yok.</p>
          ) : (
            <Controller
              control={control}
              name="bransIds"
              render={({ field }) => (
                <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
                  {branchOptions.map((b) => {
                    const checked = field.value.includes(b.id);
                    return (
                      <label
                        key={b.id}
                        className="flex items-center gap-2 text-sm text-gray-700"
                      >
                        <input
                          type="checkbox"
                          className="h-4 w-4"
                          checked={checked}
                          onChange={(e) => {
                            if (e.target.checked) {
                              field.onChange([...field.value, b.id]);
                            } else {
                              field.onChange(field.value.filter((x) => x !== b.id));
                            }
                          }}
                        />
                        {b.ad}
                      </label>
                    );
                  })}
                </div>
              )}
            />
          )}
          {errors.bransIds?.message && (
            <p className="text-xs text-red-600">{errors.bransIds.message}</p>
          )}
        </section>

        <div className="flex justify-end gap-3">
          <button type="button" className="btn btn-ghost" onClick={() => navigate('/ogretmenler')}>
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
