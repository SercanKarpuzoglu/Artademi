import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createBranch,
  getBranch,
  getBranches,
  setBranchActive,
  updateBranch,
  type GetBranchesParams,
} from '../../api/branches';
import type { BranchInput } from '../../api/types';

/** Branş listesi sorgusu. Sayfa/filtre degisince onceki veriyi korur. */
export function useBranches(params: GetBranchesParams) {
  return useQuery({
    queryKey: ['branches', params],
    queryFn: () => getBranches(params),
    placeholderData: keepPreviousData,
  });
}

/** Tek branş sorgusu (duzenleme formu). */
export function useBranch(id: number | undefined) {
  return useQuery({
    queryKey: ['branch', id],
    queryFn: () => getBranch(id as number),
    enabled: id !== undefined,
  });
}

/** Yeni branş; basarida liste tazelenir. */
export function useCreateBranch() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: BranchInput) => createBranch(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['branches'] });
    },
  });
}

/** Branş guncelle; basarida liste ve ilgili kayit tazelenir. */
export function useUpdateBranch(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: BranchInput) => updateBranch(id, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['branches'] });
      qc.invalidateQueries({ queryKey: ['branch', id] });
    },
  });
}

/** Aktiflik degistir; basarida liste tazelenir. */
export function useSetBranchActive() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, aktif }: { id: number; aktif: boolean }) => setBranchActive(id, aktif),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['branches'] });
    },
  });
}
