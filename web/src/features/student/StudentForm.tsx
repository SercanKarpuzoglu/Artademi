import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiException } from '../../api/client';
import type { StudentResponse } from '../../api/types';
import { StudentFormValues, studentSchema, toPayload } from './studentSchema';
import { useCreateStudent, useStudent, useUpdateStudent } from './useStudentMutations';

const EMPTY: StudentFormValues = {
  ad: '',
  soyad: '',
  tcKimlikNo: '',
  dogumTarihi: '',
  telefon: '',
  yetiskinMi: false,
  anneAd: '',
  anneTcKimlikNo: '',
  anneTelefon: '',
  babaAd: '',
  babaTcKimlikNo: '',
  babaTelefon: '',
  veliMeslek: '',
  evAdresi: '',
  veliMail: '',
};

/** StudentResponse -> form degerleri (null -> ''). */
function toFormValues(s: StudentResponse): StudentFormValues {
  const v = (x: string | null) => x ?? '';
  return {
    ad: s.ad,
    soyad: s.soyad,
    tcKimlikNo: s.tcKimlikNo,
    dogumTarihi: s.dogumTarihi,
    telefon: v(s.telefon),
    yetiskinMi: s.yetiskinMi,
    anneAd: v(s.anneAd),
    anneTcKimlikNo: v(s.anneTcKimlikNo),
    anneTelefon: v(s.anneTelefon),
    babaAd: v(s.babaAd),
    babaTcKimlikNo: v(s.babaTcKimlikNo),
    babaTelefon: v(s.babaTelefon),
    veliMeslek: v(s.veliMeslek),
    evAdresi: v(s.evAdresi),
    veliMail: v(s.veliMail),
  };
}

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

export default function StudentForm() {
  const params = useParams<{ id?: string }>();
  const id = params.id ? Number(params.id) : undefined;
  const isEdit = id !== undefined;
  const navigate = useNavigate();

  const [formError, setFormError] = useState<string | null>(null);

  const studentQuery = useStudent(id);
  const createMut = useCreateStudent();
  const updateMut = useUpdateStudent(id ?? 0);

  const {
    register,
    handleSubmit,
    reset,
    watch,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<StudentFormValues>({
    resolver: zodResolver(studentSchema),
    defaultValues: EMPTY,
  });

  // Duzenlemede kayit gelince formu doldur.
  useEffect(() => {
    if (isEdit && studentQuery.data) {
      reset(toFormValues(studentQuery.data));
    }
  }, [isEdit, studentQuery.data, reset]);

  const yetiskin = watch('yetiskinMi');
  // superRefine 'veli' yoluna hata baglar; FieldErrors tipinde olmadigi icin cast ile okunur.
  const veliError = (errors as Record<string, { message?: string } | undefined>).veli?.message;

  async function onSubmit(values: StudentFormValues) {
    setFormError(null);
    try {
      const payload = toPayload(values);
      if (isEdit) {
        await updateMut.mutateAsync(payload);
      } else {
        await createMut.mutateAsync(payload);
      }
      navigate('/ogrenciler');
    } catch (e) {
      if (e instanceof ApiException) {
        if (e.code === 'VALIDATION_ERROR' && e.fields) {
          // Sunucu alan hatalarini ilgili input'a bagla (backend alan adlari form ile ayni).
          for (const [field, message] of Object.entries(e.fields)) {
            setError(field as keyof StudentFormValues, { message });
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

  if (isEdit && studentQuery.isLoading) {
    return <p className="py-12 text-center text-gray-500">Yükleniyor…</p>;
  }
  if (isEdit && studentQuery.isError) {
    return (
      <p className="py-12 text-center text-red-700">
        {studentQuery.error instanceof ApiException ? studentQuery.error.message : 'Öğrenci yüklenemedi'}
      </p>
    );
  }

  return (
    <div className="mx-auto max-w-3xl">
      <div className="topbar">
        <div>
          <h1>{isEdit ? 'Öğrenci Düzenle' : 'Yeni Öğrenci'}</h1>
          <div className="sub">Öğrenci ve veli bilgileri</div>
        </div>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          {formError && (
            <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
              {formError}
            </div>
          )}

          {/* Temel bilgiler */}
          <section className="card space-y-4">
            <h2 className="text-sm font-semibold text-gray-700">Öğrenci Bilgileri</h2>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Field label="Ad" required error={errors.ad?.message}>
                <input className={inputClass} {...register('ad')} />
              </Field>
              <Field label="Soyad" required error={errors.soyad?.message}>
                <input className={inputClass} {...register('soyad')} />
              </Field>
              <Field label="TC Kimlik No" required error={errors.tcKimlikNo?.message}>
                <input className={inputClass} inputMode="numeric" maxLength={11} {...register('tcKimlikNo')} />
              </Field>
              <Field label="Doğum Tarihi" required error={errors.dogumTarihi?.message}>
                <input type="date" className={inputClass} {...register('dogumTarihi')} />
              </Field>
              <Field label="Telefon" error={errors.telefon?.message}>
                <input className={inputClass} {...register('telefon')} />
              </Field>
              <label className="flex items-center gap-2 self-end pb-2 text-sm text-gray-700">
                <input type="checkbox" className="h-4 w-4" {...register('yetiskinMi')} />
                Yetişkin öğrenci (veli zorunlu değil)
              </label>
            </div>
          </section>

          {/* Veli bilgileri */}
          <section className="card space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold text-gray-700">Veli Bilgileri</h2>
              {!yetiskin && <span className="text-xs text-gray-500">En az bir veli (ad + TC) zorunlu</span>}
            </div>
            {veliError && <p className="text-xs text-red-600">{veliError}</p>}

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <Field label="Anne Ad" error={errors.anneAd?.message}>
                <input className={inputClass} {...register('anneAd')} />
              </Field>
              <Field label="Anne TC" error={errors.anneTcKimlikNo?.message}>
                <input className={inputClass} inputMode="numeric" maxLength={11} {...register('anneTcKimlikNo')} />
              </Field>
              <Field label="Anne Telefon" error={errors.anneTelefon?.message}>
                <input className={inputClass} {...register('anneTelefon')} />
              </Field>
              <Field label="Baba Ad" error={errors.babaAd?.message}>
                <input className={inputClass} {...register('babaAd')} />
              </Field>
              <Field label="Baba TC" error={errors.babaTcKimlikNo?.message}>
                <input className={inputClass} inputMode="numeric" maxLength={11} {...register('babaTcKimlikNo')} />
              </Field>
              <Field label="Baba Telefon" error={errors.babaTelefon?.message}>
                <input className={inputClass} {...register('babaTelefon')} />
              </Field>
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <Field label="Veli Meslek" error={errors.veliMeslek?.message}>
                <input className={inputClass} {...register('veliMeslek')} />
              </Field>
              <Field label="Veli E-posta" error={errors.veliMail?.message}>
                <input className={inputClass} {...register('veliMail')} />
              </Field>
              <Field label="Ev Adresi" error={errors.evAdresi?.message}>
                <input className={inputClass} {...register('evAdresi')} />
              </Field>
            </div>
          </section>

          <div className="flex justify-end gap-3">
            <button type="button" className="btn btn-ghost" onClick={() => navigate('/ogrenciler')}>
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
