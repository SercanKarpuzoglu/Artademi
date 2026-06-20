import { api } from './client';
import type { ApiResponse, ProductInput, ProductResponse, UpdateProductInput } from './types';

export interface GetProductsParams {
  aktif?: boolean;
  q?: string;
  page?: number;
  size?: number;
}

/** Ürün listesi (sayfali). Zarfin tamamini dondurur (data + meta). Tenant JWT'den okunur. */
export async function getProducts(
  params: GetProductsParams = {},
): Promise<ApiResponse<ProductResponse[]>> {
  const res = await api.get<ApiResponse<ProductResponse[]>>('/api/products', { params });
  return res.data;
}

/** Tek ürün (detay/duzenleme icin). */
export async function getProductById(id: number): Promise<ProductResponse> {
  const res = await api.get<ApiResponse<ProductResponse>>(`/api/products/${id}`);
  return res.data.data;
}

/** Yeni ürün olusturur (backend aktif=true baslatir). YALNIZCA ADMIN. */
export async function createProduct(payload: ProductInput): Promise<ProductResponse> {
  const res = await api.post<ApiResponse<ProductResponse>>('/api/products', payload);
  return res.data.data;
}

/** Ürün gunceller (stok/aktiflik bu uctan degismez). YALNIZCA ADMIN. */
export async function updateProduct(
  id: number,
  payload: UpdateProductInput,
): Promise<ProductResponse> {
  const res = await api.put<ApiResponse<ProductResponse>>(`/api/products/${id}`, payload);
  return res.data.data;
}

/** Aktiflik durumunu degistirir. YALNIZCA ADMIN. */
export async function setProductActive(id: number, aktif: boolean): Promise<ProductResponse> {
  const res = await api.patch<ApiResponse<ProductResponse>>(`/api/products/${id}/active`, { aktif });
  return res.data.data;
}

/** Stok adedini MUTLAK olarak atar (artirma/azaltma degil). YALNIZCA ADMIN. */
export async function setProductStock(id: number, stokAdedi: number): Promise<ProductResponse> {
  const res = await api.patch<ApiResponse<ProductResponse>>(`/api/products/${id}/stok`, {
    stokAdedi,
  });
  return res.data.data;
}
