/**
 * ISO tarih -> TR (gg.aa.yyyy). Bos/null -> "—".
 *
 * ⚠️ Hem `YYYY-MM-DD` (LocalDate) hem `2026-09-07T09:30:47Z` (Instant) kabul eder:
 * saat kismi ONCE atilir. Aksi halde Instant verildiginde `-` ile bolme sonucu
 * "07T09:30:47.326471Z.09.2026" gibi bozuk bir metin uretiliyordu. Saati de gostermek
 * icin {@link formatDateTime} kullanin.
 */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  const [y, m, d] = iso.slice(0, 10).split('-');
  return y && m && d ? `${d}.${m}.${y}` : iso;
}

/**
 * ISO instant -> TR tarih + saat (gg.aa.yyyy ss:dd). Bos/null -> "—".
 *
 * Tarayicinin yerel saat dilimine cevirir: backend UTC (`Z`) gonderir, kullanici kendi
 * saatini gormeli ("bugun 12:30" ile "09:30" arasindaki fark kafa karistirir).
 */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  const t = new Date(iso);
  if (Number.isNaN(t.getTime())) {
    return formatDate(iso);
  }
  return t.toLocaleString('tr-TR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
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
