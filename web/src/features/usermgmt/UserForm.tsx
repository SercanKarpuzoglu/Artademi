import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiException } from '../../api/client';
import type { UserResponse } from '../../api/types';
import { ASSIGNABLE_ROLES, roleLabel } from './userDisplay';
import { toCreatePayload, toUpdatePayload, userSchema, type UserFormValues } from './userSchema';
import { useCreateUser, useUpdateUser, useUser } from './useUsers';

/** Yeni kullanıcının alacağı sabit ilk parola (backend tarafında atanır). */
const ILK_PAROLA = 'Artademi2026!';

const EMPTY: UserFormValues = {
  kullaniciAdi: '',
  ad: '',
  soyad: '',
  email: '',
  telefon: '',
  roller: [],
};

function toFormValues(u: UserResponse): UserFormValues {
  return {
    kullaniciAdi: u.kullaniciAdi,
    ad: u.ad,
    soyad: u.soyad,
    email: u.email ?? '',
    telefon: u.telefon ?? '',
    // Sadece atanabilir rolleri forma al (SUPER_ADMIN vb. checkbox listesinde yok).
    roller: u.roller.filter((r) => (ASSIGNABLE_ROLES as readonly string[]).includes(r)),
  };
}

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

export default function UserForm() {
  const params = useParams<{ id?: string }>();
  const id = params.id;
  const isEdit = id !== undefined;
  const navigate = useNavigate();

  const [formError, setFormError] = useState<string | null>(null);

  const userQuery = useUser(id);
  const createMut = useCreateUser();
  const updateMut = useUpdateUser(id ?? '');

  const {
    register,
    handleSubmit,
    reset,
    control,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<UserFormValues>({
    resolver: zodResolver(userSchema),
    defaultValues: EMPTY,
  });

  useEffect(() => {
    if (isEdit && userQuery.data) {
      reset(toFormValues(userQuery.data));
    }
  }, [isEdit, userQuery.data, reset]);

  function applyServerError(e: unknown) {
    if (e instanceof ApiException) {
      if (e.code === 'CONFLICT') {
        setFormError('Kullanıcı adı veya e-posta zaten kayıtlı.');
      } else if (e.code === 'VALIDATION_ERROR' && e.fields) {
        for (const [field, message] of Object.entries(e.fields)) {
          setError(field as keyof UserFormValues, { message });
        }
        setFormError('Lütfen işaretli alanları düzeltin.');
      } else {
        setFormError(e.message);
      }
    } else {
      setFormError('Beklenmeyen bir hata oluştu.');
    }
  }

  async function onSubmit(values: UserFormValues) {
    setFormError(null);
    try {
      if (isEdit) {
        await updateMut.mutateAsync(toUpdatePayload(values));
        navigate('/kullanicilar');
      } else {
        await createMut.mutateAsync(toCreatePayload(values));
        // İlk parola admine açıkça gösterilmeli — banner ile bilgilendirip listeye dön.
        navigate('/kullanicilar', {
          state: {
            createdNotice: `Kullanıcı oluşturuldu. İlk parola: ${ILK_PAROLA} — kullanıcıya iletin, ilk girişte değiştirecek.`,
          },
        });
      }
    } catch (e) {
      applyServerError(e);
    }
  }

  if (isEdit && userQuery.isLoading) {
    return <p className="py-12 text-center text-ink-soft">Yükleniyor…</p>;
  }
  if (isEdit && userQuery.isError) {
    return (
      <p className="py-12 text-center text-red">
        {userQuery.error instanceof ApiException ? userQuery.error.message : 'Kullanıcı yüklenemedi'}
      </p>
    );
  }

  return (
    <div className="mx-auto max-w-3xl">
      <div className="topbar">
        <div>
          <h1>{isEdit ? 'Kullanıcı Düzenle' : 'Yeni Kullanıcı'}</h1>
          <div className="sub">Kullanıcı bilgileri ve rolleri</div>
        </div>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {formError && (
          <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
            {formError}
          </div>
        )}

        {!isEdit && (
          <div className="rounded-[14px] border border-rasp/30 bg-rasp-soft px-4 py-3 text-[13px] text-ink">
            Kullanıcı oluşturulduğunda ilk parolası <b>{ILK_PAROLA}</b> olur. Bu parolayı kullanıcıya
            iletin; ilk girişte değiştirmek zorundadır.
          </div>
        )}

        <section className="card space-y-4">
          <h2 className="text-sm font-semibold text-ink">Kullanıcı Bilgileri</h2>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {isEdit ? (
              <Field label="Kullanıcı Adı">
                <input className={`${inputClass} bg-paper`} value={userQuery.data?.kullaniciAdi ?? ''} readOnly />
              </Field>
            ) : (
              <Field label="Kullanıcı Adı" required error={errors.kullaniciAdi?.message}>
                <input className={inputClass} {...register('kullaniciAdi')} />
              </Field>
            )}
            <Field label="E-posta" error={errors.email?.message}>
              <input className={inputClass} type="email" {...register('email')} />
            </Field>
            <Field label="Ad" required error={errors.ad?.message}>
              <input className={inputClass} {...register('ad')} />
            </Field>
            <Field label="Soyad" required error={errors.soyad?.message}>
              <input className={inputClass} {...register('soyad')} />
            </Field>
            <Field label="Telefon" error={errors.telefon?.message}>
              <input className={inputClass} {...register('telefon')} />
            </Field>
          </div>
        </section>

        <section className="card space-y-3">
          <h2 className="text-sm font-semibold text-ink">
            Roller<span className="text-red"> *</span>
          </h2>
          <Controller
            control={control}
            name="roller"
            render={({ field }) => (
              <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                {ASSIGNABLE_ROLES.map((r) => {
                  const checked = field.value.includes(r);
                  return (
                    <label
                      key={r}
                      className="flex cursor-pointer items-center gap-2 rounded-[10px] border border-line px-3 py-2 text-[13.5px] hover:border-rasp"
                    >
                      <input
                        type="checkbox"
                        className="accent-rasp"
                        checked={checked}
                        onChange={(e) => {
                          if (e.target.checked) field.onChange([...field.value, r]);
                          else field.onChange(field.value.filter((v) => v !== r));
                        }}
                      />
                      <span>{roleLabel(r)}</span>
                    </label>
                  );
                })}
              </div>
            )}
          />
          {errors.roller?.message && (
            <span className="block text-xs text-red">{errors.roller.message}</span>
          )}
        </section>

        <div className="flex justify-end gap-3">
          <button type="button" className="btn btn-ghost" onClick={() => navigate('/kullanicilar')}>
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
      <span className="mb-1 block text-sm font-medium text-ink">
        {label}
        {required && <span className="text-red"> *</span>}
      </span>
      {children}
      {error && <span className="mt-1 block text-xs text-red">{error}</span>}
    </label>
  );
}
