import { useQuery } from '@tanstack/react-query';
import { getDashboard } from '../../api/dashboard';

/** Genel Bakış paneli (GET /api/dashboard). İçerik token rolüne göre değişir. */
export function useDashboard() {
  return useQuery({
    queryKey: ['dashboard'],
    queryFn: getDashboard,
  });
}
