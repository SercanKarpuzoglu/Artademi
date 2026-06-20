/** Düşük stok esigi (bu degerin altinda/esitinde amber uyari). */
export const DUSUK_STOK_ESIGI = 5;

export interface StockBadge {
  /** badge sinifi VEYA null (null ise sade sayi gosterilir). */
  className: string | null;
  label: string;
}

/**
 * Stok adedine gore rozet bilgisi: 0 -> kirmizi "Stok yok", <=esik -> amber "{n} adet",
 * aksi halde sade sayi (className null). Rozet sinif eslemeleri burada (feature'a degil bilesene degil).
 */
export function stockBadge(stokAdedi: number): StockBadge {
  if (stokAdedi === 0) {
    return { className: 'b-red', label: 'Stok yok' };
  }
  if (stokAdedi <= DUSUK_STOK_ESIGI) {
    return { className: 'b-amber', label: `${stokAdedi} adet` };
  }
  return { className: null, label: String(stokAdedi) };
}
