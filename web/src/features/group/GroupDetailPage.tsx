import { useState, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiException } from '../../api/client';
import type { EnrollmentDurumu, GroupResponse } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Role } from '../../auth/roles';
import { formatDate, formatMoney } from '../../lib/format';
import { useDebounce } from '../../lib/useDebounce';
import { useStudents } from '../student/useStudents';
import GroupSchedulePanel from './GroupSchedulePanel';
import { DURUM_BADGE, DURUM_LABEL, TIP_BADGE, TIP_LABEL } from './groupDisplay';
import { useCreateEnrollment, useEnrollments, useLeaveEnrollment } from './useEnrollments';
import { useGroup } from './useGroups';

const ENROLLMENT_ROLES = [Role.ADMIN, Role.FRONTDESK, Role.FRONTDESK_ACCOUNTING] as const;

export default function GroupDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params.id ? Number(params.id) : undefined;
  const navigate = useNavigate();
  const { hasRole, hasAnyRole } = useAuth();
  const isAdmin = hasRole(Role.ADMIN);
  const canManageEnrollment = hasAnyRole(ENROLLMENT_ROLES);

  const groupQuery = useGroup(id);

  if (groupQuery.isLoading) {
    return <CenteredMessage>Yükleniyor…</CenteredMessage>;
  }
  if (groupQuery.isError) {
    const err = groupQuery.error;
    const notFound = err instanceof ApiException && err.code === 'NOT_FOUND';
    return (
      <CenteredMessage tone="error">
        {notFound ? 'Grup bulunamadı' : err instanceof ApiException ? err.message : 'Bir hata oluştu'}
      </CenteredMessage>
    );
  }

  const g = groupQuery.data as GroupResponse;

  return (
    <div className="mx-auto max-w-3xl">
      <div className="topbar">
        <div className="flex items-center gap-3">
          <h1>{g.ad}</h1>
          <span className={`badge ${TIP_BADGE[g.tip]}`}>{TIP_LABEL[g.tip]}</span>
          <span className={`badge ${g.aktif ? 'b-green' : 'b-gray'}`}>
            {g.aktif ? 'Aktif' : 'Pasif'}
          </span>
        </div>
        <div className="top-actions">
          {isAdmin && (
            <Link to={`/gruplar/${g.id}/duzenle`} className="btn btn-primary">
              Düzenle
            </Link>
          )}
          <button type="button" className="btn btn-ghost" onClick={() => navigate('/gruplar')}>
            Listeye dön
          </button>
        </div>
      </div>

      <div className="space-y-4">
        <Section title="Özet">
          <dl className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-2">
            <Info label="Branş" value={g.brans?.ad} />
            <Info
              label="Öğretmen"
              value={g.ogretmen ? `${g.ogretmen.ad} ${g.ogretmen.soyad}` : null}
            />
            <Info label="Salon" value={g.salon?.ad} />
            <Info label="Seviye" value={g.seviye} />
            <div>
              <dt className="text-xs text-gray-500">
                {g.tip === 'GRUP' ? 'Aylık Aidat' : 'Ders Başı Ücret'}
              </dt>
              <dd className="text-sm text-gray-800">
                <span className="amount">
                  {g.tip === 'GRUP'
                    ? formatMoney(g.aylikAidat)
                    : formatMoney(g.dersBasiUcret)}{' '}
                  ₺
                </span>
              </dd>
            </div>
          </dl>
        </Section>

        {id !== undefined && (
          <EnrollmentSection groupId={id} canManage={canManageEnrollment} />
        )}

        {id !== undefined && (
          <GroupSchedulePanel groupId={id} tip={g.tip} isAdmin={isAdmin} />
        )}
      </div>
    </div>
  );
}

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

