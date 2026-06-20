import { api } from './client';
import type { ApiResponse, ExpenseInput, ExpenseResponse } from './types';

export interface GetExpensesParams {
  from?: string;
  to?: string;
  kategori?: string;
  page?: number;
  size?: number;
}

/** Gider listesi (sayfali). Zarfin tamamini dondurur (data + meta). Tenant JWT'den okunur. */
export async function getExpenses(
  params: GetExpensesParams = {},
): Promise<ApiResponse<ExpenseResponse[]>> {
  const res = await api.get<ApiResponse<ExpenseResponse[]>>('/api/expenses', { params });
  return res.data;
}

/** Yeni gider olusturur. */
export async function createExpense(payload: ExpenseInput): Promise<ExpenseResponse> {
  const res = await api.post<ApiResponse<ExpenseResponse>>('/api/expenses', payload);
  return res.data.data;
}
