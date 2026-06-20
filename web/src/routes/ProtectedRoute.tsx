import type { ReactNode } from 'react';
import { keycloak } from '../lib/keycloak';

/**
 * Giriş şartı guard'ı. keycloak-js {@code login-required} ile başlatıldığından pratikte
 * kullanıcı her zaman authenticated'tir; yine de savunma katmanı olarak dursun: oturum
 * yoksa Keycloak login'e yönlendirir.
 */
export default function ProtectedRoute({ children }: { children: ReactNode }) {
  if (!keycloak.authenticated) {
    void keycloak.login();
    return null;
  }
  return <>{children}</>;
}
