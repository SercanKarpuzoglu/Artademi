import { useQuery } from '@tanstack/react-query';
import { getPaketler } from '../../api/paket';
import { useAuth } from '../../auth/AuthContext';
import { Role } from '../../auth/roles';
import { formatDate, formatMoney } from '../../lib/format';

/**
 * Öğrenci detayında kredi kartı (Dalga E): aktif paketler (dönemlik kredi, aylık kredi, elle satış) —
 * kalan / toplam, son kullanma. Tutar yalnız para gören rollere gösterilir.
 */
export default function KrediKarti({ studentId }: { studentId: number }) {
  const { hasAnyRole } = useAuth();
  const paraGorebilir = hasAnyRole([Role.ADMIN, Role.FRONTDESK_ACCOUNTING]);
  const q = useQuery({ queryKey: ['paketler', studentId], queryFn: () => getPaketler(studentId) });
  const aktif = (q.data ?? []).filter((p) => p.durum === 'AKTIF');
  const toplamKalan = aktif.reduce((s, p) => s + p.kalanDers, 0);

  return (
    <section className="card space-y-3">
      <div className="flex items-center justify-between">
        <h3>Kredi</h3>
        {aktif.length > 0 && (
          <span className={`badge ${toplamKalan > 0 ? 'b-green' : 'b-red'}`}>{toplamKalan} ders kaldı</span>
        )}
      </div>
      {q.isLoading ? (
        <p className="text-sm text-ink-soft">Yükleniyor…</p>
      ) : aktif.length === 0 ? (
        <p className="text-sm text-ink-soft">
          Aktif kredi yok. Gruba yazılınca (aylık / dönemlik) otomatik açılır; aylık kredi her ay Otomatik
          Tahakkuk ile gelir.
        </p>
      ) : (
        <ul className="divide-y divide-line">
          {aktif.map((p) => (
            <li key={p.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-[13.5px]">
              <span>
                <b>{p.ad}</b>
                <span className="ml-2 text-ink-soft">
                  {p.sonKullanmaTarihi ? `son ${formatDate(p.sonKullanmaTarihi)}` : 'süresiz'}
                  {paraGorebilir && Number(p.tutar) > 0 && ` · ${formatMoney(p.tutar)} ₺`}
                </span>
                {p.suresiDoldu && <span className="badge b-red ml-2">Süresi doldu</span>}
              </span>
              <span className={p.kalanDers === 0 ? 'font-semibold text-red' : 'font-semibold'}>
                {p.kalanDers} / {p.toplamDers}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
