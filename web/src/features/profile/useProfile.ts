import { useMutation, useQueryClient } from '@tanstack/react-query';
import { changePassword, updateMe } from '../../api/me';
import type { ChangePasswordInput, UpdateMeInput } from '../../api/types';

/** Profil güncelle; başarıda ['me'] tazelenir (topbar + profil + kilit aynı veriyi okur). */
export function useUpdateMe() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateMeInput) => updateMe(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['me'] });
    },
  });
}

/** Şifre değiştir; başarıda ['me'] tazelenir (mustChangePassword kilidini serbest bırakır). */
export function useChangePassword() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: ChangePasswordInput) => changePassword(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['me'] });
    },
  });
}
