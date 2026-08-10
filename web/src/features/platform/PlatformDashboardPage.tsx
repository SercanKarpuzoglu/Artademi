import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ApiException } from '../../api/client';
import { getPlatformDashboard } from '../../api/platform';
import type { PlatformDashboard } from '../../api/types';

/**
 * Platform Genel Bakış (SUPER_ADMIN) — platform SAHİBİNİN işletme görünümü.
 *
 * ⚠️ Buradaki tutarlar okulların BİZE ödediği platform geliridir; okulun kendi tahsilatıyla
 * karıştırılmaz (o, tenant'ın Finans modülünde).
 */
export default function PlatformDashboardPage() {
  const q = useQuery({ queryKey: ['platform', 'dashboard'], queryFn: getPlatformDashboard });

  if (q.isLoading) {
    return <div className="card text-center text-ink-soft">Yükleniyor…</div>;
  }
  if (q.isError || !q.data) {
    return (
      <div className="card text-center text-red">
        {q.error instanceof ApiException ? q.error.message : 'Genel bakış yüklenemedi'}
      </div>
    );
  }

  const d = q.data;
  return (
    <div className="space-y-5">
      <div className="topbar">
        <div>
          <h1>Genel Bakış</h1>
          <div className="sub">Platform işletme durumu</div>
        </div>
        <Link to="/platform/tenants/yeni" className="btn btn-primary">
          Yeni Kurum
        </Link>
      </div>

      <SayiKartlari d={d} />

      <div className="grid gap-4 lg:grid-cols-2">
        <DikkatKarti satirlar={d.dikkatGerektirenler} />
        <YenilemeKarti satirlar={d.yaklasanYenilemeler} />
      </div>

      <HareketKarti satirlar={d.sonHareketler} />
    </div>
  );
}

function SayiKartlari({ d }: { d: PlatformDashboard }) {
  const askida = d.kurumlar.statuBazinda.ASKIDA ?? 0;
  return (
    <div className="grid stats gap-4">
      <div className="card stat">
        <div className="label">Kurum</div>
        <div className="value">{d.kurumlar.toplam}</div>
        <div className="delta flat">
          {d.kurumlar.buAyYeni > 0 ? `Bu ay +${d.kurumlar.buAyYeni}` : 'Bu ay yeni yok'}
        </div>
      </div>
      <div className="card stat">
        <div className="label">Ödeyen kurum</div>
        <div className="value">{d.gelir.odeyenKurum}</div>
        <div className="delta flat">Deneme: {d.abonelikler.DENEME ?? 0}</div>
      </div>
      <div className="card stat">
        <div className="label">Aylık gelir</div>
        <div className="value">{paraFormat(d.gelir.aylikTekrarlayan)}</div>
        <div className="delta flat">Kurum başı {paraFormat(d.gelir.aylikPlanUcreti)}</div>
      </div>
      <div className="card stat">
        <div className="label">Askıda</div>
        <div className={`value ${askida > 0 ? 'text-red' : ''}`}>{askida}</div>
        <div className="delta flat">
          Ödeme bekleyen: {d.abonelikler.ODEME_BEKLIYOR ?? 0}
        </div>
      </div>
    </div>
  );
}

