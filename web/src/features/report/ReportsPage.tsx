import { useState } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { Role } from '../../auth/roles';
import FinancialSummaryTab from './FinancialSummaryTab';
import GroupOccupancyTab from './GroupOccupancyTab';
import StudentBalancesTab from './StudentBalancesTab';
import TeacherPayoutsTab from './TeacherPayoutsTab';

type TabKey = 'financial' | 'balances' | 'payouts' | 'occupancy';

interface TabDef {
  key: TabKey;
  label: string;
  /** Bu sekmeyi görebilecek roller — backend ReportController yetkisini birebir aynalar. */
  roles: readonly Role[];
}

/**
 * Sekme tanimlari — HER SEKME KENDI ROL KAPISINA sahip (backend ReportController ile birebir).
 * Yetkisiz sekme HIC render/fetch edilmez; boylece ADMIN-only uca yetkisiz cagri (403) gitmez.
 */
const TAB_DEFS: readonly TabDef[] = [
  { key: 'financial', label: 'Finansal Özet', roles: [Role.ADMIN] },
  { key: 'balances', label: 'Öğrenci Borç', roles: [Role.ADMIN, Role.FRONTDESK_ACCOUNTING] },
  { key: 'payouts', label: 'Öğretmen Hakediş', roles: [Role.ADMIN] },
  {
    key: 'occupancy',
    label: 'Grup Doluluk',
    roles: [Role.ADMIN, Role.FRONTDESK, Role.FRONTDESK_ACCOUNTING],
  },
];

export default function ReportsPage() {
  const { hasAnyRole } = useAuth();
  const visibleTabs = TAB_DEFS.filter((t) => hasAnyRole(t.roles));
  const [tab, setTab] = useState<TabKey>(visibleTabs[0]?.key ?? 'occupancy');

  // Aktif sekme görünür sekmeler arasinda degilse ilkine düş (rol değişimi savunmasi).
  const active = visibleTabs.some((t) => t.key === tab) ? tab : visibleTabs[0]?.key;

  return (
    <>
      <div className="topbar">
        <div>
          <h1>Raporlar</h1>
          <div className="sub">Finansal özet, öğrenci borç, öğretmen hakediş ve grup doluluk</div>
        </div>
      </div>

      <div className="tabs mb-[18px]">
        {visibleTabs.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setTab(t.key)}
            className={`tab${active === t.key ? ' active' : ''}`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {active === 'financial' && <FinancialSummaryTab />}
      {active === 'balances' && <StudentBalancesTab />}
      {active === 'payouts' && <TeacherPayoutsTab />}
      {active === 'occupancy' && <GroupOccupancyTab />}
    </>
  );
}
