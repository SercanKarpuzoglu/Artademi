import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiException } from '../../api/client';
import { getPlatformBillingEvents, getPlatformSubscriptions } from '../../api/platform';
import type { OdemeFiltre, PlatformSubscriptionRow } from '../../api/types';
import { useDebounce } from '../../lib/useDebounce';

const FILTRELER: ReadonlyArray<{ key: OdemeFiltre; label: string }> = [
  { key: 'HEPSI', label: 'Hepsi' },
  { key: 'ODEYEN', label: 'Ödeyen' },
  { key: 'DENEME', label: 'Deneme' },
  { key: 'GECIKMIS', label: 'Gecikmiş' },
  { key: 'ASKIDA', label: 'Askıda' },
];

const SAYFA_BOYUTU = 20;

/**
 * Ödeme & abonelik takibi (SUPER_ADMIN): hangi kurum ödüyor, kim gecikti, hareketler ne diyor.
 * Üstte kurum bazlı durum tablosu (iş dili filtreleriyle), altta ham ödeme hareketleri.
 */
export default function PlatformOdemelerPage() {
  const [filtre, setFiltre] = useState<OdemeFiltre>('HEPSI');
  const [arama, setArama] = useState('');
  const q = useDebounce(arama.trim(), 300);

  const subs = useQuery({
    queryKey: ['platform', 'billing', 'subscriptions', { filtre, q }],
    queryFn: () => getPlatformSubscriptions({ filtre, q: q || undefined }),
    placeholderData: keepPreviousData,
  });

  return (
    <div className="space-y-5">
      <div className="topbar">
        <div>
          <h1>Ödemeler</h1>
          <div className="sub">Abonelik ve tahsilat durumu</div>
        </div>
      </div>

      <div className="card">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
          <div className="tabs">
            {FILTRELER.map((f) => (
              <button
                key={f.key}
                type="button"
                className={`tab${filtre === f.key ? ' active' : ''}`}
                onClick={() => setFiltre(f.key)}
              >
                {f.label}
              </button>
            ))}
          </div>
          <input
            className="w-full max-w-[240px] rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none"
            placeholder="Kurum ara…"
            value={arama}
            onChange={(e) => setArama(e.target.value)}
          />
        </div>

        {subs.isLoading ? (
          <p className="py-8 text-center text-ink-soft">Yükleniyor…</p>
        ) : subs.isError ? (
          <p className="py-8 text-center text-red">
            {subs.error instanceof ApiException ? subs.error.message : 'Liste yüklenemedi'}
          </p>
        ) : subs.data && subs.data.length === 0 ? (
          <p className="py-8 text-center text-[13.5px] text-ink-soft">
            Bu filtreye uyan kurum yok.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Kurum</th>
                  <th>Plan</th>
                  <th>Durum</th>
                  <th>Ödeme</th>
                  <th>Dönem bitişi</th>
                  <th>Tahsilat</th>
                </tr>
              </thead>
              <tbody>
                {subs.data?.map((r) => (
                  <SubscriptionSatiri key={r.tenantId} r={r} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <HareketlerKarti />
    </div>
  );
}

function SubscriptionSatiri({ r }: { r: PlatformSubscriptionRow }) {
  return (
    <tr>
      <td>
        <Link to={`/platform/tenants/${r.tenantId}`} className="font-semibold hover:text-rasp">
          {r.ad}
        </Link>
        {r.tenantStatus === 'SILINDI' && (
          <span className="badge b-gray ml-2">Silindi</span>
        )}
      </td>
      <td>{r.plan === 'AYLIK' ? 'Aylık' : 'Deneme'}</td>
      <td>
        <span className={`badge ${DURUM_ROZET[r.abonelikStatus].cls}`}>
          {DURUM_ROZET[r.abonelikStatus].label}
        </span>
      </td>
      <td>
        <span className={`badge ${ODEME_ROZET[r.odemeStatus].cls}`}>
          {ODEME_ROZET[r.odemeStatus].label}
        </span>
      </td>
      <td className="text-ink-soft">
        {tarihFormat(r.currentPeriodEnd)}
        {r.graceEndsAt && (
          <div className="text-[11.5px] text-amber">
            Ek süre: {tarihFormat(r.graceEndsAt)}
          </div>
        )}
      </td>
      <td>
        <span className={`badge ${r.otomatikOdeme ? 'b-green' : 'b-gray'}`}>
          {r.otomatikOdeme ? 'Otomatik' : 'Manuel'}
        </span>
      </td>
    </tr>
  );
}

function HareketlerKarti() {
  const [sayfa, setSayfa] = useState(0);
  const q = useQuery({
    queryKey: ['platform', 'billing', 'events', sayfa],
    queryFn: () => getPlatformBillingEvents({ page: sayfa, size: SAYFA_BOYUTU }),
    placeholderData: keepPreviousData,
  });

  return (
    <div className="card">
      <h2 className="mb-1 font-fraunces text-[17px] font-semibold">Ödeme hareketleri</h2>
      <p className="mb-3 text-[12.5px] text-ink-soft">
        Sağlayıcıdan gelen bildirimler. Bildirim ulaşmasa bile tahsilatlar her gece mutabakatla
        doğrulanır.
      </p>

      {q.isLoading ? (
        <p className="py-6 text-center text-ink-soft">Yükleniyor…</p>
      ) : q.isError ? (
        <p className="py-6 text-center text-red">Hareketler yüklenemedi</p>
      ) : q.data && q.data.rows.length === 0 ? (
        <p className="py-6 text-center text-[13.5px] text-ink-soft">Henüz hareket yok.</p>
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Olay</th>
                  <th>Kurum</th>
                  <th>Sonuç</th>
                  <th>Sağlayıcı</th>
                  <th>Tarih</th>
                </tr>
              </thead>
              <tbody>
                {q.data?.rows.map((e) => (
                  <tr key={e.id}>
                    <td>{olayEtiketi(e.eventType)}</td>
                    <td>{e.kurumAdi ?? '—'}</td>
                    <td>
                      <span className={`badge ${e.status === 'PROCESSED' ? 'b-green' : 'b-gray'}`}>
                        {e.status === 'PROCESSED' ? 'İşlendi' : 'Eşleşmedi'}
                      </span>
                    </td>
                    <td className="text-ink-soft">{e.provider}</td>
                    <td className="text-ink-soft">{zamanFormat(e.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {(q.data?.totalPages ?? 1) > 1 && (
            <div className="mt-3 flex items-center justify-between text-[13px]">
              <span className="text-ink-soft">
                Sayfa {sayfa + 1} / {q.data?.totalPages} · {q.data?.totalElements} kayıt
              </span>
              <div className="flex gap-2">
                <button
                  type="button"
                  className="btn btn-ghost"
                  disabled={sayfa === 0}
                  onClick={() => setSayfa((s) => Math.max(0, s - 1))}
                >
                  Önceki
                </button>
                <button
                  type="button"
                  className="btn btn-ghost"
                  disabled={sayfa + 1 >= (q.data?.totalPages ?? 1)}
                  onClick={() => setSayfa((s) => s + 1)}
                >
                  Sonraki
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}

const DURUM_ROZET: Record<PlatformSubscriptionRow['abonelikStatus'], { cls: string; label: string }> =
  {
    AKTIF: { cls: 'b-green', label: 'Aktif' },
    DENEME: { cls: 'b-amber', label: 'Deneme' },
    ODEME_BEKLIYOR: { cls: 'b-amber', label: 'Ödeme bekliyor' },
    ASKIDA: { cls: 'b-red', label: 'Askıda' },
    IPTAL: { cls: 'b-gray', label: 'İptal' },
  };

const ODEME_ROZET: Record<PlatformSubscriptionRow['odemeStatus'], { cls: string; label: string }> = {
  ODENDI: { cls: 'b-green', label: 'Ödendi' },
  BEKLIYOR: { cls: 'b-amber', label: 'Bekliyor' },
  BASARISIZ: { cls: 'b-red', label: 'Başarısız' },
};

function olayEtiketi(eventType: string): string {
  if (eventType.endsWith('order.success')) return 'Tahsilat başarılı';
  if (eventType.endsWith('order.failure')) return 'Tahsilat başarısız';
  // Webhook DIŞI kaynaklar: ödeme sayfasından tamamlanan checkout ve gecelik mutabakat.
  if (eventType === 'odeme.checkout.basarili') return 'Ödeme alındı (form)';
  if (eventType === 'odeme.mutabakat.yakalandi') return 'Tahsilat doğrulandı (mutabakat)';
  if (eventType === 'abonelik.iptal.talep') return 'Abonelik iptal edildi';
  return eventType;
}

function tarihFormat(iso: string | null): string {
  if (!iso) return '—';
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
