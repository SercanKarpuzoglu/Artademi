import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiException } from '../../api/client';
import { getPlatformAudit } from '../../api/platform';
import type { AuditAction, AuditRow } from '../../api/types';

const SAYFA_BOYUTU = 25;

/** İşlem tipine göre rozet rengi — yıkıcı işlemler görsel olarak ayrışsın. */
const ROZET: Record<AuditAction, string> = {
  KURUM_OLUSTURULDU: 'b-green',
  KURUM_DURUMU_DEGISTI: 'b-amber',
  KURUM_SILINDI: 'b-red',
  KULLANICI_EKLENDI: 'b-green',
  KULLANICI_SILINDI: 'b-red',
  ABONELIK_GUNCELLENDI: 'b-amber',
};

/**
 * Platform denetim izi (SUPER_ADMIN): kim, ne zaman, hangi kuruma ne yaptı.
 * Kayıtlar salt-okunur — düzenleme/silme yoktur, izin değeri değiştirilemez olmasından gelir.
 */
export default function PlatformDenetimPage() {
  const [sayfa, setSayfa] = useState(0);
  const q = useQuery({
    queryKey: ['platform', 'audit', sayfa],
    queryFn: () => getPlatformAudit({ page: sayfa, size: SAYFA_BOYUTU }),
    placeholderData: keepPreviousData,
  });

  return (
    <div className="space-y-5">
      <div className="topbar">
        <div>
          <h1>Denetim İzi</h1>
          <div className="sub">Platform üzerinde yapılan işlemler</div>
        </div>
      </div>

      <div className="card">
        {q.isLoading ? (
          <p className="py-8 text-center text-ink-soft">Yükleniyor…</p>
        ) : q.isError ? (
          <p className="py-8 text-center text-red">
            {q.error instanceof ApiException ? q.error.message : 'Denetim izi yüklenemedi'}
          </p>
        ) : q.data && q.data.rows.length === 0 ? (
          <p className="py-8 text-center text-[13.5px] text-ink-soft">
            Henüz kayıtlı işlem yok. Kurum açma, askıya alma, kullanıcı ekleme gibi işlemler
            burada iz bırakır.
          </p>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>İşlem</th>
                    <th>Kurum</th>
                    <th>Ayrıntı</th>
                    <th>Yapan</th>
                    <th>Tarih</th>
                  </tr>
                </thead>
                <tbody>
                  {q.data?.rows.map((r) => (
                    <DenetimSatiri key={r.id} r={r} />
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
    </div>
  );
}

function DenetimSatiri({ r }: { r: AuditRow }) {
  return (
    <tr>
      <td>
        <span className={`badge ${ROZET[r.action] ?? 'b-gray'}`}>{r.actionEtiketi}</span>
      </td>
      <td>
        {r.targetTenantId ? (
          // Kurum silinmiş olabilir; ad snapshot olduğu için yine de okunur.
          <Link to={`/platform/tenants/${r.targetTenantId}`} className="hover:text-rasp">
            {r.targetAd ?? '—'}
          </Link>
        ) : (
          (r.targetAd ?? '—')
        )}
      </td>
      <td className="text-ink-soft">{r.detail ?? '—'}</td>
      <td>{r.actor}</td>
      <td className="whitespace-nowrap text-ink-soft">{zamanFormat(r.createdAt)}</td>
    </tr>
  );
}

function zamanFormat(iso: string): string {
  return new Date(iso).toLocaleString('tr-TR', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
