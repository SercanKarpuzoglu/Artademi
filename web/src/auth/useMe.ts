import { useQuery } from '@tanstack/react-query';
import { getMe } from '../api/me';

/**
 * Oturum sahibinin profili (GET /api/me). AppShell ilk-parola kilidi, topbar ve profil
 * ekranı bunu kullanır. Şifre değişiminden sonra: queryClient.invalidateQueries(['me']).
 */
export function useMe() {
  return useQuery({
    queryKey: ['me'],
    queryFn: getMe,
  });
}
