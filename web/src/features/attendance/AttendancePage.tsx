import { useEffect, useMemo, useState } from 'react';
import { ApiException } from '../../api/client';
import type {
  AttendanceGroupRef,
  SessionResponse,
  YoklamaDurumu,
} from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Role } from '../../auth/roles';
import { useGroups } from '../group/useGroups';
import { DURUM_CHIP, DURUM_LABEL, nextDurum } from './attendanceDisplay';
import {
  useCreateSession,
  useSessions,
  useTeacherGroups,
  useUpdateEntries,
} from './useAttendance';

const inputClass =
  'rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

const OFIS = [Role.ADMIN, Role.FRONTDESK, Role.FRONTDESK_ACCOUNTING] as const;
const WRITE_ROLES = [Role.ADMIN, Role.FRONTDESK, Role.TEACHER] as const;

/** Bugunun yerel tarihini YYYY-MM-DD olarak dondurur. */
function todayIso(): string {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export default function AttendancePage() {
  const { hasAnyRole } = useAuth();
  const isOffice = hasAnyRole(OFIS);
  // Ofis degilse (ve sayfaya eristiyse) öğretmendir. Ofis ile öğretmen grup kaynağı farklı
  // olduğu icin (öğretmen /api/groups'tan 403 alir) ayrı bileşenlere ayırırız ki yanlış
  // uç hiç çağrılmasın.
  return isOffice ? <OfficeAttendance /> : <TeacherAttendance />;
}

/** Ofis (ADMIN/FRONTDESK/FRONTDESK_ACCOUNTING): gruplar /api/groups'tan. */
function OfficeAttendance() {
  const groupsQuery = useGroups({ aktif: true, size: 200 });
  const options: AttendanceGroupRef[] = (groupsQuery.data?.data ?? []).map((g) => ({
    id: g.id,
    ad: g.ad,
    tip: g.tip,
  }));
  return (
    <AttendanceShell
      groupOptions={options}
      groupsLoading={groupsQuery.isLoading}
      isTeacher={false}
    />
  );
}

/** Öğretmen: gruplar kendi oturumlarından turetilir (getSessions otomatik daraltilir). */
function TeacherAttendance() {
  const groupsQuery = useTeacherGroups(true);
  return (
    <AttendanceShell
      groupOptions={groupsQuery.data ?? []}
      groupsLoading={groupsQuery.isLoading}
      isTeacher
    />
  );
}

function AttendanceShell({
  groupOptions,
  groupsLoading,
  isTeacher,
}: {
  groupOptions: AttendanceGroupRef[];
  groupsLoading: boolean;
  isTeacher: boolean;
}) {
  const { hasAnyRole } = useAuth();
  const canWrite = hasAnyRole(WRITE_ROLES);

  const [grupId, setGrupId] = useState<number | undefined>(undefined);
  const [tarih, setTarih] = useState<string>(todayIso());

  return (
    <div className="mx-auto max-w-3xl">
      <div className="topbar">
        <div>
          <h1>Yoklama</h1>
          <div className="sub">Grup ve tarih seçip oturum yoklamasını alın</div>
        </div>
      </div>

      <div className="space-y-4">
        <section className="card space-y-3">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-gray-700">Grup</span>
              <select
                className={`${inputClass} w-full`}
                value={grupId ?? ''}
                disabled={groupsLoading}
                onChange={(e) =>
                  setGrupId(e.target.value ? Number(e.target.value) : undefined)
                }
              >
                <option value="">Grup seçin…</option>
                {groupOptions.map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.ad}
                  </option>
                ))}
              </select>
            </label>
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-gray-700">Tarih</span>
              <input
                type="date"
                className={`${inputClass} w-full`}
                value={tarih}
                onChange={(e) => setTarih(e.target.value)}
              />
            </label>
          </div>

          {isTeacher && !groupsLoading && groupOptions.length === 0 && (
            <p className="note">
              Henüz bir grubunuz için oturum yok. (Öğretmen yalnızca daha önce en az bir
              oturumu olan grupları görür.)
            </p>
          )}
        </section>

        {grupId !== undefined && tarih && (
          <SessionPanel
            grupId={grupId}
            tarih={tarih}
            canWrite={canWrite}
          />
        )}
      </div>
    </div>
  );
}

