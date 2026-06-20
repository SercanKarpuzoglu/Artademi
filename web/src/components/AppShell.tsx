import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { z } from 'zod';
import { changePassword } from '../api/me';
import { ApiException } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useMe } from '../auth/useMe';
import { MENU } from '../routes/menu';
import RoleBadge from './RoleBadge';

/**
 * Uygulama çerçevesi. İki kademe:
 *  1) İLK-PAROLA KİLİDİ — meQuery.data.mustChangePassword true ise SADECE kilit ekranı render edilir
 *     (sidebar/nav/Outlet YOK) → kullanıcı URL ile hiçbir modüle ulaşamaz.
 *  2) NORMAL ÇERÇEVE — koyu erik sidebar (marka + rol bazlı nav) + üstte ince topbar (kullanıcı,
 *     rol rozeti, Profil, Çıkış) + .main içerik (sayfalar kendi .topbar başlığını verir).
 * Kimlik bloğu artık sidebar .foot'tan ÇIKARILDI; topbar'a taşındı.
 */
export default function AppShell() {
  const { username, primary, hasAnyRole, logout } = useAuth();
  const meQuery = useMe();

  if (meQuery.isLoading) {
    return (
      <div className="grid min-h-screen place-items-center bg-paper text-ink-soft">Yükleniyor…</div>
    );
  }

  if (meQuery.data?.mustChangePassword) {
    return <FirstPasswordLock onLogout={logout} />;
  }

  const gorunur = MENU.filter((m) => hasAnyRole(m.roles));

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">
          <div className="mark">A</div>
          <div>
            <b>Artademi</b>
            <small>Yönetim Paneli</small>
          </div>
        </div>

        <nav className="flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto">
          {gorunur.map((m, i) => {
            const Icon = m.icon;
            const yeniBolum = i === 0 || gorunur[i - 1].section !== m.section;
            return (
              <div key={m.path}>
                {yeniBolum && <div className="nav-label">{m.section}</div>}
                <NavLink
                  to={m.path}
                  className={({ isActive }) => `nav-btn${isActive ? ' active' : ''}`}
                >
                  <span className="ico">
                    <Icon size={17} strokeWidth={1.75} />
                  </span>
                  <span className="flex-1">{m.label}</span>
                  {!m.hazir && (
                    <span className="rounded bg-white/10 px-1.5 py-0.5 text-[10px] font-normal text-white/55">
                      Yakında
                    </span>
                  )}
                </NavLink>
              </div>
            );
          })}
        </nav>
      </aside>

      <div className="flex min-h-screen flex-col">
        {/* İnce üst bar: solda kurum (tenant) adı (/api/me.tenantAdi), sağda kimlik + Profil/Çıkış */}
        <header className="flex items-center justify-between border-b border-line bg-card px-7 py-2.5">
          {/* Tenant adı yoksa slot gizli (sahte ad yazılmaz). */}
          {meQuery.data?.tenantAdi ? (
            <span className="font-fraunces text-[15px] font-semibold text-ink">
              {meQuery.data.tenantAdi}
            </span>
          ) : (
            <span />
          )}
          <div className="flex items-center gap-3">
            <span className="text-[13px] font-semibold text-ink">{username}</span>
            {primary && <RoleBadge role={primary} />}
            <Link to="/profil" className="btn btn-ghost">
              Profil
            </Link>
            <button type="button" className="btn btn-ghost" onClick={logout}>
              Çıkış
            </button>
          </div>
        </header>

        <main className="main">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

const lockSchema = z
  .object({
    mevcutParola: z.string().min(1, 'Mevcut parola zorunludur'),
    yeniParola: z.string().min(8, 'En az 8 karakter olmalı'),
    yeniParolaTekrar: z.string().min(1, 'Tekrar zorunludur'),
  })
  .superRefine((v, ctx) => {
    if (v.yeniParola !== v.yeniParolaTekrar) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['yeniParolaTekrar'],
        message: 'Parolalar eşleşmiyor',
      });
    }
  });

type LockFormValues = z.infer<typeof lockSchema>;

const lockInputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

/**
 * İlk-parola kilit ekranı: yalnızca Çıkış'lı ince bir bar + ortalanmış "İlk Şifre Belirleme" formu.
 * Başarıda ['me'] invalidate edilir → kilit serbest kalır → normal çerçeve render olur.
 */
function FirstPasswordLock({ onLogout }: { onLogout: () => void }) {
  const qc = useQueryClient();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LockFormValues>({
    resolver: zodResolver(lockSchema),
    defaultValues: { mevcutParola: '', yeniParola: '', yeniParolaTekrar: '' },
  });

  async function onSubmit(values: LockFormValues) {
    setFormError(null);
    try {
      await changePassword({ mevcutParola: values.mevcutParola, yeniParola: values.yeniParola });
      await qc.invalidateQueries({ queryKey: ['me'] });
    } catch (e) {
      if (e instanceof ApiException) {
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
    <div className="flex min-h-screen flex-col bg-paper">
      <header className="flex items-center justify-between border-b border-line bg-card px-7 py-2.5">
        <span className="font-fraunces text-[16px] font-semibold text-ink">Artademi</span>
        <button type="button" className="btn btn-ghost" onClick={onLogout}>
          Çıkış
        </button>
      </header>

      <div className="grid flex-1 place-items-center px-4 py-10">
        <form onSubmit={handleSubmit(onSubmit)} className="card w-full max-w-md space-y-4" noValidate>
          <div>
            <h1 className="font-fraunces text-[20px] font-semibold text-ink">İlk Şifre Belirleme</h1>
            <p className="mt-1 text-[13px] text-ink-soft">
              Devam etmeden önce parolanızı değiştirmelisiniz. İlk parolanız:{' '}
              <b>Artademi2026!</b>
            </p>
          </div>

          {formError && (
            <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
              {formError}
            </div>
          )}

          <LockField label="Mevcut Parola" error={errors.mevcutParola?.message}>
            <input
              className={lockInputClass}
              type="password"
              autoComplete="current-password"
              {...register('mevcutParola')}
            />
          </LockField>
          <LockField label="Yeni Parola" error={errors.yeniParola?.message}>
            <input
              className={lockInputClass}
              type="password"
              autoComplete="new-password"
              {...register('yeniParola')}
            />
          </LockField>
          <LockField label="Yeni Parola (Tekrar)" error={errors.yeniParolaTekrar?.message}>
            <input
              className={lockInputClass}
              type="password"
              autoComplete="new-password"
              {...register('yeniParolaTekrar')}
            />
          </LockField>

          <button type="submit" className="btn btn-primary w-full" disabled={isSubmitting}>
            {isSubmitting ? 'Kaydediliyor…' : 'Şifreyi Belirle'}
          </button>
        </form>
      </div>
    </div>
  );
}

function LockField({
  label,
  error,
  children,
}: {
  label: string;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-ink">
        {label}
        <span className="text-red"> *</span>
      </span>
      {children}
      {error && <span className="mt-1 block text-xs text-red">{error}</span>}
    </label>
  );
}
