import { api } from './client';
import type { ApiResponse } from './types';

/** Geri bildirim tipi — backend FeedbackTipi. */
export type FeedbackTipi = 'HATA' | 'ONERI' | 'DESTEK' | 'DIGER';

export interface FeedbackInput {
  tip: FeedbackTipi;
  mesaj: string;
  eposta?: string;
}

/** Uygulama içinden geri bildirim gönderir; info@artademi.com'a iletilir. */
export async function sendFeedback(payload: FeedbackInput): Promise<void> {
  await api.post<ApiResponse<void>>('/api/feedback', payload);
}
