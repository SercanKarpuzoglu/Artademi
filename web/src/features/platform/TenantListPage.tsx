import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiException } from '../../api/client';
import type { CreateTenantResult, PlatformTenant, TenantStatus } from '../../api/types';
import { formatDate } from '../../lib/format';
import { useDebounce } from '../../lib/useDebounce';
import { useSoftDeleteTenant, useTenants, useUpdateTenantStatus } from './usePlatformTenants';

const STATUS_TABS: { label: string; value: TenantStatus | undefined }[] = [
  { label: 'Hepsi', value: undefined },
  { label: 'Aktif', value: 'AKTIF' },
  { label: 'Askıda', value: 'ASKIDA' },
  { label: 'Silindi', value: 'SILINDI' },
];

const STATUS_BADGE: Record<TenantStatus, { cls: string; label: string }> = {
  AKTIF: { cls: 'b-green', label: 'Aktif' },
  ASKIDA: { cls: 'b-red', label: 'Askıda' },
  SILINDI: { cls: 'b-gray', label: 'Silindi' },
};

const inputClass =
  'rounded-[10px] border border-line bg-card px-3 py-2 text-[13px] focus-visible:outline focus-visible:outline-2 focus-visible:outline-rasp';

/** Form'dan dönen provisioning sonucunu liste üstünde göstermek için taşınan state. */
interface ListLocationState {
  result?: CreateTenantResult;
}

/** ISO datetime (createdAt) -> gg.aa.yyyy (saat kısmı atılır). */
function formatCreatedAt(iso: string): string {
  return formatDate(iso.slice(0, 10));
}

