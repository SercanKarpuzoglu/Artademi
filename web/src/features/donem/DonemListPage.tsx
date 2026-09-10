import { Link, useNavigate } from 'react-router-dom';
import { ApiException } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { Role } from '../../auth/roles';
import SilButonu from '../../components/SilButonu';
import { formatDate } from '../../lib/format';
import { useDonemler, useSetDonemActive } from './useDonemler';

/**
 * Dönemler (Dalga E): "2026-27 Güz · 14 Eyl – 31 Oca". Grup bir döneme bağlanır; dönemlik kayıtta kredi
 * (ders sayısı) ve tek tahakkuk bu aralıktan hesaplanır.
 */
export default function DonemListPage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole(Role.ADMIN);
  const navigate = useNavigate();
  const q = useDonemler();
  const setActiveMut = useSetDonemActive();
  const liste = q.data ?? [];
  const bugun = new Date().toISOString().slice(0, 10);

  return (
    <>
      <div className="topbar">
        <div>
          <h1>Dönemler</h1>
          <div className="sub">Eğitim dönemleri; gruplar bir döneme bağlanır, dönemlik kayıt buradan hesaplanır</div>
        </div>
        {isAdmin && (
          <div className="top-actions">
            <button type="button" className="btn btn-primary" onClick={() => navigate('/donemler/yeni')}>
              + Yeni Dönem
            </button>
          </div>
        )}
      </div>

      {q.isLoading ? (
        <div className="card text-center text-ink-soft">Yükleniyor…</div>
      ) : q.isError ? (
        <div className="card text-center text-red">
          {q.error instanceof ApiException ? q.error.message : 'Bir hata oluştu'}
        </div>
      ) : liste.length === 0 ? (
        <div className="card text-center text-ink-soft">
          Henüz dönem yok. Örnek: "2026-27 Güz" 14 Eylül – 31 Ocak, "2026-27 Bahar" 1 Şubat – 15 Haziran.
        </div>
      ) : (
        <div className="card">
          <table className="data-table">
            <thead>
              <tr>
                <th>Ad</th>
                <th>Başlangıç</th>
                <th>Bitiş</th>
                <th>Durum</th>
                {isAdmin && <th className="t-right">Aksiyon</th>}
              </tr>
            </thead>
            <tbody>
              {liste.map((d) => {
                const devam = d.baslangic <= bugun && bugun <= d.bitis;
                return (
                  <tr key={d.id} className={d.aktif ? '' : 'opacity-60'}>
                    <td>
                      <b>{d.ad}</b>
                      {devam && <span className="badge b-rasp ml-2">Şu an</span>}
                    </td>
                    <td className="text-ink-soft">{formatDate(d.baslangic)}</td>
                    <td className="text-ink-soft">{formatDate(d.bitis)}</td>
                    <td>
                      <span className={`badge ${d.aktif ? 'b-green' : 'b-gray'}`}>{d.aktif ? 'Aktif' : 'Pasif'}</span>
                    </td>
                    {isAdmin && (
                      <td className="t-right">
                        <div className="inline-flex gap-2">
                          <Link to={`/donemler/${d.id}/duzenle`} className="btn btn-ghost">
                            Düzenle
                          </Link>
                          <button
                            type="button"
                            className="btn btn-ghost"
                            disabled={setActiveMut.isPending}
                            onClick={() => setActiveMut.mutate({ id: d.id, aktif: !d.aktif })}
                          >
                            {d.aktif ? 'Pasifleştir' : 'Aktifleştir'}
                          </button>
                          <SilButonu tur="donem" id={d.id} ad={d.ad} />
                        </div>
                      </td>
                    )}
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
