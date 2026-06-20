import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createSchedule,
  getGroupSchedules,
  setScheduleActive,
  updateSchedule,
} from '../../api/schedules';
import type { ScheduleInput } from '../../api/types';

/** Bir grubun program (ders saati) listesi sorgusu. */
export function useGroupSchedules(groupId: number | undefined) {
  return useQuery({
    queryKey: ['group', groupId, 'schedules'],
    queryFn: () => getGroupSchedules(groupId as number),
    enabled: groupId !== undefined,
  });
}

/** Yeni ders saati; basarida ilgili grubun program listesi tazelenir. */
export function useCreateSchedule(groupId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: ScheduleInput) => createSchedule(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['group', groupId, 'schedules'] });
    },
  });
}

/** Ders saati guncelle; basarida ilgili grubun program listesi tazelenir. */
export function useUpdateSchedule(groupId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: ScheduleInput }) =>
      updateSchedule(id, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['group', groupId, 'schedules'] });
    },
  });
}

/** Aktiflik degistir; basarida ilgili grubun program listesi tazelenir. */
export function useSetScheduleActive(groupId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, aktif }: { id: number; aktif: boolean }) =>
      setScheduleActive(id, aktif),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['group', groupId, 'schedules'] });
    },
  });
}
