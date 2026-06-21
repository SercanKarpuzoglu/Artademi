import { Outlet } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';

/**
 * Platform konsolu cercevesi (SUPER_ADMIN). Is AppShell'inden AYRI ve sidebar'siz: super.admin
 * "farkli bir yerdeyim" hissetsin. design-reference.html dili (erik-ahududu, Fraunces/Manrope,
 * .card/.data-table/.btn*) korunur; yeni tema UYDURULMAZ.
 *
 * <p>Kimlik token'dan okunur ({@code name}/{@code preferred_username}); {@code /api/me}'YE BAGIMLI
 * DEGILDIR (super.admin tenant_id'siz -> /api/me 400 TENANT_REQUIRED doner). Topbar tenant adi
 * GOSTERMEZ (super.admin'in tenant'i yok).
 */
export default function PlatformShell() {
  const { username, name, logout } = useAuth();

  return (
    <div className="flex min-h-screen flex-col bg-paper">
      <header className="flex items-center justify-between border-b border-line bg-card px-7 py-3">
        <div className="flex items-center gap-2.5">
          <div className="grid h-8 w-8 place-items-center rounded-[9px] bg-ink font-fraunces text-[15px] font-bold text-white">
            A
          </div>
          <div className="leading-tight">
            <div className="font-fraunces text-[15px] font-semibold text-ink">
              Artademi · Platform Konsolu
            </div>
            <div className="text-[11.5px] text-ink-soft">Kurum (tenant) yönetimi</div>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <div className="text-right leading-tight">
            <div className="text-[13px] font-semibold text-ink">{name ?? username}</div>
            <div className="text-[11.5px] text-ink-soft">Platform Yöneticisi</div>
          </div>
          <button type="button" className="btn btn-ghost" onClick={logout}>
            Çıkış
          </button>
        </div>
      </header>

      <main className="mx-auto w-full max-w-[1180px] px-7 py-7">
        <Outlet />
      </main>
    </div>
  );
}
