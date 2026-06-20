import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { MENU } from '../routes/menu';
import RoleBadge from './RoleBadge';

/**
 * Uygulama çerçevesi — design-reference.html'e BİREBİR uyar: koyu erik sidebar (.sidebar),
 * marka rozeti, bölümlü rol bazlı menü (.nav-label / .nav-btn, aktif = ahududu), altta kimlik
 * (.foot: kullanıcı + rol rozeti + çıkış) ve .main içerik alanı (sayfalar kendi .topbar'ını verir).
 * Menü öğeleri kullanıcının rollerine göre süzülür (sadece UX; asıl yetki backend'de).
 */
export default function AppShell() {
  const { username, primary, hasAnyRole, logout } = useAuth();
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

        <nav className="flex flex-col gap-1">
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

        <div className="foot">
          <div className="font-semibold text-white/90">{username}</div>
          {primary && (
            <div className="mt-1.5">
              <RoleBadge role={primary} />
            </div>
          )}
          <button
            type="button"
            onClick={logout}
            className="mt-2.5 w-full rounded-lg border border-white/15 px-3 py-1.5 text-[12px] font-semibold text-white/80 transition hover:bg-white/10 focus-visible:outline focus-visible:outline-2 focus-visible:outline-rasp"
          >
            Çıkış
          </button>
        </div>
      </aside>

      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
