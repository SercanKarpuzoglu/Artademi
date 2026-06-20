/** ISO tarih (YYYY-MM-DD) -> TR (gg.aa.yyyy). Bos/null -> "—". */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  const [y, m, d] = iso.split('-');
  return y && m && d ? `${d}.${m}.${y}` : iso;
}

/**
 * Para degerini (number|string|null) defansif bicimde gosterir; bos -> "—".
 * Backend BigDecimal'i JSON'da sayi VEYA string olarak gelebilir; ikisini de tolere eder.
 * (Birden cok feature kullandigi icin ortak lib'te durur — feature'a gomulmez.)
 */
export function formatMoney(v: string | number | null | undefined): string {
  if (v === null || v === undefined || v === '') {
    return '—';
  }
  const n = typeof v === 'number' ? v : Number(String(v).replace(',', '.'));
  if (Number.isNaN(n)) {
    return String(v);
  }
  return n.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
