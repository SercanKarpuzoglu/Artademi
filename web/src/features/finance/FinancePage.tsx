import { useState } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { Role } from '../../auth/roles';
import GiderTab from './GiderTab';
import KasaTab from './KasaTab';
import PaketTab from './PaketTab';
import GelirTab from './GelirTab';
import IndirimTab from './IndirimTab';
import OtomatikTahakkukTab from './OtomatikTahakkukTab';
import TahakkukTab from './TahakkukTab';
import TedarikciTab from './TedarikciTab';

type TabKey = 'tahakkuk' | 'odeme' | 'gider' | 'paket' | 'indirim' | 'kasa' | 'tedarikci' | 'otomatik';

export default function FinancePage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole(Role.ADMIN);
  const [tab, setTab] = useState<TabKey>('tahakkuk');

  // Otomatik tahakkuk YALNIZCA ADMIN; FRONTDESK_ACCOUNTING bu sekmeyi görmez (backend de 403 verir).
  const tabs: { key: TabKey; label: string }[] = [
    { key: 'tahakkuk', label: 'Tahakkuklar' },
    { key: 'odeme', label: 'Gelirler' },
    { key: 'gider', label: 'Giderler' },
    { key: 'paket', label: 'Ders Paketleri' },
    { key: 'indirim', label: 'İndirimler' },
    { key: 'kasa', label: 'Kasalar' },
    { key: 'tedarikci', label: 'Tedarikçiler' },
    ...(isAdmin ? [{ key: 'otomatik' as TabKey, label: 'Otomatik Tahakkuk' }] : []),
  ];

  return (
    <>
      <div className="topbar">
        <div>
          <h1>Finans</h1>
          <div className="sub">Tahakkuk, gelir, gider ve öğrenci bakiyeleri</div>
        </div>
      </div>

      <div className="tabs mb-[18px]">
        {tabs.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setTab(t.key)}
            className={`tab${tab === t.key ? ' active' : ''}`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'tahakkuk' && <TahakkukTab />}
      {tab === 'odeme' && <GelirTab />}
      {tab === 'gider' && <GiderTab />}
      {tab === 'paket' && <PaketTab />}
      {tab === 'indirim' && <IndirimTab />}
      {tab === 'kasa' && <KasaTab />}
      {tab === 'tedarikci' && <TedarikciTab />}
      {tab === 'otomatik' && isAdmin && <OtomatikTahakkukTab />}
    </>
  );
}
