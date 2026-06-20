import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createGroup,
  getGroupById,
  getGroups,
  setGroupActive,
  updateGroup,
  type GetGroupsParams,
} from '../../api/groups';
import type { GroupInput } from '../../api/types';

/** Grup listesi sorgusu. Sayfa/filtre degisince onceki veriyi korur. */
export function useGroups(params: GetGroupsParams) {
  return useQuery({
    queryKey: ['groups', params],
    queryFn: () => getGroups(params),
    placeholderData: keepPreviousData,
  });
}

/** Tek grup sorgusu (detay/duzenleme formu). */
export function useGroup(id: number | undefined) {
  return useQuery({
    queryKey: ['group', id],
    queryFn: () => getGroupById(id as number),
    enabled: id !== undefined,
  });
}

/** Yeni grup; basarida liste tazelenir. */
export function useCreateGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: GroupInput) => createGroup(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['groups'] });
    },
  });
}

/** Grup guncelle; basarida liste ve ilgili kayit tazelenir. */
export function useUpdateGroup(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: GroupInput) => updateGroup(id, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['groups'] });
      qc.invalidateQueries({ queryKey: ['group', id] });
    },
  });
}

/** Aktiflik degistir; basarida liste ve ilgili kayit tazelenir. */
export function useSetGroupActive() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, aktif }: { id: number; aktif: boolean }) => setGroupActive(id, aktif),
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: ['groups'] });
      qc.invalidateQueries({ queryKey: ['group', vars.id] });
    },
  });
}
