import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import type { Role } from '../auth/roles';

/**
 * Rol şartı guard'ı. Kullanıcının {@code requiredRoles}'tan en az biri yoksa 403 ekranına
 * yönlendirir. (Rol bazlı gizleme UX; asıl yetki backend'de.)
 */
export default function RoleRoute({
  requiredRoles,
  children,
}: {
  requiredRoles: readonly Role[];
  children: ReactNode;
}) {
  const { hasAnyRole } = useAuth();
  if (!hasAnyRole(requiredRoles)) {
    return <Navigate to="/403" replace />;
  }
  return <>{children}</>;
}
