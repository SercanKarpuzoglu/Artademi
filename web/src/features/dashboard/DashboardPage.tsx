import type { DashboardData, DersOzet, OdemeOzet, OgrenciOzet } from '../../api/dashboard';
import { ApiException } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import StatusBadge from '../../components/StatusBadge';
import { formatDate, formatMoney } from '../../lib/format';
import TrendChart from './TrendChart';
import { useDashboard } from './useDashboard';

const ODEME_YONTEM_LABEL: Record<OdemeOzet['yontem'], string> = {
  NAKIT: 'Nakit',
  KART: 'Kart',
  HAVALE: 'Havale',
};

export default function DashboardPage() {
  const { username } = useAuth();
  const query = useDashboard();

  return (
    <>
      <div className="topbar">
        <div>
          <h1>Genel Bakış</h1>
          <div className="sub">Hoş geldin, {username}</div>
        </div>
      </div>

      {query.isLoading ? (
        <div className="card text-center text-ink-soft">Yükleniyor…</div>
      ) : query.isError ? (
        <div className="card text-center text-red">
          {query.error instanceof ApiException ? query.error.message : 'Panel yüklenemedi'}
        </div>
      ) : query.data ? (
        <DashboardContent data={query.data} />
      ) : null}
    </>
  );
}

function DashboardContent({ data }: { data: DashboardData }) {
  switch (data.rol) {
    case 'ADMIN':
      return (
        <div className="space-y-5">
          {data.subscriptionWarning?.inGrace && (
            <SubscriptionBanner message={data.subscriptionWarning.message} />
          )}
          <StatGrid
            items={[
              { label: 'Aktif Öğrenci', value: String(data.sayilar.aktifOgrenci) },
              { label: 'Aktif Grup', value: String(data.sayilar.aktifGrup) },
              { label: 'Bu Ay Tahsilat', value: `${formatMoney(data.sayilar.buAyTahsilat)} ₺` },
              { label: 'Bu Ay Gider', value: `${formatMoney(data.sayilar.buAyGider)} ₺` },
              {
                label: 'Bu Ay Net',
                value: `${formatMoney(data.sayilar.buAyNet)} ₺`,
                tone: data.sayilar.buAyNet < 0 ? 'down' : 'up',
              },
              { label: 'Bekleyen Borç', value: `${formatMoney(data.sayilar.bekleyenBorcToplam)} ₺` },
            ]}
          />
          <Card title="Son 6 Ay — Tahsilat / Gider / Net">
            <TrendChart data={data.trend6Ay} showGiderNet />
          </Card>
          <div className="grid gap-5 lg:grid-cols-2">
            <Card title="Son Ödemeler">
              <OdemeList odemeler={data.sonOdemeler} />
            </Card>
            <Card title="Son Öğrenciler">
              <OgrenciList ogrenciler={data.sonOgrenciler} />
            </Card>
          </div>
          <Card title="Bugünkü Dersler">
            <DersList dersler={data.bugunDersler} />
          </Card>
        </div>
      );

    case 'FRONTDESK_ACCOUNTING':
      return (
        <div className="space-y-5">
          <StatGrid
            items={[
              { label: 'Aktif Öğrenci', value: String(data.sayilar.aktifOgrenci) },
              { label: 'Aktif Grup', value: String(data.sayilar.aktifGrup) },
              { label: 'Bu Ay Tahsilat', value: `${formatMoney(data.sayilar.buAyTahsilat)} ₺` },
              { label: 'Bekleyen Borç', value: `${formatMoney(data.sayilar.bekleyenBorcToplam)} ₺` },
            ]}
          />
          <Card title="Son 6 Ay — Tahsilat">
            <TrendChart data={data.trend6Ay} showGiderNet={false} />
          </Card>
          <div className="grid gap-5 lg:grid-cols-2">
            <Card title="Son Ödemeler">
              <OdemeList odemeler={data.sonOdemeler} />
            </Card>
            <Card title="Son Öğrenciler">
              <OgrenciList ogrenciler={data.sonOgrenciler} />
            </Card>
          </div>
          <Card title="Bugünkü Dersler">
            <DersList dersler={data.bugunDersler} />
          </Card>
        </div>
      );

    case 'FRONTDESK':
      return (
        <div className="space-y-5">
          <StatGrid
            items={[
              { label: 'Aktif Öğrenci', value: String(data.sayilar.aktifOgrenci) },
              { label: 'Aktif Grup', value: String(data.sayilar.aktifGrup) },
            ]}
          />
          <div className="grid gap-5 lg:grid-cols-2">
            <Card title="Bugünkü Dersler">
              <DersList dersler={data.bugunDersler} />
            </Card>
            <Card title="Son Öğrenciler">
              <OgrenciList ogrenciler={data.sonOgrenciler} />
            </Card>
          </div>
        </div>
      );

    case 'TEACHER':
      return (
        <div className="space-y-5">
          <Card title="Gruplarım">
            {data.kendiGruplar.length === 0 ? (
              <Empty>Henüz grubunuz yok</Empty>
            ) : (
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {data.kendiGruplar.map((g) => (
                  <div key={g.id} className="rounded-[12px] border border-line px-4 py-3">
                    <div className="flex items-center justify-between gap-2">
                      <b className="text-[14px]">{g.ad}</b>
                      <span className={`badge ${g.tip === 'OZEL' ? 'b-amber' : 'b-rasp'}`}>
                        {g.tip === 'OZEL' ? 'Özel' : 'Grup'}
                      </span>
                    </div>
                    <div className="mt-1 text-[13px] text-ink-soft">{g.ogrenciSayisi} öğrenci</div>
                  </div>
                ))}
              </div>
            )}
          </Card>
          <div className="grid gap-5 lg:grid-cols-2">
            <Card title="Bugünkü Dersler">
              <DersList dersler={data.bugunDersler} />
            </Card>
            <Card title="Son Yoklamalar">
              {data.sonYoklamalar.length === 0 ? (
                <Empty>Henüz yoklama yok</Empty>
              ) : (
                <ul className="divide-y divide-line">
                  {data.sonYoklamalar.map((y, i) => (
                    <li key={i} className="flex items-center justify-between py-2 text-[13.5px]">
                      <span>
                        <b>{y.grupAd}</b>
                        <span className="ml-2 text-ink-soft">{formatDate(y.tarih)}</span>
                      </span>
                      <span className="text-ink-soft">
                        {y.gelenSayi}/{y.toplam} geldi
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          </div>
        </div>
      );
  }
}

// ---------------------------------------------------------------- ortak parçalar

interface StatItem {
  label: string;
  value: string;
  tone?: 'up' | 'down';
}

function StatGrid({ items }: { items: StatItem[] }) {
  return (
    <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
      {items.map((it) => (
        <div key={it.label} className="card stat">
          <div className="label">{it.label}</div>
          <div className={`value ${it.tone === 'down' ? 'text-red' : ''}`}>{it.value}</div>
        </div>
      ))}
    </div>
  );
}

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="card">
      <h3>{title}</h3>
      {children}
    </div>
  );
}

function Empty({ children }: { children: React.ReactNode }) {
  return <p className="py-6 text-center text-[13px] text-ink-soft">{children}</p>;
}

function OdemeList({ odemeler }: { odemeler: OdemeOzet[] }) {
  if (odemeler.length === 0) return <Empty>Henüz ödeme yok</Empty>;
  return (
    <table className="data-table">
      <thead>
        <tr>
          <th>Öğrenci</th>
          <th>Tutar</th>
          <th>Tarih</th>
          <th>Yöntem</th>
        </tr>
      </thead>
      <tbody>
        {odemeler.map((o, i) => (
          <tr key={i}>
            <td>
              <b>{o.ogrenciAd}</b>
            </td>
            <td>{formatMoney(o.tutar)} ₺</td>
            <td className="text-ink-soft">{formatDate(o.tarih)}</td>
            <td>
              <span className="badge b-blue">{ODEME_YONTEM_LABEL[o.yontem]}</span>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function OgrenciList({ ogrenciler }: { ogrenciler: OgrenciOzet[] }) {
  if (ogrenciler.length === 0) return <Empty>Henüz öğrenci yok</Empty>;
  return (
    <ul className="divide-y divide-line">
      {ogrenciler.map((o, i) => (
        <li key={i} className="flex items-center justify-between py-2 text-[13.5px]">
          <span>
            <b>
              {o.ad} {o.soyad}
            </b>
            <span className="ml-2 text-ink-soft">{formatDate(o.kayitTarihi.slice(0, 10))}</span>
          </span>
          <StatusBadge status={o.statu} />
        </li>
      ))}
    </ul>
  );
}

function DersList({ dersler }: { dersler: DersOzet[] }) {
  if (dersler.length === 0) return <Empty>Bugün ders yok</Empty>;
  return (
    <ul className="divide-y divide-line">
      {dersler.map((d, i) => (
        <li key={i} className="flex items-center justify-between py-2 text-[13.5px]">
          <span>
            <b>{d.grupAd}</b>
            {d.salon && <span className="ml-2 text-ink-soft">{d.salon}</span>}
          </span>
          <span className="text-ink-soft">
            {d.baslangic.slice(0, 5)}–{d.bitis.slice(0, 5)}
          </span>
        </li>
      ))}
    </ul>
  );
}

function SubscriptionBanner({ message }: { message: string }) {
  return (
    <div className="rounded-[14px] border border-amber/40 bg-amber-soft px-4 py-3 text-[13px] text-ink">
      {message}
    </div>
  );
}
