import { useState } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { Role } from '../../auth/roles';
import GiderTab from './GiderTab';
import OdemeTab from './OdemeTab';
import OtomatikTahakkukTab from './OtomatikTahakkukTab';
import TahakkukTab from './TahakkukTab';

type TabKey = 'tahakkuk' | 'odeme' | 'gider' | 'otomatik';

export default function FinancePage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole(Role.ADMIN);
  const [tab, setTab] = useState<TabKey>('tahakkuk');

  // Otomatik tahakkuk YALNIZCA ADMIN; FRONTDESK_ACCOUNTING bu sekmeyi görmez (backend de 403 verir).
  const tabs: { key: TabKey; label: string }[] = [
    { key: 'tahakkuk', label: 'Tahakkuklar' },
    { key: 'odeme', label: 'Ödemeler' },
    { key: 'gider', label: 'Giderler' },
    ...(isAdmin ? [{ key: 'otomatik' as TabKey, label: 'Otomatik Tahakkuk' }] : []),
  ];

  return (
    <>
      <div className="topbar">
        <div>
          <h1>Finans</h1>
          <div className="sub">Tahakkuk, ödeme, gider ve öğrenci bakiyeleri</div>
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
      {tab === 'odeme' && <OdemeTab />}
      {tab === 'gider' && <GiderTab />}
      {tab === 'otomatik' && isAdmin && <OtomatikTahakkukTab />}
    </>
  );
}
