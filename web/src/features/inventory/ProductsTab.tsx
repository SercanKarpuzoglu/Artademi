import { useState } from 'react';
import { ApiException } from '../../api/client';
import type { ProductInput, ProductResponse, UpdateProductInput } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Role } from '../../auth/roles';
import { formatMoney } from '../../lib/format';
import { useDebounce } from '../../lib/useDebounce';
import { stockBadge } from './inventoryDisplay';
import {
  useCreateProduct,
  useProducts,
  useSetProductActive,
  useSetProductStock,
  useUpdateProduct,
} from './useInventory';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

const PAGE_SIZE = 20;

const AKTIF_TABS: { label: string; value: boolean | undefined }[] = [
  { label: 'Tümü', value: undefined },
  { label: 'Aktif', value: true },
  { label: 'Pasif', value: false },
];

export default function ProductsTab() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole(Role.ADMIN);

  const [aktif, setAktif] = useState<boolean | undefined>(undefined);
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<ProductResponse | null>(null);

  const debouncedQ = useDebounce(q, 300);
  const query = useProducts({
    aktif,
    q: debouncedQ.trim() || undefined,
    page,
    size: PAGE_SIZE,
  });
  const products = query.data?.data ?? [];
  const meta = query.data?.meta;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3">
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-gray-700">Ara</span>
          <input
            type="search"
            className={inputClass}
            placeholder="Ürün adı…"
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setPage(0);
            }}
          />
        </label>
        {isAdmin && (
          <div className="ml-auto">
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => {
                setEditing(null);
                setShowForm((v) => !v);
              }}
            >
              {showForm && !editing ? 'Kapat' : '+ Yeni Ürün'}
            </button>
          </div>
        )}
      </div>

      <div className="tabs">
        {AKTIF_TABS.map((t) => (
          <button
            key={t.label}
            type="button"
            onClick={() => {
              setAktif(t.value);
              setPage(0);
            }}
            className={`tab${aktif === t.value ? ' active' : ''}`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {isAdmin && showForm && (
        <ProductForm
          editing={editing}
          onDone={() => {
            setShowForm(false);
            setEditing(null);
          }}
        />
      )}

      {query.isLoading ? (
        <div className="card text-center text-ink-soft">Yükleniyor…</div>
      ) : query.isError ? (
        <div className="card text-center text-red">
          {query.error instanceof ApiException ? query.error.message : 'Bir hata oluştu'}
        </div>
      ) : products.length === 0 ? (
        <div className="card text-center text-ink-soft">Ürün bulunamadı</div>
      ) : (
        <>
          <div className="card">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Ad</th>
                  <th className="t-right">Fiyat</th>
                  <th>Stok</th>
                  <th>Durum</th>
                  {isAdmin && <th className="t-right">İşlemler</th>}
                </tr>
              </thead>
              <tbody>
                {products.map((p) => (
                  <ProductRow key={p.id} product={p} isAdmin={isAdmin} />
                ))}
              </tbody>
            </table>
          </div>
          {meta && (
            <div className="flex items-center justify-between text-[13px] text-ink-soft">
              <span>
                Toplam {meta.totalElements} · Sayfa {meta.page + 1}/{Math.max(meta.totalPages, 1)}
              </span>
              <div className="flex gap-2">
                <button
                  type="button"
                  className="btn btn-ghost disabled:opacity-40"
                  onClick={() => setPage((x) => Math.max(x - 1, 0))}
                  disabled={meta.page <= 0}
                >
                  Önceki
                </button>
                <button
                  type="button"
                  className="btn btn-ghost disabled:opacity-40"
                  onClick={() => setPage((x) => x + 1)}
                  disabled={meta.page + 1 >= meta.totalPages}
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

function ProductRow({ product, isAdmin }: { product: ProductResponse; isAdmin: boolean }) {
  const [editing, setEditing] = useState(false);
  const [stokEditing, setStokEditing] = useState(false);
  const badge = stockBadge(product.stokAdedi);

  if (isAdmin && editing) {
    return (
      <tr>
        <td colSpan={5}>
          <ProductForm editing={product} onDone={() => setEditing(false)} />
        </td>
      </tr>
    );
  }

  return (
    <tr>
      <td>
        <b>{product.ad}</b>
        {product.aciklama && (
          <div className="text-xs text-ink-soft">{product.aciklama}</div>
        )}
      </td>
      <td className="t-right">
        <span className="amount">{formatMoney(product.satisFiyati)} ₺</span>
      </td>
      <td>
        {badge.className ? (
          <span className={`badge ${badge.className}`}>{badge.label}</span>
        ) : (
          badge.label
        )}
        {isAdmin && stokEditing && (
          <StockEditor product={product} onDone={() => setStokEditing(false)} />
        )}
      </td>
      <td>
        <span className={`badge ${product.aktif ? 'b-green' : 'b-gray'}`}>
          {product.aktif ? 'Aktif' : 'Pasif'}
        </span>
      </td>
      {isAdmin && (
        <td className="t-right">
          <div className="flex justify-end gap-2">
            <button type="button" className="btn btn-ghost" onClick={() => setEditing(true)}>
              Düzenle
            </button>
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => setStokEditing((v) => !v)}
            >
              Stok
            </button>
            <ActiveToggle product={product} />
          </div>
        </td>
      )}
    </tr>
  );
}

function ActiveToggle({ product }: { product: ProductResponse }) {
  const mut = useSetProductActive();
  return (
    <button
      type="button"
      className="btn btn-ghost"
      disabled={mut.isPending}
      onClick={() => mut.mutate({ id: product.id, aktif: !product.aktif })}
    >
      {product.aktif ? 'Pasifleştir' : 'Aktifleştir'}
    </button>
  );
}

function StockEditor({ product, onDone }: { product: ProductResponse; onDone: () => void }) {
  const mut = useSetProductStock();
  const [value, setValue] = useState(String(product.stokAdedi));
  const [error, setError] = useState<string | null>(null);

  async function save() {
    setError(null);
    const n = Number(value);
    if (!Number.isInteger(n) || n < 0) {
      setError('Stok adedi 0 veya pozitif tam sayı olmalı');
      return;
    }
    try {
      await mut.mutateAsync({ id: product.id, stokAdedi: n });
      onDone();
    } catch (err) {
      setError(err instanceof ApiException ? err.message : 'Bir hata oluştu');
    }
  }

  return (
    <div className="mt-2 flex items-center gap-2">
      <input
        type="number"
        min={0}
        step={1}
        className="w-24 rounded-[10px] border border-line bg-card px-2 py-1 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp"
        value={value}
        onChange={(e) => setValue(e.target.value)}
      />
      <button type="button" className="btn btn-primary" disabled={mut.isPending} onClick={save}>
        Kaydet
      </button>
      <button type="button" className="btn btn-ghost" onClick={onDone}>
        İptal
      </button>
      {error && <span className="text-xs text-red-600">{error}</span>}
    </div>
  );
}

function ProductForm({
  editing,
  onDone,
}: {
  editing: ProductResponse | null;
  onDone: () => void;
}) {
  const createMut = useCreateProduct();
  const updateMut = useUpdateProduct(editing?.id ?? 0);
  const isEdit = editing !== null;

  const [ad, setAd] = useState(editing?.ad ?? '');
  const [satisFiyati, setSatisFiyati] = useState(
    editing ? String(editing.satisFiyati) : '',
  );
  const [stokAdedi, setStokAdedi] = useState(editing ? String(editing.stokAdedi) : '0');
  const [aciklama, setAciklama] = useState(editing?.aciklama ?? '');

  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const pending = createMut.isPending || updateMut.isPending;

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    setFieldErrors({});

    const errs: Record<string, string> = {};
    if (!ad.trim()) {
      errs.ad = 'Ad zorunlu';
    }
    const fiyat = Number(satisFiyati.replace(',', '.'));
    if (!satisFiyati.trim() || Number.isNaN(fiyat) || fiyat <= 0) {
      errs.satisFiyati = 'Satış fiyatı pozitif olmalı';
    }
    if (!isEdit) {
      const stok = Number(stokAdedi);
      if (!Number.isInteger(stok) || stok < 0) {
        errs.stokAdedi = 'Stok adedi 0 veya pozitif tam sayı olmalı';
      }
    }
    if (Object.keys(errs).length > 0) {
      setFieldErrors(errs);
      return;
    }

    try {
      if (isEdit) {
        const payload: UpdateProductInput = {
          ad: ad.trim(),
          satisFiyati: satisFiyati.trim(),
          aciklama: aciklama.trim() || undefined,
        };
        await updateMut.mutateAsync(payload);
      } else {
        const payload: ProductInput = {
          ad: ad.trim(),
          satisFiyati: satisFiyati.trim(),
          stokAdedi: Number(stokAdedi),
          aciklama: aciklama.trim() || undefined,
        };
        await createMut.mutateAsync(payload);
      }
      onDone();
    } catch (err) {
      if (err instanceof ApiException) {
        if (err.code === 'VALIDATION_ERROR' && err.fields) {
          setFieldErrors(err.fields);
          setFormError('Lütfen işaretli alanları düzeltin.');
        } else {
          setFormError(err.message);
        }
      } else {
        setFormError('Beklenmeyen bir hata oluştu.');
      }
    }
  }

  return (
    <form onSubmit={onSubmit} className="card space-y-4" noValidate>
      <h3>{isEdit ? 'Ürün Düzenle' : 'Yeni Ürün'}</h3>
      {formError && (
        <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
          {formError}
        </div>
      )}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Ad" required error={fieldErrors.ad}>
          <input className={inputClass} value={ad} onChange={(e) => setAd(e.target.value)} />
        </Field>
        <Field label="Satış Fiyatı (₺)" required error={fieldErrors.satisFiyati}>
          <input
            className={inputClass}
            inputMode="decimal"
            value={satisFiyati}
            onChange={(e) => setSatisFiyati(e.target.value)}
          />
        </Field>
        {!isEdit && (
          <Field label="Stok Adedi" error={fieldErrors.stokAdedi}>
            <input
              type="number"
              min={0}
              step={1}
              className={inputClass}
              value={stokAdedi}
              onChange={(e) => setStokAdedi(e.target.value)}
            />
          </Field>
        )}
        <Field label="Açıklama" error={fieldErrors.aciklama}>
          <input
            className={inputClass}
            value={aciklama}
            onChange={(e) => setAciklama(e.target.value)}
          />
        </Field>
      </div>
      <div className="flex justify-end gap-3">
        <button type="button" className="btn btn-ghost" onClick={onDone}>
          İptal
        </button>
        <button type="submit" className="btn btn-primary" disabled={pending}>
          {pending ? 'Kaydediliyor…' : 'Kaydet'}
        </button>
      </div>
    </form>
  );
}

function Field({
  label,
  error,
  required,
  children,
}: {
  label: string;
  error?: string;
  required?: boolean;
  children: React.ReactNode;
}) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-gray-700">
        {label}
        {required && <span className="text-red-600"> *</span>}
      </span>
      {children}
      {error && <span className="mt-1 block text-xs text-red-600">{error}</span>}
    </label>
  );
}
