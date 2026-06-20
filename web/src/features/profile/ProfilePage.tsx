import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { ApiException } from '../../api/client';
import { updateTenant } from '../../api/tenant';
import type { MeResponse } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Role } from '../../auth/roles';
import { useMe } from '../../auth/useMe';
import { roleBadgeClass, roleLabel } from '../usermgmt/userDisplay';
import {
  changePasswordSchema,
  profileSchema,
  toUpdateMePayload,
  type ChangePasswordFormValues,
  type ProfileFormValues,
} from './profileSchema';
import { useChangePassword, useUpdateMe } from './useProfile';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

export default function ProfilePage() {
  const meQuery = useMe();
  const { hasRole } = useAuth();

  if (meQuery.isLoading) {
    return <div className="card text-center text-ink-soft">Yükleniyor…</div>;
  }
  if (meQuery.isError || !meQuery.data) {
    return (
      <div className="card text-center text-red">
        {meQuery.error instanceof ApiException ? meQuery.error.message : 'Profil yüklenemedi'}
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl">
      <div className="topbar">
        <div>
          <h1>Profil</h1>
          <div className="sub">Hesap bilgileriniz ve şifreniz</div>
        </div>
      </div>

      <div className="space-y-4">
        <ProfileInfoCard me={meQuery.data} />
        {hasRole(Role.ADMIN) && <KurumAdiCard me={meQuery.data} />}
        <ChangePasswordCard />
      </div>
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

function ProfileInfoCard({ me }: { me: MeResponse }) {
  const updateMut = useUpdateMe();
  const [formError, setFormError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      ad: me.ad,
      soyad: me.soyad,
      email: me.email ?? '',
      telefon: me.telefon ?? '',
    },
  });

  useEffect(() => {
    reset({ ad: me.ad, soyad: me.soyad, email: me.email ?? '', telefon: me.telefon ?? '' });
  }, [me, reset]);

  async function onSubmit(values: ProfileFormValues) {
    setFormError(null);
    setSuccess(false);
    try {
      await updateMut.mutateAsync(toUpdateMePayload(values));
      setSuccess(true);
    } catch (e) {
      if (e instanceof ApiException && e.code === 'VALIDATION_ERROR' && e.fields) {
        for (const [field, message] of Object.entries(e.fields)) {
          setError(field as keyof ProfileFormValues, { message });
        }
        setFormError('Lütfen işaretli alanları düzeltin.');
      } else {
        setFormError(e instanceof ApiException ? e.message : 'Beklenmeyen bir hata oluştu.');
      }
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="card space-y-4" noValidate>
      <h2 className="text-sm font-semibold text-ink">Profil Bilgileri</h2>

      {formError && (
        <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
          {formError}
        </div>
      )}
      {success && (
        <div className="rounded-[12px] border border-green/30 bg-green-soft px-4 py-2.5 text-[13px] font-semibold text-green">
          Profil güncellendi.
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Kullanıcı Adı">
          <input className={`${inputClass} bg-paper`} value={me.kullaniciAdi} readOnly />
        </Field>
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

      <div>
        <span className="mb-1 block text-sm font-medium text-ink">Roller</span>
        <div className="flex flex-wrap gap-1">
          {me.roller.length === 0
            ? '—'
            : me.roller.map((r) => (
                <span key={r} className={`badge ${roleBadgeClass(r)}`}>
                  {roleLabel(r)}
                </span>
              ))}
        </div>
      </div>

      <div className="flex justify-end">
        <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
          {isSubmitting ? 'Kaydediliyor…' : 'Kaydet'}
        </button>
      </div>
    </form>
  );
}

function ChangePasswordCard() {
  const changeMut = useChangePassword();
  const [formError, setFormError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ChangePasswordFormValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { mevcutParola: '', yeniParola: '', yeniParolaTekrar: '' },
  });

  async function onSubmit(values: ChangePasswordFormValues) {
    setFormError(null);
    setSuccess(false);
    try {
      await changeMut.mutateAsync({
        mevcutParola: values.mevcutParola,
        yeniParola: values.yeniParola,
      });
      setSuccess(true);
      reset({ mevcutParola: '', yeniParola: '', yeniParolaTekrar: '' });
    } catch (e) {
      if (e instanceof ApiException) {
        // Servis doğrulamaları genelde error.fields taşımaz; mesaj mevcut parola altına basılır.
        if (e.code === 'VALIDATION_ERROR') {
          setError('mevcutParola', { message: e.message || 'Mevcut parola hatalı' });
        } else {
          setFormError(e.message);
        }
      } else {
        setFormError('Beklenmeyen bir hata oluştu.');
      }
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="card space-y-4" noValidate>
      <h2 className="text-sm font-semibold text-ink">Şifre Değiştir</h2>

      {formError && (
        <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
          {formError}
        </div>
      )}
      {success && (
        <div className="rounded-[12px] border border-green/30 bg-green-soft px-4 py-2.5 text-[13px] font-semibold text-green">
          Şifreniz değiştirildi.
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Field label="Mevcut Parola" required error={errors.mevcutParola?.message}>
          <input className={inputClass} type="password" autoComplete="current-password" {...register('mevcutParola')} />
        </Field>
        <Field label="Yeni Parola" required error={errors.yeniParola?.message}>
          <input className={inputClass} type="password" autoComplete="new-password" {...register('yeniParola')} />
        </Field>
        <Field label="Yeni Parola (Tekrar)" required error={errors.yeniParolaTekrar?.message}>
          <input className={inputClass} type="password" autoComplete="new-password" {...register('yeniParolaTekrar')} />
        </Field>
      </div>

      <div className="flex justify-end">
        <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
          {isSubmitting ? 'Değiştiriliyor…' : 'Şifreyi Değiştir'}
        </button>
      </div>
    </form>
  );
}

const kurumSchema = z.object({ ad: z.string().trim().min(1, 'Kurum adı zorunludur') });
type KurumFormValues = z.infer<typeof kurumSchema>;

/** Kurum (tenant) adı düzenleme — yalnızca ADMIN. Başarıda ['me'] invalidate → topbar anında güncellenir. */
function KurumAdiCard({ me }: { me: MeResponse }) {
  const qc = useQueryClient();
  const [formError, setFormError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<KurumFormValues>({
    resolver: zodResolver(kurumSchema),
    defaultValues: { ad: me.tenantAdi ?? '' },
  });

  useEffect(() => {
    reset({ ad: me.tenantAdi ?? '' });
  }, [me, reset]);

  const updateMut = useMutation({
    mutationFn: (values: KurumFormValues) => updateTenant({ ad: values.ad.trim() }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['me'] });
      qc.invalidateQueries({ queryKey: ['tenant'] });
    },
  });

  async function onSubmit(values: KurumFormValues) {
    setFormError(null);
    setSuccess(false);
    try {
      await updateMut.mutateAsync(values);
      setSuccess(true);
    } catch (e) {
      if (e instanceof ApiException && e.code === 'VALIDATION_ERROR' && e.fields?.ad) {
        setError('ad', { message: e.fields.ad });
      } else {
        setFormError(e instanceof ApiException ? e.message : 'Beklenmeyen bir hata oluştu.');
      }
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="card space-y-4" noValidate>
      <h2 className="text-sm font-semibold text-ink">Kurum Adı</h2>

      {formError && (
        <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
          {formError}
        </div>
      )}
      {success && (
        <div className="rounded-[12px] border border-green/30 bg-green-soft px-4 py-2.5 text-[13px] font-semibold text-green">
          Kurum adı güncellendi.
        </div>
      )}

      <Field label="Kurum Adı" required error={errors.ad?.message}>
        <input className={inputClass} {...register('ad')} />
      </Field>

      <div className="flex justify-end">
        <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
          {isSubmitting ? 'Kaydediliyor…' : 'Kaydet'}
        </button>
      </div>
    </form>
  );
}
