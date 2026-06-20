import { useState } from 'react';
import ProductsTab from './ProductsTab';
import SalesTab from './SalesTab';

type TabKey = 'urunler' | 'satislar';

/**
 * Stok / Satış tek sayfa, iki sekme: Ürünler ve Satışlar.
 * Route gating: /stok -> [ADMIN, FRONTDESK_ACCOUNTING] (App.tsx). Sayfa ici yazma gating'i
 * sekmelerin kendi icinde: ürün yazma = ADMIN; satis = herkes (bu sayfaya ulasan).
 */
export default function InventoryPage() {
  const [tab, setTab] = useState<TabKey>('urunler');

  const tabs: { key: TabKey; label: string }[] = [
    { key: 'urunler', label: 'Ürünler' },
    { key: 'satislar', label: 'Satışlar' },
  ];

  return (
    <>
      <div className="topbar">
        <div>
          <h1>Stok / Satış</h1>
          <div className="sub">Ürün tanımları, stok ve ürün satışları</div>
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

      {tab === 'urunler' && <ProductsTab />}
      {tab === 'satislar' && <SalesTab />}
    </>
  );
}
