import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createDonem, getDonem, getDonemler, setDonemActive, updateDonem } from '../../api/donemler';
import type { DonemInput } from '../../api/types';

export function useDonemler(aktif?: boolean) {
  return useQuery({ queryKey: ['donemler', aktif ?? 'hepsi'], queryFn: () => getDonemler(aktif) });
}

export function useDonem(id: number | undefined) {
  return useQuery({
    queryKey: ['donem', id],
    queryFn: () => getDonem(id as number),
    enabled: id !== undefined,
  });
}

export function useCreateDonem() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: DonemInput) => createDonem(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['donemler'] }),
  });
}

export function useUpdateDonem(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: DonemInput) => updateDonem(id, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['donemler'] });
      qc.invalidateQueries({ queryKey: ['donem', id] });
    },
  });
}

export function useSetDonemActive() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, aktif }: { id: number; aktif: boolean }) => setDonemActive(id, aktif),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['donemler'] }),
  });
}
