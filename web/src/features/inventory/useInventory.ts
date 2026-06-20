import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createProduct,
  getProductById,
  getProducts,
  setProductActive,
  setProductStock,
  updateProduct,
  type GetProductsParams,
} from '../../api/products';
import { createSale, getSales, type GetSalesParams } from '../../api/sales';
import type { ProductInput, SaleInput, UpdateProductInput } from '../../api/types';

/** Ürün listesi sorgusu. Sayfa/filtre degisince onceki veriyi korur. */
export function useProducts(params: GetProductsParams) {
  return useQuery({
    queryKey: ['products', params],
    queryFn: () => getProducts(params),
    placeholderData: keepPreviousData,
  });
}

/** Tek ürün sorgusu (duzenleme). */
export function useProductById(id: number | undefined) {
  return useQuery({
    queryKey: ['product', id],
    queryFn: () => getProductById(id as number),
    enabled: id !== undefined,
  });
}

/** Yeni ürün; basarida liste tazelenir. */
export function useCreateProduct() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: ProductInput) => createProduct(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['products'] });
    },
  });
}

/** Ürün guncelle; basarida liste ve ilgili kayit tazelenir. */
export function useUpdateProduct(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateProductInput) => updateProduct(id, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['products'] });
      qc.invalidateQueries({ queryKey: ['product', id] });
    },
  });
}

/** Aktiflik degistir; basarida liste tazelenir. */
export function useSetProductActive() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, aktif }: { id: number; aktif: boolean }) => setProductActive(id, aktif),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['products'] });
    },
  });
}

/** Stok ata; basarida liste tazelenir. */
export function useSetProductStock() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, stokAdedi }: { id: number; stokAdedi: number }) =>
      setProductStock(id, stokAdedi),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['products'] });
    },
  });
}

/** Satis listesi sorgusu. Sayfa/filtre degisince onceki veriyi korur. */
export function useSales(params: GetSalesParams) {
  return useQuery({
    queryKey: ['sales', params],
    queryFn: () => getSales(params),
    placeholderData: keepPreviousData,
  });
}

/** Yeni satis; basarida satis VE urun (stok degisti) listeleri tazelenir. */
export function useCreateSale() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: SaleInput) => createSale(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sales'] });
      qc.invalidateQueries({ queryKey: ['products'] });
    },
  });
}
