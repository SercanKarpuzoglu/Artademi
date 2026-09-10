import { useState, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiException } from '../../api/client';
import { indirKayitFormu } from '../../api/students';
import type { StudentResponse } from '../../api/types';
import { useDebounce } from '../../lib/useDebounce';
import { DURUM_BADGE, DURUM_LABEL, TIP_BADGE, TIP_LABEL } from '../group/groupDisplay';
import { useGroups } from '../group/useGroups';
import { useEnrollStudent, useLeaveFromStudent, useStudentEnrollments } from './useStudentEnrollments';
import { useAuth } from '../../auth/AuthContext';
import { Role } from '../../auth/roles';
import StatusBadge from '../../components/StatusBadge';
import StudentFinanceCard from '../finance/StudentFinanceCard';
import { formatDate } from '../../lib/format';
import { useSiblings, useStudent } from './useStudentMutations';

export default function StudentDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params.id ? Number(params.id) : undefined;
  const navigate = useNavigate();
  const { hasAnyRole } = useAuth();
  const canSeeFinance = hasAnyRole([Role.ADMIN, Role.FRONTDESK_ACCOUNTING]);

  // ⚠️ Hook'lar kosullu return'lerden ONCE tanimlanmali (React hook kurali).
  const [formIndiriliyor, setFormIndiriliyor] = useState(false);
  const kayitFormuIndir = async (id: number) => {
    setFormIndiriliyor(true);
    try {
      const { blob, dosyaAdi } = await indirKayitFormu(id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = dosyaAdi;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } finally {
      setFormIndiriliyor(false);
    }
  };


  const studentQuery = useStudent(id);
  const siblingsQuery = useSiblings(id);

  if (studentQuery.isLoading) {
    return <CenteredMessage>Yükleniyor…</CenteredMessage>;
  }
  if (studentQuery.isError) {
    const err = studentQuery.error;
    const notFound = err instanceof ApiException && err.code === 'NOT_FOUND';
    return (
      <CenteredMessage tone="error">
        {notFound ? 'Öğrenci bulunamadı' : err instanceof ApiException ? err.message : 'Bir hata oluştu'}
      </CenteredMessage>
    );
  }

  const s = studentQuery.data as StudentResponse;

  return (
    <div className="mx-auto max-w-3xl">
      <div className="topbar">
        <div className="flex items-center gap-3">
          <h1>
            {s.ad} {s.soyad}
          </h1>
          <StatusBadge status={s.status} />
        </div>
        <div className="top-actions">
          <button
            type="button"
            className="btn btn-ghost"
            disabled={formIndiriliyor}
            onClick={() => kayitFormuIndir(s.id)}
          >
            {formIndiriliyor ? 'Hazırlanıyor…' : 'Kayıt Formu (PDF)'}
          </button>
          <Link to={`/ogrenciler/${s.id}/duzenle`} className="btn btn-primary">
            Düzenle
          </Link>
          <button type="button" className="btn btn-ghost" onClick={() => navigate('/ogrenciler')}>
            Listeye dön
          </button>
        </div>
      </div>

      <div className="space-y-4">
        {/* Kunye */}
        <Section title="Künye">
          <dl className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-2">
            <Info label="TC Kimlik No" value={s.tcKimlikNo} mono />
            <Info label="Doğum Tarihi" value={formatDate(s.dogumTarihi)} />
            <Info label="Telefon" value={s.telefon} />
            <Info label="Yetişkin mi" value={s.yetiskinMi ? 'Evet' : 'Hayır'} />
          </dl>
        </Section>

        {/* Gruplar / kayıtlar — öğrenci sayfasından gruba atama (Dalga A) */}
        <Section title="Gruplar / Kayıtlar">
          <KayitPaneli student={s} />
        </Section>

        {/* Finans — yalnizca ADMIN / FRONTDESK_ACCOUNTING (para hassas) */}
        {canSeeFinance && id !== undefined && <StudentFinanceCard studentId={id} />}

        {/* Veli */}
        <Section title="Veli Bilgileri">
          {s.yetiskinMi && (
            <p className="mb-2 text-xs text-gray-500">Yetişkin öğrenci — veli bilgisi zorunlu değil.</p>
          )}
          <dl className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-3">
            <Info label="Anne Ad" value={s.anneAd} />
            <Info label="Anne TC" value={s.anneTcKimlikNo} mono />
            <Info label="Anne Telefon" value={s.anneTelefon} />
            <Info label="Baba Ad" value={s.babaAd} />
            <Info label="Baba TC" value={s.babaTcKimlikNo} mono />
            <Info label="Baba Telefon" value={s.babaTelefon} />
            <Info label="Veli Meslek" value={s.veliMeslek} />
            <Info label="Veli E-posta" value={s.veliMail} />
            <Info label="Ev Adresi" value={s.evAdresi} />
          </dl>
        </Section>

        {/* Kardesler */}
        <Section title="Kardeşler">
          {siblingsQuery.isLoading ? (
            <p className="text-sm text-gray-500">Kardeşler yükleniyor…</p>
          ) : siblingsQuery.isError ? (
            <p className="text-sm text-red-700">
              {siblingsQuery.error instanceof ApiException
                ? siblingsQuery.error.message
                : 'Kardeşler yüklenemedi'}
            </p>
          ) : (siblingsQuery.data?.length ?? 0) === 0 ? (
            <p className="text-sm text-gray-500">Kardeş kaydı yok</p>
          ) : (
            <ul className="divide-y divide-gray-100">
              {siblingsQuery.data!.map((k) => (
                <li key={k.id}>
                  <Link
                    to={`/ogrenciler/${k.id}`}
                    className="flex items-center justify-between py-2 hover:bg-gray-50"
                  >
                    <span className="text-sm text-gray-800">
                      {k.ad} {k.soyad}
                    </span>
                    <StatusBadge status={k.status} />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Section>
      </div>
    </div>
  );
}

const pickerClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

/** Öğrencinin grup kayıtları + "Gruba ekle" arama kutusu. Aynı iş grup sayfasından da yapılabilir. */
function KayitPaneli({ student }: { student: StudentResponse }) {
  const kayitlar = useStudentEnrollments(student.id);
  const ekleMut = useEnrollStudent(student.id);
  const cikarMut = useLeaveFromStudent(student.id);
  const [q, setQ] = useState('');
  const debouncedQ = useDebounce(q, 300);
  const [hata, setHata] = useState<string | null>(null);
  const [eklendi, setEklendi] = useState<string | null>(null);
  const gruplar = useGroups({ q: debouncedQ.trim() || undefined, aktif: true, size: 10 });
  const adaylar = debouncedQ.trim() ? gruplar.data?.data ?? [] : [];
  const liste = [...(kayitlar.data?.data ?? [])].sort((a, b) =>
    a.durum === b.durum ? b.id - a.id : a.durum === 'AKTIF' ? -1 : 1,
  );
  const yazilabilir = student.status === 'AKTIF' || student.status === 'DENEME';

  async function ekle(grupId: number, grupAd: string) {
    setHata(null);
    setEklendi(null);
    try {
      await ekleMut.mutateAsync({ ogrenciId: student.id, grupId });
      setQ('');
      setEklendi(grupAd);
    } catch (e) {
      if (e instanceof ApiException) {
        setHata(e.code === 'CONFLICT' ? 'Bu öğrenci gruba zaten kayıtlı' : e.message);
      } else {
        setHata('Beklenmeyen bir hata oluştu.');
      }
    }
  }

  function cikar(id: number) {
    if (!window.confirm('Öğrenciyi gruptan çıkar?')) return;
    cikarMut.mutate(id);
  }

  return (
    <div className="space-y-3">
      {yazilabilir ? (
        <div className="relative">
          <input
            type="search"
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setHata(null);
            }}
            placeholder="Gruba ekle: grup adı ara…"
            aria-label="Grup ara ve ekle"
            className={pickerClass}
            disabled={ekleMut.isPending}
          />
          {adaylar.length > 0 && (
            <ul className="absolute z-10 mt-1 max-h-64 w-full overflow-auto rounded-[10px] border border-line bg-card shadow-lg">
              {adaylar.map((g) => (
                <li key={g.id}>
                  <button
                    type="button"
                    className="flex w-full items-center justify-between px-3 py-2 text-left text-[13.5px] hover:bg-gray-50"
                    disabled={ekleMut.isPending}
                    onClick={() => ekle(g.id, g.ad)}
                  >
                    <span className="flex items-center gap-2">
                      {g.ad}
                      <span className={`badge ${TIP_BADGE[g.tip]}`}>{TIP_LABEL[g.tip]}</span>
                    </span>
                    <span className="text-xs text-ink-soft">
                      {g.brans?.ad ?? ''}
                      {g.ogretmen ? ` · ${g.ogretmen.ad} ${g.ogretmen.soyad}` : ''}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
          {hata && <p className="mt-1 text-xs text-red">{hata}</p>}
        </div>
      ) : (
        <p className="text-[13px] text-ink-soft">
          Pasif veya dondurulmuş öğrenci gruba yazılamaz; önce statüyü değiştirin.
        </p>
      )}

      {eklendi && student.status === 'DENEME' && (
        <div role="status" className="rounded-[12px] border border-amber/40 bg-amber-soft px-4 py-3 text-[13px]">
          <p className="font-semibold text-amber">{eklendi} grubuna Deneme statüsünde yazıldı</p>
          <p className="mt-1 text-ink-soft">
            Statü kendiliğinden değişmez: öğrenci Aktif listesinde görünmez ve aylık aidat tahakkuku
            üretilmez. Gerçek kayıtsa{' '}
            <Link to={`/ogrenciler/${student.id}/duzenle`} className="text-rasp underline">
              statüyü Aktif yapın
            </Link>
            .
          </p>
        </div>
      )}

      {kayitlar.isLoading ? (
        <p className="text-sm text-ink-soft">Yükleniyor…</p>
      ) : kayitlar.isError ? (
        <p className="text-sm text-red">
          {kayitlar.error instanceof ApiException ? kayitlar.error.message : 'Kayıtlar yüklenemedi'}
        </p>
      ) : liste.length === 0 ? (
        <p className="text-sm text-ink-soft">Henüz bir gruba kayıtlı değil</p>
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>Grup</th>
              <th>Kayıt Tarihi</th>
              <th>Durum</th>
              <th className="t-right">Aksiyon</th>
            </tr>
          </thead>
          <tbody>
            {liste.map((e) => (
              <tr key={e.id}>
                <td>
                  <Link to={`/gruplar/${e.grup.id}`} className="font-semibold hover:underline">
                    {e.grup.ad}
                  </Link>{' '}
                  <span className={`badge ${TIP_BADGE[e.grup.tip]}`}>{TIP_LABEL[e.grup.tip]}</span>
                </td>
                <td className="text-ink-soft">{formatDate(e.kayitTarihi)}</td>
                <td>
                  <span className={`badge ${DURUM_BADGE[e.durum]}`}>{DURUM_LABEL[e.durum]}</span>
                  {e.ayrilmaTarihi && (
                    <span className="ml-2 text-xs text-ink-soft">{formatDate(e.ayrilmaTarihi)}</span>
                  )}
                </td>
                <td className="t-right">
                  {e.durum === 'AKTIF' && (
                    <button
                      type="button"
                      className="btn btn-ghost"
                      disabled={cikarMut.isPending}
                      onClick={() => cikar(e.id)}
                    >
                      Çıkar
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
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

function Info({ label, value, mono }: { label: string; value?: string | null; mono?: boolean }) {
  const display = value && value.trim() ? value : '—';
  return (
    <div>
      <dt className="text-xs text-gray-500">{label}</dt>
      <dd className={mono ? 'font-mono text-sm text-gray-800' : 'text-sm text-gray-800'}>{display}</dd>
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