function SessionPanel({
  grupId,
  tarih,
  canWrite,
}: {
  grupId: number;
  tarih: string;
  canWrite: boolean;
}) {
  const sessionsQuery = useSessions({ grupId, tarih });
  const createMut = useCreateSession();

  // grup+tarih icin mevcut oturum (varsa).
  const sessions = sessionsQuery.data?.data ?? [];
  const existing = sessions.find((s) => s.grup?.id === grupId && s.tarih === tarih) ?? null;

  const [openError, setOpenError] = useState<string | null>(null);

  async function onOpenSession() {
    setOpenError(null);
    try {
      await createMut.mutateAsync({ grupId, tarih });
      await sessionsQuery.refetch();
    } catch (e) {
      if (e instanceof ApiException) {
        if (e.code === 'CONFLICT') {
          // Yaris/zaten var -> oturumlari tazele ve yuklenmis olani goster.
          await sessionsQuery.refetch();
        } else {
          setOpenError(e.message);
        }
      } else {
        setOpenError('Beklenmeyen bir hata oluştu.');
      }
    }
  }

  if (sessionsQuery.isLoading) {
    return (
      <section className="card">
        <p className="text-sm text-ink-soft">Yükleniyor…</p>
      </section>
    );
  }
  if (sessionsQuery.isError) {
    return (
      <section className="card">
        <p className="text-sm text-red">
          {sessionsQuery.error instanceof ApiException
            ? sessionsQuery.error.message
            : 'Oturum bilgisi yüklenemedi'}
        </p>
      </section>
    );
  }

  if (!existing) {
    return (
      <section className="card space-y-3">
        <h3>Oturum</h3>
        <p className="text-sm text-ink-soft">Bu grup ve tarih için henüz oturum açılmamış.</p>
        {openError && (
          <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
            {openError}
          </div>
        )}
        {canWrite && (
          <button
            type="button"
            className="btn btn-primary"
            disabled={createMut.isPending}
            onClick={onOpenSession}
          >
            {createMut.isPending ? 'Açılıyor…' : 'Oturum Aç'}
          </button>
        )}
      </section>
    );
  }

  return <RollPanel session={existing} canWrite={canWrite} />;
}

function RollPanel({
  session,
  canWrite,
}: {
  session: SessionResponse;
  canWrite: boolean;
}) {
  const updateMut = useUpdateEntries();
  const [feedback, setFeedback] = useState<
    { tone: 'ok' | 'err'; text: string } | null
  >(null);

  // Yerel durum: ogrenciId -> durum. Yuklenen oturumdan baslatilir.
  const [durumlar, setDurumlar] = useState<Record<number, YoklamaDurumu>>(() =>
    Object.fromEntries(session.entries.map((e) => [e.ogrenci.id, e.durum])),
  );

  // Oturum (id veya girisler) degisince yerel durumu tazele.
  useEffect(() => {
    setDurumlar(Object.fromEntries(session.entries.map((e) => [e.ogrenci.id, e.durum])));
    setFeedback(null);
  }, [session.id, session.entries]);

  function cycle(ogrenciId: number) {
    if (!canWrite) return;
    setFeedback(null);
    setDurumlar((prev) => ({
      ...prev,
      [ogrenciId]: nextDurum(prev[ogrenciId] ?? 'GELMEDI'),
    }));
  }

  const counts = useMemo(() => {
    let geldi = 0;
    let gelmedi = 0;
    let izinli = 0;
    for (const d of Object.values(durumlar)) {
      if (d === 'GELDI') geldi++;
      else if (d === 'GELMEDI') gelmedi++;
      else izinli++;
    }
    return { geldi, gelmedi, izinli };
  }, [durumlar]);

  async function onSave() {
    setFeedback(null);
    const items = session.entries.map((e) => ({
      ogrenciId: e.ogrenci.id,
      durum: durumlar[e.ogrenci.id] ?? 'GELMEDI',
    }));
    try {
      await updateMut.mutateAsync({ sessionId: session.id, items });
      setFeedback({ tone: 'ok', text: 'Yoklama kaydedildi.' });
    } catch (e) {
      const text =
        e instanceof ApiException ? e.message : 'Beklenmeyen bir hata oluştu.';
      setFeedback({ tone: 'err', text });
    }
  }

  return (
    <section className="card space-y-2">
      <h3>Yoklama</h3>

      <div className="roll-legend">
        <span>
          <i style={{ background: 'var(--green)' }} /> Geldi
        </span>
        <span>
          <i style={{ background: 'var(--red)' }} /> Gelmedi
        </span>
        <span>
          <i style={{ background: 'var(--amber)' }} /> İzinli
        </span>
        {canWrite && <span>İsme tıklayarak durum değiştirin</span>}
      </div>

      {session.entries.length === 0 ? (
        <p className="text-sm text-ink-soft">Bu oturumda kayıtlı öğrenci yok.</p>
      ) : (
        <div className="roll">
          {session.entries.map((e) => {
            const d = durumlar[e.ogrenci.id] ?? 'GELMEDI';
            return (
              <button
                key={e.ogrenci.id}
                type="button"
                className={`chip ${DURUM_CHIP[d]}`}
                disabled={!canWrite}
                aria-label={`${e.ogrenci.ad} ${e.ogrenci.soyad}: ${DURUM_LABEL[d]}`}
                onClick={() => cycle(e.ogrenci.id)}
              >
                <span className="dot" />
                {e.ogrenci.ad} {e.ogrenci.soyad}
              </button>
            );
          })}
        </div>
      )}

      <div className="flex items-center justify-between pt-2">
        <div className="text-[13px] font-bold text-ink-soft">
          {counts.geldi} geldi · {counts.gelmedi} gelmedi · {counts.izinli} izinli
        </div>
        {canWrite && (
          <button
            type="button"
            className="btn btn-primary"
            disabled={updateMut.isPending || session.entries.length === 0}
            onClick={onSave}
          >
            {updateMut.isPending ? 'Kaydediliyor…' : 'Kaydet'}
          </button>
        )}
      </div>

      {feedback && (
        <p className={`text-[13px] font-semibold ${feedback.tone === 'ok' ? 'text-green' : 'text-red'}`}>
          {feedback.text}
        </p>
      )}
    </section>
  );
}
