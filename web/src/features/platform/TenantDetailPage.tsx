import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { ApiException } from '../../api/client';
import type { PlatformTenant, PlatformTenantUser } from '../../api/types';
import { ASSIGNABLE_ROLES, roleBadgeClass, roleLabel } from '../usermgmt/userDisplay';
import { toCreatePayload, userSchema, type UserFormValues } from '../usermgmt/userSchema';
import {
  useCreateTenantUser,
  useDeleteTenantUser,
  useTenantUsers,
} from './usePlatformTenants';

const ILK_PAROLA = 'Artademi2026!';

const STATUS_BADGE: Record<string, { cls: string; label: string }> = {
  AKTIF: { cls: 'b-green', label: 'Aktif' },
  ASKIDA: { cls: 'b-red', label: 'Askıda' },
  SILINDI: { cls: 'b-gray', label: 'Silindi' },
};

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

const EMPTY: UserFormValues = {
  kullaniciAdi: '',
  ad: '',
  soyad: '',
  email: '',
  telefon: '',
  roller: [],
};

interface DetailLocationState {
  tenant?: PlatformTenant;
}

export default function TenantDetailPage() {
  const { id } = useParams<{ id: string }>();
  const tenantId = id as string;
  const navigate = useNavigate();
  const location = useLocation();
  const tenant = (location.state as DetailLocationState | null)?.tenant;

  const usersQuery = useTenantUsers(tenantId);
  const deleteUserMut = useDeleteTenantUser(tenantId);

  const [showForm, setShowForm] = useState(false);
  const [createdNotice, setCreatedNotice] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const users = usersQuery.data ?? [];

  async function handleDeleteUser(u: PlatformTenantUser) {
    if (!window.confirm(`"${u.kullaniciAdi}" kullanıcısı silinecek. Emin misiniz?`)) return;
    setActionError(null);
    try {
      await deleteUserMut.mutateAsync(u.id);
    } catch (e) {
      setActionError(e instanceof ApiException ? e.message : 'Kullanıcı silinemedi');
    }
  }

  return (
    <>
      <div className="topbar">
        <div>
          <button
            type="button"
            className="mb-1 text-[12px] font-semibold text-rasp"
            onClick={() => navigate('/platform/tenants')}
          >
            ← Kurumlar
          </button>
          <h1 className="flex items-center gap-2">
            {tenant?.ad ?? 'Kurum'}
            {tenant && (
              <span className={`badge ${STATUS_BADGE[tenant.status].cls}`}>
                {STATUS_BADGE[tenant.status].label}
              </span>
            )}
          </h1>
          <div className="sub">Kurum kullanıcıları</div>
        </div>
        <div className="top-actions">
          <button type="button" className="btn btn-primary" onClick={() => setShowForm((s) => !s)}>
            {showForm ? 'Vazgeç' : '+ Kullanıcı Ekle'}
          </button>
        </div>
      </div>

      {createdNotice && (
        <div className="mb-4 flex items-start justify-between gap-4 rounded-[14px] border border-rasp/30 bg-rasp-soft px-4 py-3 text-[13px] text-ink">
          <span>{createdNotice}</span>
          <button
            type="button"
            className="shrink-0 font-semibold text-rasp"
            onClick={() => setCreatedNotice(null)}
          >
            Kapat
          </button>
        </div>
      )}

      {actionError && (
        <div className="mb-4 rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
          {actionError}
        </div>
      )}

      {showForm && (
        <AddUserForm
          tenantId={tenantId}
          onDone={(username) => {
            setShowForm(false);
            setCreatedNotice(
              `Kullanıcı oluşturuldu: ${username} / parola: ${ILK_PAROLA} (ilk girişte değiştirilecek).`,
            );
          }}
        />
      )}

      {usersQuery.isLoading ? (
        <div className="card text-center text-ink-soft">Yükleniyor…</div>
      ) : usersQuery.isError ? (
        <div className="card text-center text-red">
          {usersQuery.error instanceof ApiException ? usersQuery.error.message : 'Bir hata oluştu'}
        </div>
      ) : users.length === 0 ? (
        <div className="card text-center text-ink-soft">Bu kurumda kullanıcı yok</div>
      ) : (
        <div className="card">
          <table className="data-table">
            <thead>
              <tr>
                <th>Kullanıcı Adı</th>
                <th>Ad Soyad</th>
                <th>E-posta</th>
                <th>Roller</th>
                <th>Durum</th>
                <th className="t-right">Aksiyon</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id}>
                  <td>
                    <b>{u.kullaniciAdi}</b>
                  </td>
                  <td>
                    {u.ad} {u.soyad}
                  </td>
                  <td className="text-ink-soft">{u.email ?? '—'}</td>
                  <td>
                    <div className="flex flex-wrap gap-1">
                      {u.roller.length === 0
                        ? '—'
                        : u.roller.map((r) => (
                            <span key={r} className={`badge ${roleBadgeClass(r)}`}>
                              {roleLabel(r)}
                            </span>
                          ))}
                    </div>
                  </td>
                  <td>
                    <span className={`badge ${u.enabled ? 'b-green' : 'b-gray'}`}>
                      {u.enabled ? 'Aktif' : 'Pasif'}
                    </span>
                  </td>
                  <td className="t-right">
                    <button
                      type="button"
                      className="btn btn-ghost text-red"
                      disabled={deleteUserMut.isPending}
                      onClick={() => handleDeleteUser(u)}
                    >
                      Sil
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

function AddUserForm({
  tenantId,
  onDone,
}: {
  tenantId: string;
  onDone: (username: string) => void;
}) {
  const createMut = useCreateTenantUser(tenantId);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    control,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<UserFormValues>({
    resolver: zodResolver(userSchema),
    defaultValues: EMPTY,
  });

  async function onSubmit(values: UserFormValues) {
    setFormError(null);
    try {
      const created = await createMut.mutateAsync(toCreatePayload(values));
      onDone(created.kullaniciAdi);
    } catch (e) {
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
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="card mb-5 space-y-4" noValidate>
      <h3>Yeni Kullanıcı</h3>
      <div className="rounded-[12px] border border-rasp/30 bg-rasp-soft px-4 py-2.5 text-[13px] text-ink">
        İlk parola <b>{ILK_PAROLA}</b> olur; kullanıcı ilk girişte değiştirir.
      </div>
      {formError && (
        <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
          {formError}
        </div>
      )}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Kullanıcı Adı" required error={errors.kullaniciAdi?.message}>
          <input className={inputClass} {...register('kullaniciAdi')} />
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
        <span className="mb-1 block text-sm font-medium text-ink">
          Roller<span className="text-red"> *</span>
        </span>
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
          <span className="mt-1 block text-xs text-red">{errors.roller.message}</span>
        )}
      </div>
      <div className="flex justify-end">
        <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
          {isSubmitting ? 'Ekleniyor…' : 'Kullanıcı Ekle'}
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
      <span className="mb-1 block text-sm font-medium text-ink">
        {label}
        {required && <span className="text-red"> *</span>}
      </span>
      {children}
      {error && <span className="mt-1 block text-xs text-red">{error}</span>}
    </label>
  );
}
