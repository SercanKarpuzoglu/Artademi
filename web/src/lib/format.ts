/** ISO tarih (YYYY-MM-DD) -> TR (gg.aa.yyyy). Bos/null -> "—". */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  const [y, m, d] = iso.split('-');
  return y && m && d ? `${d}.${m}.${y}` : iso;
}
