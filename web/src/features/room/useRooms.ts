import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createRoom,
  getRoom,
  getRooms,
  setRoomActive,
  updateRoom,
  type GetRoomsParams,
} from '../../api/rooms';
import type { RoomInput } from '../../api/types';

/** Salon listesi sorgusu. Sayfa/filtre degisince onceki veriyi korur. */
export function useRooms(params: GetRoomsParams) {
  return useQuery({
    queryKey: ['rooms', params],
    queryFn: () => getRooms(params),
    placeholderData: keepPreviousData,
  });
}

/** Tek salon sorgusu (duzenleme formu). */
export function useRoom(id: number | undefined) {
  return useQuery({
    queryKey: ['room', id],
    queryFn: () => getRoom(id as number),
    enabled: id !== undefined,
  });
}

/** Yeni salon; basarida liste tazelenir. */
export function useCreateRoom() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: RoomInput) => createRoom(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['rooms'] });
    },
  });
}

/** Salon guncelle; basarida liste ve ilgili kayit tazelenir. */
export function useUpdateRoom(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: RoomInput) => updateRoom(id, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['rooms'] });
      qc.invalidateQueries({ queryKey: ['room', id] });
    },
  });
}

/** Aktiflik degistir; basarida liste tazelenir. */
export function useSetRoomActive() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, aktif }: { id: number; aktif: boolean }) => setRoomActive(id, aktif),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['rooms'] });
    },
  });
}
