import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createAccrual, getAccruals, uret, uretOnizle, type GetAccrualsParams } from '../../api/accruals';
import { createExpense, getExpenses, type GetExpensesParams } from '../../api/expenses';
import { getStudentBalance, getStudentFinance } from '../../api/finance';
import { createPayment, getPayments, type GetPaymentsParams } from '../../api/payments';
import type {
  AccrualGenerationResult,
  AccrualInput,
  ExpenseInput,
  PaymentInput,
} from '../../api/types';

// --- Tahakkuk ---

/** Tahakkuk listesi sorgusu. Sayfa/filtre degisince onceki veriyi korur. */
export function useAccruals(params: GetAccrualsParams) {
  return useQuery({
    queryKey: ['accruals', params],
    queryFn: () => getAccruals(params),
    placeholderData: keepPreviousData,
  });
}

/** Yeni tahakkuk; basarida tahakkuk listesi tazelenir. */
export function useCreateAccrual() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: AccrualInput) => createAccrual(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['accruals'] });
    },
  });
}

// --- Ödeme ---

/** Ödeme listesi sorgusu. */
export function usePayments(params: GetPaymentsParams) {
  return useQuery({
    queryKey: ['payments', params],
    queryFn: () => getPayments(params),
    placeholderData: keepPreviousData,
  });
}

/** Yeni ödeme; basarida ödeme listesi (ve etkilenen bakiye/tahakkuk) tazelenir. */
export function useCreatePayment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: PaymentInput) => createPayment(payload),
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: ['payments'] });
      qc.invalidateQueries({ queryKey: ['accruals'] });
      qc.invalidateQueries({ queryKey: ['studentBalance', vars.ogrenciId] });
      qc.invalidateQueries({ queryKey: ['studentFinance', vars.ogrenciId] });
    },
  });
}

// --- Gider ---

/** Gider listesi sorgusu. */
export function useExpenses(params: GetExpensesParams) {
  return useQuery({
    queryKey: ['expenses', params],
    queryFn: () => getExpenses(params),
    placeholderData: keepPreviousData,
  });
}

/** Yeni gider; basarida gider listesi tazelenir. */
export function useCreateExpense() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: ExpenseInput) => createExpense(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['expenses'] });
    },
  });
}

// --- Öğrenci bakiye / finans özeti ---

/** Tek öğrenci bakiye sorgusu (öğrenci detay finans karti). */
export function useStudentBalance(studentId: number | undefined) {
  return useQuery({
    queryKey: ['studentBalance', studentId],
    queryFn: () => getStudentBalance(studentId as number),
    enabled: studentId !== undefined,
  });
}

/** Tek öğrenci finans özeti sorgusu (tahakkuklar + ödemeler). */
export function useStudentFinance(studentId: number | undefined) {
  return useQuery({
    queryKey: ['studentFinance', studentId],
    queryFn: () => getStudentFinance(studentId as number),
    enabled: studentId !== undefined,
  });
}

// --- Otomatik aylik tahakkuk (ADMIN) ---

/** Önizleme mutasyonu — KAYIT OLUSTURMAZ, yalnizca sonuc doner. */
export function useUretOnizle() {
  return useMutation<AccrualGenerationResult, unknown, string>({
    mutationFn: (donem: string) => uretOnizle(donem),
  });
}

/** Üretim mutasyonu — kayit olusturur; basarida tahakkuk listesi tazelenir. */
export function useUret() {
  const qc = useQueryClient();
  return useMutation<AccrualGenerationResult, unknown, string>({
    mutationFn: (donem: string) => uret(donem),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['accruals'] });
    },
  });
}
