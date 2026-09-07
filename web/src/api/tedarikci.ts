import { api } from './client';
import type { ApiResponse, TedarikciInput, TedarikciResponse } from './types';

export async function getTedarikciler(aktif?: boolean): Promise<TedarikciResponse[]> {
  const res = await api.get<ApiResponse<TedarikciResponse[]>>('/api/tedarikciler', {
    params: { aktif },
  });
  return res.data.data;
}

export async function createTedarikci(payload: TedarikciInput): Promise<TedarikciResponse> {
  const res = await api.post<ApiResponse<TedarikciResponse>>('/api/tedarikciler', payload);
  return res.data.data;
}

export async function updateTedarikci(
  id: number,
  payload: TedarikciInput,
): Promise<TedarikciResponse> {
  const res = await api.put<ApiResponse<TedarikciResponse>>(`/api/tedarikciler/${id}`, payload);
  return res.data.data;
}

export async function tedarikciDurum(id: number, aktif: boolean): Promise<TedarikciResponse> {
  const res = await api.patch<ApiResponse<TedarikciResponse>>(
    `/api/tedarikciler/${id}/durum`,
    null,
    { params: { aktif } },
  );
  return res.data.data;
}
