import { NavLink, Outlet } from 'react-router-dom';
import amblem from '../../assets/artademi-amblem.png';
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
/** Konsol sekmeleri — tek kaynak (yeni ops sayfalari buraya eklenir). */
const SEKMELER: ReadonlyArray<{ to: string; label: string; end?: boolean }> = [
  { to: '/platform', label: 'Genel Bakış', end: true },
  { to: '/platform/tenants', label: 'Kurumlar' },
  { to: '/platform/odemeler', label: 'Ödemeler' },
];

export default function PlatformShell() {
  const { username, name, logout } = useAuth();

  return (
    <div className="flex min-h-screen flex-col bg-paper">
      <header className="flex items-center justify-between border-b border-line bg-card px-7 py-3">
        <div className="flex items-center gap-2.5">
          <img src={amblem} alt="artademi" className="h-8 w-8 shrink-0 object-contain" />
          <div className="leading-tight">
            <div className="font-fraunces text-[15px] font-semibold text-ink">
              artademi · Platform Konsolu
            </div>
            <div className="text-[11.5px] text-ink-soft">Platform yönetimi</div>
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

      {/* Konsol navigasyonu — is AppShell'inin sidebar'i BURADA YOK (ayri agac); sade sekme seridi. */}
      <nav className="border-b border-line bg-card px-7">
        <div className="mx-auto flex max-w-[1180px] gap-1">
          {SEKMELER.map((s) => (
            <NavLink
              key={s.to}
              to={s.to}
              end={s.end}
              className={({ isActive }) =>
                `border-b-2 px-3 py-2.5 text-[13.5px] font-semibold transition-colors ${
                  isActive
                    ? 'border-rasp text-rasp'
                    : 'border-transparent text-ink-soft hover:text-ink'
                }`
              }
            >
              {s.label}
            </NavLink>
          ))}
        </div>
      </nav>

      <main className="mx-auto w-full max-w-[1180px] px-7 py-7">
        <Outlet />
      </main>
    </div>
  );
}
