import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ApiException } from '../../api/client';
import type { AttendanceGroupRef, SessionResponse } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Role } from '../../auth/roles';
import { formatDate } from '../../lib/format';
import { useGroups } from '../group/useGroups';
import RollPanel from './RollPanel';
import { useSessions, useTeacherGroups } from './useAttendance';

const inputClass =
  'rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';
const OFIS = [Role.ADMIN, Role.FRONTDESK, Role.FRONTDESK_ACCOUNTING] as const;
const PAGE_SIZE = 20;

function iso(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/**
 * Yoklama Listesi (Dalga C): geçmiş oturumlar tarih aralığı + grup filtresiyle; satır açılınca
 * yoklama panelinde düzenlenir. Eğitmen yalnız kendi gruplarını görür ve kilitli oturumu değiştiremez.
 */
export default function YoklamaListesiPage() {
  const { hasAnyRole } = useAuth();
  const ofis = hasAnyRole(OFIS);
  const [params] = useSearchParams();
  const [grupId, setGrupId] = useState<number | undefined>(
    params.get('grupId') ? Number(params.get('grupId')) : undefined,
  );
  const [from, setFrom] = useState(() => params.get('tarih') ?? iso(new Date(Date.now() - 29 * 86400000)));
  const [to, setTo] = useState(() => params.get('tarih') ?? iso(new Date()));
  const [page, setPage] = useState(0);
  const [acikId, setAcikId] = useState<number | null>(null);

  const ofisGruplar = useGroups(ofis ? { aktif: true, size: 200 } : { size: 0 });
  const egitmenGruplar = useTeacherGroups(!ofis);
  const grupSecenekleri: AttendanceGroupRef[] = ofis
    ? (ofisGruplar.data?.data ?? []).map((g) => ({ id: g.id, ad: g.ad, tip: g.tip }))
    : egitmenGruplar.data ?? [];

  const query = useSessions({ grupId, from: from || undefined, to: to || undefined, page, size: PAGE_SIZE });
  const sessions = query.data?.data ?? [];
  const meta = query.data?.meta;

  return (
    <>
      <div className="topbar">
        <div>
          <h1>Yoklama Listesi</h1>
          <div className="sub">
            {ofis
              ? 'Geçmiş oturumları görün ve düzeltin'
              : 'Kendi derslerinizin yoklamaları; kaydedilenler kilitlidir'}
          </div>
        </div>
      </div>

      <div className="card mb-4 flex flex-wrap items-end gap-3">
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-gray-700">Grup</span>
          <select
            className={inputClass}
            value={grupId ?? ''}
            onChange={(e) => {
              setGrupId(e.target.value ? Number(e.target.value) : undefined);
              setPage(0);
            }}
          >
            <option value="">Tüm gruplar</option>
            {grupSecenekleri.map((g) => (
              <option key={g.id} value={g.id}>
                {g.ad}
              </option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-gray-700">Başlangıç</span>
          <input type="date" className={inputClass} value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); }} />
        </label>
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-gray-700">Bitiş</span>
          <input type="date" className={inputClass} value={to} onChange={(e) => { setTo(e.target.value); setPage(0); }} />
        </label>
      </div>

      {query.isLoading ? (
        <div className="card text-center text-ink-soft">Yükleniyor…</div>
      ) : query.isError ? (
        <div className="card text-center text-red">
          {query.error instanceof ApiException ? query.error.message : 'Bir hata oluştu'}
        </div>
      ) : sessions.length === 0 ? (
        <div className="card text-center text-ink-soft">Bu aralıkta yoklama oturumu yok</div>
      ) : (
        <>
          <div className="card">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Tarih</th>
                  <th>Grup</th>
                  <th>Katılım</th>
                  <th>Kayıt</th>
                  <th className="t-right">İşlem</th>
                </tr>
              </thead>
              <tbody>
                {sessions.map((s) => (
                  <Satir key={s.id} s={s} acik={acikId === s.id} onToggle={() => setAcikId(acikId === s.id ? null : s.id)} />
                ))}
              </tbody>
            </table>
          </div>
          {meta && (
            <div className="mt-4 flex items-center justify-between text-[13px] text-ink-soft">
              <span>
                Toplam {meta.totalElements} · Sayfa {meta.page + 1}/{Math.max(meta.totalPages, 1)}
              </span>
              <div className="flex gap-2">
                <button type="button" className="btn btn-ghost disabled:opacity-40" onClick={() => setPage((p) => Math.max(p - 1, 0))} disabled={meta.page <= 0}>
                  Önceki
                </button>
                <button type="button" className="btn btn-ghost disabled:opacity-40" onClick={() => setPage((p) => p + 1)} disabled={meta.page + 1 >= meta.totalPages}>
                  Sonraki
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </>
  );
}

function Satir({ s, acik, onToggle }: { s: SessionResponse; acik: boolean; onToggle: () => void }) {
  const geldi = s.entries.filter((e) => e.durum === 'GELDI').length;
  const gelmedi = s.entries.filter((e) => e.durum === 'GELMEDI').length;
  const izinli = s.entries.length - geldi - gelmedi;
  return (
    <>
      <tr className="cursor-pointer" onClick={onToggle}>
        <td className="text-ink-soft">{formatDate(s.tarih)}</td>
        <td>
          <b>{s.grup?.ad ?? '—'}</b>
        </td>
        <td>
          <span className="badge b-green">{geldi} geldi</span>{' '}
          <span className="badge b-red">{gelmedi} gelmedi</span>
          {izinli > 0 && <span className="badge b-amber"> {izinli} izinli</span>}
        </td>
        <td>
          {s.kaydedildi ? (
            <span className="badge b-green">Kaydedildi{s.kaydeden ? ` · ${s.kaydeden}` : ''}</span>
          ) : (
            <span className="badge b-gray">Henüz kaydedilmedi</span>
          )}
        </td>
        <td className="t-right">
          <button type="button" className="btn btn-ghost" onClick={(e) => { e.stopPropagation(); onToggle(); }}>
            {acik ? 'Kapat' : 'Aç / Düzenle'}
          </button>
        </td>
      </tr>
      {acik && (
        <tr>
          <td colSpan={5} className="bg-paper">
            <RollPanel session={s} kompakt />
          </td>
        </tr>
      )}
    </>
  );
}
