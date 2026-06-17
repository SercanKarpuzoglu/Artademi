import { useState } from 'react';
import { ApiException } from '../../api/client';
import { getStudents } from '../../api/students';
import { keycloak } from '../../lib/keycloak';

interface ConnState {
  loading: boolean;
  ok: string | null;
  error: string | null;
}

/**
 * Iskelet kanit ekrani: login -> token -> backend -> tenant zincirinin calistigini gosterir.
 * Henuz ogrenci tablosu/formu YOK (3b/3c).
 */
export default function HomePage() {
  const [conn, setConn] = useState<ConnState>({ loading: false, ok: null, error: null });

  // JWT claim'leri (debug amacli gosterilir).
  const claims = keycloak.tokenParsed as
    | { preferred_username?: string; tenant_id?: string; realm_access?: { roles?: string[] } }
    | undefined;
  const username = claims?.preferred_username ?? 'bilinmiyor';
  const tenantId = claims?.tenant_id ?? '(yok)';
  const roles = claims?.realm_access?.roles ?? [];

  async function testConnection() {
    setConn({ loading: true, ok: null, error: null });
    try {
      const res = await getStudents({ page: 0, size: 5 });
      const count = res.meta?.totalElements ?? res.data.length;
      setConn({ loading: false, ok: `Bağlantı OK, ${count} öğrenci`, error: null });
    } catch (e) {
      const message =
        e instanceof ApiException ? `${e.code}: ${e.message}` : 'Bağlantı kurulamadı';
      setConn({ loading: false, ok: null, error: message });
    }
  }

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="w-full max-w-md space-y-5 rounded-xl bg-white p-6 shadow">
        <h1 className="text-xl font-semibold text-gray-800">Hoş geldin, {username}</h1>

        <dl className="space-y-1 text-sm text-gray-600">
          <div className="flex gap-2">
            <dt className="font-medium">tenant_id:</dt>
            <dd className="font-mono break-all">{tenantId}</dd>
          </div>
          <div className="flex gap-2">
            <dt className="font-medium">roller:</dt>
            <dd className="font-mono">{roles.length ? roles.join(', ') : '(yok)'}</dd>
          </div>
        </dl>

        <button
          type="button"
          onClick={testConnection}
          disabled={conn.loading}
          className="w-full rounded-lg bg-indigo-600 py-2 font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
        >
          {conn.loading ? 'Test ediliyor…' : 'Bağlantıyı test et'}
        </button>

        {conn.ok && <p className="text-sm text-green-700">{conn.ok}</p>}
        {conn.error && <p className="text-sm text-red-700">{conn.error}</p>}

        <button
          type="button"
          onClick={() => keycloak.logout()}
          className="w-full rounded-lg border border-gray-300 py-2 text-gray-700 hover:bg-gray-50"
        >
          Çıkış yap
        </button>
      </div>
    </div>
  );
}
