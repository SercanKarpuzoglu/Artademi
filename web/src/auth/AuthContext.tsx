import { createContext, useContext, useMemo, type ReactNode } from 'react';
import { keycloak } from '../lib/keycloak';
import { filterDomainRoles, primaryRole, type Role } from './roles';

/**
 * Oturum bilgisi: roller token'in {@code realm_access.roles} claim'inden okunur.
 * Token YALNIZCA bellekte (keycloak-js); burada sadece okunur. Rol bazli gizleme UX icindir,
 * asil yetki backend'de zorlanir.
 */
interface AuthValue {
  username: string;
  roles: Role[];
  primary: Role | null;
  hasRole: (role: Role) => boolean;
  hasAnyRole: (roles: readonly Role[]) => boolean;
  logout: () => void;
}

const AuthContext = createContext<AuthValue | null>(null);

interface TokenClaims {
  preferred_username?: string;
  realm_access?: { roles?: string[] };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const value = useMemo<AuthValue>(() => {
    const parsed = keycloak.tokenParsed as TokenClaims | undefined;
    const roles = filterDomainRoles(parsed?.realm_access?.roles ?? []);
    const roleSet = new Set<Role>(roles);
    return {
      username: parsed?.preferred_username ?? 'Kullanıcı',
      roles,
      primary: primaryRole(roles),
      hasRole: (role) => roleSet.has(role),
      hasAnyRole: (wanted) => wanted.some((r) => roleSet.has(r)),
      logout: () => keycloak.logout(),
    };
  }, []);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/** Oturum bilgisine (rol/kullanici/yetki yardimcilari) erisim. */
export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth yalnizca <AuthProvider> altinda kullanilabilir');
  }
  return ctx;
}

/** Sadece rol yardimcilari gerektiginde kisa yol. */
export function useRoles() {
  const { roles, hasRole, hasAnyRole } = useAuth();
  return { roles, hasRole, hasAnyRole };
}
