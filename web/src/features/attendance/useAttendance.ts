import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createSession,
  getSessionById,
  getSessions,
  updateEntries,
  type GetSessionsParams,
} from '../../api/attendance';
import type {
  AttendanceGroupRef,
  CreateSessionInput,
  UpdateEntryItem,
} from '../../api/types';

/** Yoklama oturumlari sorgusu (grup/tarih filtreli). */
export function useSessions(params: GetSessionsParams) {
  return useQuery({
    queryKey: ['sessions', params],
    queryFn: () => getSessions(params),
    placeholderData: keepPreviousData,
  });
}

/** Tek oturum sorgusu (girisleri ile). */
export function useSession(id: number | undefined) {
  return useQuery({
    queryKey: ['session', id],
    queryFn: () => getSessionById(id as number),
    enabled: id !== undefined,
  });
}

/**
 * TEACHER icin "benim gruplarim": teacher /api/groups'tan 403 alir, bu yuzden gruplar
 * kendi oturumlarindan turetilir (getSessions backend'de TEACHER icin otomatik daraltilir).
 * Oturumlardaki grup referanslarindan DISTINCT bir liste kurulur.
 */
export function useTeacherGroups(enabled: boolean) {
  return useQuery({
    queryKey: ['teacherGroups'],
    queryFn: async (): Promise<AttendanceGroupRef[]> => {
      const res = await getSessions({ size: 200 });
      const byId = new Map<number, AttendanceGroupRef>();
      for (const s of res.data) {
        if (s.grup && !byId.has(s.grup.id)) {
          byId.set(s.grup.id, s.grup);
        }
      }
      return [...byId.values()].sort((a, b) => a.ad.localeCompare(b.ad, 'tr'));
    },
    enabled,
  });
}

/** Yeni oturum; basarida oturum listesi tazelenir. */
export function useCreateSession() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateSessionInput) => createSession(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sessions'] });
    },
  });
}

/** Oturum girislerini gunceller; basarida ilgili oturum ve liste tazelenir. */
export function useUpdateEntries() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ sessionId, items }: { sessionId: number; items: UpdateEntryItem[] }) =>
      updateEntries(sessionId, items),
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: ['session', vars.sessionId] });
      qc.invalidateQueries({ queryKey: ['sessions'] });
    },
  });
}
