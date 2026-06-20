import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ApiException } from '../../api/client';
import { useMe } from '../../auth/useMe';
import { useDebounce } from '../../lib/useDebounce';
import { ASSIGNABLE_ROLES, roleBadgeClass, roleLabel } from './userDisplay';
import { useDeleteUser, useSetUserActive, useUsers } from './useUsers';

const AKTIF_TABS: { label: string; value: boolean | undefined }[] = [
  { label: 'Tümü', value: undefined },
  { label: 'Aktif', value: true },
  { label: 'Pasif', value: false },
];

const inputClass =
  'rounded-[10px] border border-line bg-card px-3 py-2 text-[13px] focus-visible:outline focus-visible:outline-2 focus-visible:outline-rasp';

interface ListLocationState {
  createdNotice?: string;
}

export default function UserListPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const meQuery = useMe();
  const meSub = meQuery.data?.sub;

  // UserForm create başarısında yeni kullanıcının ilk parolasını taşıyan bilgi banner'ı.
  const [createdNotice, setCreatedNotice] = useState<string | null>(
    (location.state as ListLocationState | null)?.createdNotice ?? null,
  );
  useEffect(() => {
    // Geçmiş state'ini temizle ki yenilemede banner tekrar çıkmasın (state mount'ta yakalandı).
    if ((location.state as ListLocationState | null)?.createdNotice) {
      window.history.replaceState({}, '');
    }
  }, [location.state]);

  const [q, setQ] = useState('');
  const [aktif, setAktif] = useState<boolean | undefined>(undefined);
  const [rol, setRol] = useState<string>('');
  const debouncedQ = useDebounce(q, 300);

  const [actionError, setActionError] = useState<string | null>(null);

  // Filtre değişince sayfa-state'i yok (liste sayfalanmıyor); yine de hata kutusunu temizle.
  useEffect(() => {
    setActionError(null);
  }, [debouncedQ, aktif, rol]);

  const query = useUsers({
    q: debouncedQ.trim() || undefined,
    aktif,
    rol: rol || undefined,
  });
  const setActiveMut = useSetUserActive();
  const deleteMut = useDeleteUser();

  const users = query.data?.data ?? [];
  const filtered = Boolean(debouncedQ.trim()) || aktif !== undefined || Boolean(rol);

  async function handleSetActive(id: string, next: boolean) {
    setActionError(null);
    try {
      await setActiveMut.mutateAsync({ id, aktif: next });
    } catch (e) {
      setActionError(e instanceof ApiException ? e.message : 'İşlem başarısız oldu');
    }
  }

  async function handleDelete(id: string) {
    if (!window.confirm('Kullanıcı kalıcı olarak silinecek. Emin misiniz?')) return;
    setActionError(null);
    try {
      await deleteMut.mutateAsync(id);
    } catch (e) {
      setActionError(e instanceof ApiException ? e.message : 'Silme başarısız oldu');
    }
  }

  return (
    <>
      <div className="topbar">
        <div>
          <h1>Kullanıcılar</h1>
          <div className="sub">Sistem kullanıcıları ve rolleri</div>
        </div>
        <div className="top-actions">
          <input
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Kullanıcı ara…"
            aria-label="Kullanıcı ara"
            className={inputClass}
          />
          <select
            value={rol}
            onChange={(e) => setRol(e.target.value)}
            aria-label="Rol filtresi"
            className={inputClass}
          >
            <option value="">Tüm roller</option>
            {ASSIGNABLE_ROLES.map((r) => (
              <option key={r} value={r}>
                {roleLabel(r)}
              </option>
            ))}
          </select>
          <button type="button" className="btn btn-primary" onClick={() => navigate('/kullanicilar/yeni')}>
            + Yeni Kullanıcı
          </button>
        </div>
      </div>

      <div className="tabs mb-[18px]">
        {AKTIF_TABS.map((tab) => (
          <button
            key={tab.label}
            type="button"
            onClick={() => setAktif(tab.value)}
            className={`tab${aktif === tab.value ? ' active' : ''}`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {createdNotice && (
        <div className="mb-4 flex items-start justify-between gap-4 rounded-[14px] border border-rasp/30 bg-rasp-soft px-4 py-3 text-[13px] text-ink">
          <span>{createdNotice}</span>
          <button
            type="button"
            className="shrink-0 font-semibold text-rasp"
            onClick={() => setCreatedNotice(null)}
          >
            Kapat
          </button>
        </div>
      )}

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
      ) : users.length === 0 ? (
        <div className="card text-center text-ink-soft">
          {filtered ? 'Sonuç bulunamadı' : 'Henüz kullanıcı yok'}
        </div>
      ) : (
        <div className="card">
          <table className="data-table">
            <thead>
              <tr>
                <th>Kullanıcı Adı</th>
                <th>Ad Soyad</th>
                <th>E-posta</th>
                <th>Telefon</th>
                <th>Roller</th>
                <th>Durum</th>
                <th className="t-right">Aksiyon</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => {
                const self = u.id === meSub;
                return (
                  <tr key={u.id}>
                    <td>
                      <b>{u.kullaniciAdi}</b>
                    </td>
                    <td>
                      {u.ad} {u.soyad}
                    </td>
                    <td className="text-ink-soft">{u.email ?? '—'}</td>
                    <td className="text-ink-soft">{u.telefon ?? '—'}</td>
                    <td>
                      <div className="flex flex-wrap gap-1">
                        {u.roller.length === 0
                          ? '—'
                          : u.roller.map((r) => (
                              <span key={r} className={`badge ${roleBadgeClass(r)}`}>
                                {roleLabel(r)}
                              </span>
                            ))}
                      </div>
                    </td>
                    <td>
                      <span className={`badge ${u.enabled ? 'b-green' : 'b-gray'}`}>
                        {u.enabled ? 'Aktif' : 'Pasif'}
                      </span>
                    </td>
                    <td className="t-right">
                      <div className="inline-flex gap-2">
                        <Link to={`/kullanicilar/${u.id}/duzenle`} className="btn btn-ghost">
                          Düzenle
                        </Link>
                        {!self && (
                          <>
                            <button
                              type="button"
                              className="btn btn-ghost"
                              disabled={setActiveMut.isPending}
                              onClick={() => handleSetActive(u.id, !u.enabled)}
                            >
                              {u.enabled ? 'Pasifleştir' : 'Aktifleştir'}
                            </button>
                            <button
                              type="button"
                              className="btn btn-ghost"
                              disabled={deleteMut.isPending}
                              onClick={() => handleDelete(u.id)}
                            >
                              Sil
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
