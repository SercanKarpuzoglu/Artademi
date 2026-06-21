import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { ApiException } from '../../api/client';
import { tenantSchema, type TenantFormValues } from './tenantSchema';
import { useCreateTenant } from './usePlatformTenants';

const EMPTY: TenantFormValues = { ad: '', adminEmail: '', adminAd: '', adminSoyad: '' };

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

/**
 * Yeni kurum (tenant) + ilk ADMIN olusturma formu (SUPER_ADMIN). Backend tenant'i once commit eder,
 * sonra admin'i provision eder; sonuc (username / olası uyari) listeye taşinip banner'da gosterilir.
 * error.fields -> input alti (kanonik kalip); 409 (mukerrer ad) -> form ustu hata kutusu.
 */
export default function TenantForm() {
  const navigate = useNavigate();
  const createMut = useCreateTenant();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<TenantFormValues>({
    resolver: zodResolver(tenantSchema),
    defaultValues: EMPTY,
  });

  function applyServerError(e: unknown) {
    if (e instanceof ApiException) {
      if (e.code === 'CONFLICT') {
        setFormError('Bu ada sahip bir kurum zaten var.');
      } else if (e.code === 'VALIDATION_ERROR' && e.fields) {
        for (const [field, message] of Object.entries(e.fields)) {
          setError(field as keyof TenantFormValues, { message });
        }
        setFormError('Lütfen işaretli alanları düzeltin.');
      } else {
        setFormError(e.message);
      }
    } else {
      setFormError('Beklenmeyen bir hata oluştu.');
    }
  }

  async function onSubmit(values: TenantFormValues) {
    setFormError(null);
    try {
      const result = await createMut.mutateAsync({
        ad: values.ad.trim(),
        adminEmail: values.adminEmail.trim(),
        adminAd: values.adminAd.trim(),
        adminSoyad: values.adminSoyad.trim(),
      });
      // Provisioning sonucunu (username/uyarı) listede banner olarak göster.
      navigate('/platform/tenants', { state: { result } });
    } catch (e) {
      applyServerError(e);
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <div className="topbar">
        <div>
          <h1>Yeni Kurum</h1>
          <div className="sub">Tenant açılır ve ilk yönetici (admin) kullanıcısı oluşturulur</div>
        </div>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {formError && (
          <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
            {formError}
          </div>
        )}

        <div className="rounded-[14px] border border-rasp/30 bg-rasp-soft px-4 py-3 text-[13px] text-ink">
          İlk yönetici, sabit parola <b>Artademi2026!</b> ile oluşturulur ve ilk girişte değiştirmek
          zorundadır. Oluşturulan kullanıcı adı sonraki ekranda gösterilecektir.
        </div>

        <section className="card space-y-4">
          <h2 className="text-sm font-semibold text-ink">Kurum</h2>
          <Field label="Kurum Adı" required error={errors.ad?.message}>
            <input className={inputClass} {...register('ad')} />
          </Field>
        </section>

        <section className="card space-y-4">
          <h2 className="text-sm font-semibold text-ink">İlk Yönetici</h2>
          <Field label="E-posta" required error={errors.adminEmail?.message}>
            <input className={inputClass} type="email" {...register('adminEmail')} />
          </Field>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="Ad" required error={errors.adminAd?.message}>
              <input className={inputClass} {...register('adminAd')} />
            </Field>
            <Field label="Soyad" required error={errors.adminSoyad?.message}>
              <input className={inputClass} {...register('adminSoyad')} />
            </Field>
          </div>
        </section>

        <div className="flex justify-end gap-3">
          <button
            type="button"
            className="btn btn-ghost"
            onClick={() => navigate('/platform/tenants')}
          >
            İptal
          </button>
          <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
            {isSubmitting ? 'Oluşturuluyor…' : 'Kurumu Oluştur'}
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
