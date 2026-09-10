import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ApiException } from '../../api/client';
import { getMySchedules, getSchedules } from '../../api/schedules';
import type { HaftaGunu, ScheduleResponse } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Role } from '../../auth/roles';
import { GUN_LABEL, GUN_ORDER, toHm } from '../group/scheduleDisplay';

const OFIS = [Role.ADMIN, Role.FRONTDESK, Role.FRONTDESK_ACCOUNTING] as const;

/** JS getDay(): 0=Pazar … 6=Cumartesi -> GUN_ORDER (Pazartesi=0) indeksine cevir. */
function bugunGun(): HaftaGunu {
  return GUN_ORDER[(new Date().getDay() + 6) % 7];
}

/**
 * Haftalık program. Eğitmen yalnız KENDİ derslerini görür (/api/schedules/mine); ofis rolleri tüm
 * aktif grupların ders saatlerini görür. Salt okunur — ders saati grup detayından düzenlenir.
 */
export default function ProgramPage() {
  const { hasRole, hasAnyRole } = useAuth();
  const yalnizEgitmen = hasRole(Role.TEACHER) && !hasAnyRole(OFIS);

  const query = useQuery({
    queryKey: ['program', yalnizEgitmen ? 'mine' : 'hepsi'],
    queryFn: async () =>
      yalnizEgitmen ? getMySchedules() : (await getSchedules({ aktif: true, size: 500 })).data,
  });

  const bugun = bugunGun();
  const dersler = query.data ?? [];
  const gunler = GUN_ORDER.map((gun) => ({
    gun,
    dersler: dersler
      .filter((d) => d.gun === gun && d.aktif)
      .sort((a, b) => a.baslangicSaati.localeCompare(b.baslangicSaati)),
  }));

  return (
    <>
      <div className="topbar">
        <div>
          <h1>{yalnizEgitmen ? 'Haftalık Programım' : 'Haftalık Program'}</h1>
          <div className="sub">
            {yalnizEgitmen
              ? 'Size atanmış aktif grupların ders saatleri'
              : 'Tüm aktif grupların ders saatleri'}{' '}
            · Bugün {GUN_LABEL[bugun]}
          </div>
        </div>
        {yalnizEgitmen && (
          <div className="top-actions">
            <Link to="/yoklama" className="btn btn-primary">
              Yoklama al
            </Link>
          </div>
        )}
      </div>

      {query.isLoading ? (
        <div className="card text-center text-ink-soft">Yükleniyor…</div>
      ) : query.isError ? (
        <div className="card text-center text-red">
          {query.error instanceof ApiException ? query.error.message : 'Program yüklenemedi'}
        </div>
      ) : dersler.length === 0 ? (
        <div className="card text-center text-ink-soft">
          {yalnizEgitmen
            ? 'Size atanmış bir ders saati yok. Yönetici, grubun ders saatlerini grup sayfasından tanımlar.'
            : 'Tanımlı ders saati yok. Grup sayfasından "+ Ders Saati Ekle" ile tanımlayın.'}
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {gunler.map(({ gun, dersler: gunDersleri }) => (
            <section
              key={gun}
              className={`card space-y-2${gun === bugun ? ' ring-1 ring-rasp' : ''}`}
              aria-label={GUN_LABEL[gun]}
            >
              <div className="flex items-center justify-between">
                <h3>{GUN_LABEL[gun]}</h3>
                {gun === bugun && <span className="badge b-rasp">Bugün</span>}
              </div>
              {gunDersleri.length === 0 ? (
                <p className="text-[13px] text-ink-soft">Ders yok</p>
              ) : (
                <ul className="space-y-2">
                  {gunDersleri.map((d) => (
                    <DersSatiri key={d.id} ders={d} egitmenGoster={!yalnizEgitmen} />
                  ))}
                </ul>
              )}
            </section>
          ))}
        </div>
      )}
    </>
  );
}

function DersSatiri({ ders, egitmenGoster }: { ders: ScheduleResponse; egitmenGoster: boolean }) {
  const ozel = ders.grup?.tip === 'OZEL';
  return (
    <li className={ozel ? 'lesson private' : 'lesson group'}>
      <div className="flex items-center justify-between gap-2">
        <b className="text-[13.5px]">
          {toHm(ders.baslangicSaati)}–{toHm(ders.bitisSaati)}
        </b>
        <span className={`badge ${ozel ? 'b-amber' : 'b-rasp'}`}>{ozel ? 'Özel' : 'Grup'}</span>
      </div>
      <div className="text-[13.5px]">
        {ders.grup ? (
          egitmenGoster ? (
            <Link to={`/gruplar/${ders.grup.id}`} className="font-semibold hover:underline">
              {ders.grup.ad}
            </Link>
          ) : (
            <span className="font-semibold">{ders.grup.ad}</span>
          )
        ) : (
          '—'
        )}
      </div>
      <div className="text-[12.5px] text-ink-soft">
        {ders.salon ? ders.salon.ad : 'Salon yok'}
        {egitmenGoster && ders.ogretmen && ` · ${ders.ogretmen.ad} ${ders.ogretmen.soyad}`}
      </div>
    </li>
  );
}