function EnrollmentSection({ groupId, canManage }: { groupId: number; canManage: boolean }) {
  const [showLeft, setShowLeft] = useState(false);
  const durum: EnrollmentDurumu | undefined = showLeft ? undefined : 'AKTIF';

  const enrollmentsQuery = useEnrollments(groupId, durum);
  const createMut = useCreateEnrollment(groupId);
  const leaveMut = useLeaveEnrollment(groupId);

  const [picker, setPicker] = useState('');
  const debouncedPicker = useDebounce(picker, 300);
  const [pickerError, setPickerError] = useState<string | null>(null);
  const studentsQuery = useStudents({
    q: debouncedPicker.trim() || undefined,
    size: 10,
  });
  const studentMatches =
    debouncedPicker.trim() && canManage ? studentsQuery.data?.data ?? [] : [];

  const enrollments = enrollmentsQuery.data?.data ?? [];

  async function onAdd(ogrenciId: number) {
    setPickerError(null);
    try {
      await createMut.mutateAsync({ ogrenciId, grupId: groupId });
      setPicker('');
    } catch (e) {
      if (e instanceof ApiException) {
        if (e.code === 'CONFLICT') {
          setPickerError('Bu öğrenci gruba zaten kayıtlı');
        } else {
          setPickerError(e.message);
        }
      } else {
        setPickerError('Beklenmeyen bir hata oluştu.');
      }
    }
  }

  function onLeave(enrollmentId: number) {
    if (!window.confirm('Öğrenciyi gruptan çıkar?')) {
      return;
    }
    leaveMut.mutate(enrollmentId);
  }

  return (
    <section className="card space-y-3">
      <div className="flex items-center justify-between">
        <h3>Kayıtlı Öğrenciler</h3>
        <label className="flex items-center gap-2 text-[13px] text-ink-soft">
          <input
            type="checkbox"
            className="h-4 w-4"
            checked={showLeft}
            onChange={(e) => setShowLeft(e.target.checked)}
          />
          Ayrılanları göster
        </label>
      </div>

      {canManage && (
        <div className="relative">
          <input
            type="search"
            value={picker}
            onChange={(e) => {
              setPicker(e.target.value);
              setPickerError(null);
            }}
            placeholder="Öğrenci ekle: ad, soyad veya TC ara…"
            aria-label="Öğrenci ara ve ekle"
            className={inputClass}
            disabled={createMut.isPending}
          />
          {studentMatches.length > 0 && (
            <ul className="absolute z-10 mt-1 max-h-64 w-full overflow-auto rounded-[10px] border border-line bg-card shadow-lg">
              {studentMatches.map((s) => (
                <li key={s.id}>
                  <button
                    type="button"
                    className="flex w-full items-center justify-between px-3 py-2 text-left text-[13.5px] hover:bg-gray-50"
                    disabled={createMut.isPending}
                    onClick={() => onAdd(s.id)}
                  >
                    <span>
                      {s.ad} {s.soyad}
                    </span>
                    <span className="font-mono text-xs text-ink-soft">{s.tcKimlikNo}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
          {pickerError && <p className="mt-1 text-xs text-red">{pickerError}</p>}
        </div>
      )}

      {enrollmentsQuery.isLoading ? (
        <p className="text-sm text-ink-soft">Yükleniyor…</p>
      ) : enrollmentsQuery.isError ? (
        <p className="text-sm text-red">
          {enrollmentsQuery.error instanceof ApiException
            ? enrollmentsQuery.error.message
            : 'Kayıtlar yüklenemedi'}
        </p>
      ) : enrollments.length === 0 ? (
        <p className="text-sm text-ink-soft">
          {showLeft ? 'Kayıt bulunamadı' : 'Aktif kayıtlı öğrenci yok'}
        </p>
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>Öğrenci</th>
              <th>Kayıt Tarihi</th>
              <th>Durum</th>
              {canManage && <th className="t-right">Aksiyon</th>}
            </tr>
          </thead>
          <tbody>
            {enrollments.map((e) => (
              <tr key={e.id}>
                <td>
                  <b>
                    {e.ogrenci.ad} {e.ogrenci.soyad}
                  </b>
                </td>
                <td className="text-ink-soft">{formatDate(e.kayitTarihi)}</td>
                <td>
                  <span className={`badge ${DURUM_BADGE[e.durum]}`}>{DURUM_LABEL[e.durum]}</span>
                </td>
                {canManage && (
                  <td className="t-right">
                    {e.durum === 'AKTIF' && (
                      <button
                        type="button"
                        className="btn btn-ghost"
                        disabled={leaveMut.isPending}
                        onClick={() => onLeave(e.id)}
                      >
                        Çıkar
                      </button>
                    )}
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="card space-y-3">
      <h3>{title}</h3>
      {children}
    </section>
  );
}

function Info({ label, value }: { label: string; value?: string | null }) {
  const display = value && value.trim() ? value : '—';
  return (
    <div>
      <dt className="text-xs text-gray-500">{label}</dt>
      <dd className="text-sm text-gray-800">{display}</dd>
    </div>
  );
}

function CenteredMessage({ children, tone }: { children: ReactNode; tone?: 'error' }) {
  return (
    <div className="card text-center">
      <p className={tone === 'error' ? 'text-red' : 'text-ink-soft'}>{children}</p>
    </div>
  );
}