function DikkatKarti({ satirlar }: { satirlar: PlatformDashboard['dikkatGerektirenler'] }) {
  return (
    <div className="card">
      <h2 className="mb-3 font-fraunces text-[17px] font-semibold">Dikkat gerektirenler</h2>
      {satirlar.length === 0 ? (
        <p className="py-6 text-center text-[13.5px] text-ink-soft">
          Aksiyon bekleyen kurum yok — her şey yolunda.
        </p>
      ) : (
        <ul className="space-y-2">
          {satirlar.map((s) => (
            <li
              key={s.tenantId}
              className="flex items-center justify-between gap-3 rounded-[10px] border border-line px-3 py-2"
            >
              <div className="min-w-0">
                <Link
                  to={`/platform/tenants/${s.tenantId}`}
                  className="block truncate text-[14px] font-semibold hover:text-rasp"
                >
                  {s.ad}
                </Link>
                <div className="text-[12px] text-ink-soft">
                  {s.sebep}
                  {s.graceEndsAt && ` · ${tarihFormat(s.graceEndsAt)}'e kadar`}
                </div>
              </div>
              <span
                className={`badge shrink-0 ${
                  s.tenantStatus === 'ASKIDA' ? 'b-red' : 'b-amber'
                }`}
              >
                {s.tenantStatus === 'ASKIDA' ? 'Askıda' : 'Uyarı'}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function YenilemeKarti({ satirlar }: { satirlar: PlatformDashboard['yaklasanYenilemeler'] }) {
  return (
    <div className="card">
      <h2 className="mb-3 font-fraunces text-[17px] font-semibold">Yaklaşan yenilemeler</h2>
      {satirlar.length === 0 ? (
        <p className="py-6 text-center text-[13.5px] text-ink-soft">
          Önümüzdeki 7 günde yenilenecek abonelik yok.
        </p>
      ) : (
        <ul className="space-y-2">
          {satirlar.map((s) => (
            <li
              key={s.tenantId}
              className="flex items-center justify-between gap-3 rounded-[10px] border border-line px-3 py-2"
            >
              <div className="min-w-0">
                <Link
                  to={`/platform/tenants/${s.tenantId}`}
                  className="block truncate text-[14px] font-semibold hover:text-rasp"
                >
                  {s.ad}
                </Link>
                <div className="text-[12px] text-ink-soft">{tarihFormat(s.donemBitisi)}</div>
              </div>
              <span className={`badge shrink-0 ${s.otomatikOdeme ? 'b-green' : 'b-gray'}`}>
                {s.otomatikOdeme ? 'Otomatik' : 'Manuel'}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function HareketKarti({ satirlar }: { satirlar: PlatformDashboard['sonHareketler'] }) {
  return (
    <div className="card">
      <h2 className="mb-3 font-fraunces text-[17px] font-semibold">Son ödeme hareketleri</h2>
      {satirlar.length === 0 ? (
        <p className="py-6 text-center text-[13.5px] text-ink-soft">
          Henüz ödeme bildirimi yok. Tahsilatlar günlük mutabakatla da doğrulanır.
        </p>
      ) : (
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead>
              <tr>
                <th>Olay</th>
                <th>Kurum</th>
                <th>Sonuç</th>
                <th>Tarih</th>
              </tr>
            </thead>
            <tbody>
              {satirlar.map((s, i) => (
                <tr key={`${s.eventType}-${s.tarih}-${i}`}>
                  <td>{olayEtiketi(s.eventType)}</td>
                  <td>{s.kurumAdi}</td>
                  <td>
                    <span className={`badge ${s.status === 'PROCESSED' ? 'b-green' : 'b-gray'}`}>
                      {s.status === 'PROCESSED' ? 'İşlendi' : 'Eşleşmedi'}
                    </span>
                  </td>
                  <td className="text-ink-soft">{zamanFormat(s.tarih)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function olayEtiketi(eventType: string): string {
  if (eventType.endsWith('order.success')) return 'Tahsilat başarılı';
  if (eventType.endsWith('order.failure')) return 'Tahsilat başarısız';
  // Webhook DIŞI kaynaklar: ödeme sayfasından tamamlanan checkout ve gecelik mutabakat.
  if (eventType === 'odeme.checkout.basarili') return 'Ödeme alındı (form)';
  if (eventType === 'odeme.mutabakat.yakalandi') return 'Tahsilat doğrulandı (mutabakat)';
  if (eventType === 'abonelik.iptal.talep') return 'Abonelik iptal edildi';
  return eventType;
}

function paraFormat(v: string | number): string {
  const n = typeof v === 'string' ? Number(v) : v;
  if (!Number.isFinite(n)) return '—';
  return new Intl.NumberFormat('tr-TR', {
    style: 'currency',
    currency: 'TRY',
    maximumFractionDigits: 0,
  }).format(n);
}

function tarihFormat(iso: string): string {
  return new Date(iso).toLocaleDateString('tr-TR', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });
}

function zamanFormat(iso: string): string {
  return new Date(iso).toLocaleString('tr-TR', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}