export default function TenantListPage() {
  const navigate = useNavigate();
  const location = useLocation();

  // Yeni tenant oluşturulduğunda form buraya CreateTenantResult taşır (username/uyarı banner'ı).
  const [result, setResult] = useState<CreateTenantResult | null>(
    (location.state as ListLocationState | null)?.result ?? null,
  );
  useEffect(() => {
    if ((location.state as ListLocationState | null)?.result) {
      // Geçmiş state'ini temizle ki yenilemede banner tekrar çıkmasın.
      window.history.replaceState({}, '');
    }
  }, [location.state]);

  const [q, setQ] = useState('');
  const [status, setStatus] = useState<TenantStatus | undefined>(undefined);
  const debouncedQ = useDebounce(q, 300);

  const [actionError, setActionError] = useState<string | null>(null);
  useEffect(() => {
    setActionError(null);
  }, [debouncedQ, status]);

  const query = useTenants({ q: debouncedQ.trim() || undefined, status });
  const statusMut = useUpdateTenantStatus();
  const softDeleteMut = useSoftDeleteTenant();

  const tenants = query.data ?? [];
  const filtered = Boolean(debouncedQ.trim()) || status !== undefined;

  async function handleToggle(t: PlatformTenant) {
    const next: TenantStatus = t.status === 'AKTIF' ? 'ASKIDA' : 'AKTIF';
    if (next === 'ASKIDA') {
      const ok = window.confirm(
        `"${t.ad}" askıya alınacak. Bu kurumun TÜM kullanıcıları iş ekranlarından kilitlenecek ` +
          `(yalnızca giriş/profil açık kalır). Devam edilsin mi?`,
      );
      if (!ok) return;
    }
    setActionError(null);
    try {
      await statusMut.mutateAsync({ id: t.id, status: next });
    } catch (e) {
      setActionError(e instanceof ApiException ? e.message : 'Durum değiştirilemedi');
    }
  }

  async function handleDelete(t: PlatformTenant) {
    const ok = window.confirm(
      `"${t.ad}" silinecek (soft-delete): listeden gizlenir ve tüm kullanıcıları kilitlenir. ` +
        `Veri SİLİNMEZ; "Silindi" sekmesinden geri alınabilir. Devam edilsin mi?`,
    );
    if (!ok) return;
    setActionError(null);
    try {
      await softDeleteMut.mutateAsync(t.id);
    } catch (e) {
      setActionError(e instanceof ApiException ? e.message : 'Silinemedi');
    }
  }

  return (
    <>
      <div className="topbar">
        <div>
          <h1>Kurumlar</h1>
          <div className="sub">Tenant listesi, durum yönetimi ve yeni kurum açılışı</div>
        </div>
        <div className="top-actions">
          <input
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Kurum adı ara…"
            aria-label="Kurum ara"
            className={inputClass}
          />
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => navigate('/platform/tenants/yeni')}
          >
            + Yeni Kurum
          </button>
        </div>
      </div>

      <div className="tabs mb-[18px]">
        {STATUS_TABS.map((tab) => (
          <button
            key={tab.label}
            type="button"
            onClick={() => setStatus(tab.value)}
            className={`tab${status === tab.value ? ' active' : ''}`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {result && <ProvisioningBanner result={result} onClose={() => setResult(null)} />}

      {actionError && (
        <div className="mb-4 rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
          {actionError}
        </div>
      )}

      {query.isLoading ? (
        <div className="card text-center text-ink-soft">Yükleniyor…</div>
      ) : query.isError ? (
        <div className="card text-center text-red">
          {query.error instanceof ApiException ? query.error.message : 'Bir hata oluştu'}
        </div>
      ) : tenants.length === 0 ? (
        <div className="card text-center text-ink-soft">
          {filtered ? 'Sonuç bulunamadı' : 'Henüz kurum yok'}
        </div>
      ) : (
        <div className="card">
          <table className="data-table">
            <thead>
              <tr>
                <th>Kurum Adı</th>
                <th>Durum</th>
                <th>Oluşturulma</th>
                <th className="t-right">Aksiyon</th>
              </tr>
            </thead>
            <tbody>
              {tenants.map((t) => (
                <tr
                  key={t.id}
                  className="cursor-pointer"
                  onClick={() => navigate(`/platform/tenants/${t.id}`)}
                >
                  <td>
                    <b>{t.ad}</b>
                  </td>
                  <td>
                    <span className={`badge ${STATUS_BADGE[t.status].cls}`}>
                      {STATUS_BADGE[t.status].label}
                    </span>
                  </td>
                  <td className="text-ink-soft">{formatCreatedAt(t.createdAt)}</td>
                  <td className="t-right" onClick={(e) => e.stopPropagation()}>
                    <div className="inline-flex gap-2">
                      <button
                        type="button"
                        className="btn btn-ghost"
                        disabled={statusMut.isPending}
                        onClick={() => handleToggle(t)}
                      >
                        {t.status === 'AKTIF' ? 'Askıya Al' : 'Aktif Et'}
                      </button>
                      {t.status !== 'SILINDI' && (
                        <button
                          type="button"
                          className="btn btn-ghost text-red"
                          disabled={softDeleteMut.isPending}
                          onClick={() => handleDelete(t)}
                        >
                          Sil
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

/** Yeni tenant sonucu: provisioned ise yeşil/ahududu başarı, değilse amber uyarı kutusu. */
function ProvisioningBanner({
  result,
  onClose,
}: {
  result: CreateTenantResult;
  onClose: () => void;
}) {
  const ok = result.admin.provisioned;
  const klass = ok
    ? 'border-rasp/30 bg-rasp-soft text-ink'
    : 'border-amber/40 bg-amber-soft text-ink';
  return (
    <div
      className={`mb-4 flex items-start justify-between gap-4 rounded-[14px] border px-4 py-3 text-[13px] ${klass}`}
    >
      <div className="space-y-1">
        <div className="font-semibold">
          {ok ? `"${result.tenant.ad}" oluşturuldu.` : `"${result.tenant.ad}" oluşturuldu — uyarı`}
        </div>
        {ok ? (
          <div>
            Admin kullanıcı: <b>{result.admin.username}</b> · İlk parola: <b>Artademi2026!</b>{' '}
            (kullanıcıya iletin; ilk girişte değiştirilecek).
          </div>
        ) : (
          <div>
            {result.warning ??
              'Admin kullanıcı yaratılamadı. Kurumun yöneticisini Kullanıcı Yönetimi’nden elle ekleyin.'}
          </div>
        )}
      </div>
      <button type="button" className="shrink-0 font-semibold text-rasp" onClick={onClose}>
        Kapat
      </button>
    </div>
  );
}
