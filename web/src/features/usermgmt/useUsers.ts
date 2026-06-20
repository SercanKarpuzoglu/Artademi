import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createUser,
  deleteUser,
  getUser,
  getUsers,
  setUserActive,
  updateUser,
  type GetUsersParams,
} from '../../api/users';
import type { CreateUserInput, UpdateUserInput } from '../../api/types';

/** Kullanıcı listesi sorgusu. Filtre değişince önceki veriyi korur. */
export function useUsers(params: GetUsersParams) {
  return useQuery({
    queryKey: ['users', params],
    queryFn: () => getUsers(params),
    placeholderData: keepPreviousData,
  });
}

/** Tek kullanıcı sorgusu (düzenleme formu). */
export function useUser(id: string | undefined) {
  return useQuery({
    queryKey: ['user', id],
    queryFn: () => getUser(id as string),
    enabled: id !== undefined,
  });
}

/** Yeni kullanıcı; başarıda liste tazelenir. */
export function useCreateUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateUserInput) => createUser(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] });
    },
  });
}

/** Kullanıcı güncelle; başarıda liste ve ilgili kayıt tazelenir. */
export function useUpdateUser(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateUserInput) => updateUser(id, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] });
      qc.invalidateQueries({ queryKey: ['user', id] });
    },
  });
}

/** Aktiflik değiştir; başarıda liste tazelenir. */
export function useSetUserActive() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, aktif }: { id: string; aktif: boolean }) => setUserActive(id, aktif),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] });
    },
  });
}

/** Kullanıcı sil; başarıda liste tazelenir. */
export function useDeleteUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteUser(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] });
    },
  });
}
