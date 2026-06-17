import type { ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiException } from '../../api/client';
import type { StudentResponse } from '../../api/types';
import StatusBadge from '../../components/StatusBadge';
import { formatDate } from '../../lib/format';
import { useSiblings, useStudent } from './useStudentMutations';

export default function StudentDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params.id ? Number(params.id) : undefined;
  const navigate = useNavigate();

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
    <div className="min-h-screen bg-gray-50">
      <header className="border-b bg-white">
        <div className="mx-auto flex max-w-3xl items-center justify-between gap-3 px-4 py-3">
          <div className="flex items-center gap-3">
            <h1 className="text-lg font-semibold text-gray-800">
              {s.ad} {s.soyad}
            </h1>
            <StatusBadge status={s.status} />
          </div>
          <div className="flex gap-2 text-sm">
            <Link
              to={`/students/${s.id}/edit`}
              className="rounded-lg bg-indigo-600 px-3 py-1 font-medium text-white hover:bg-indigo-700"
            >
              Düzenle
            </Link>
            <button
              type="button"
              onClick={() => navigate('/')}
              className="rounded-lg border border-gray-300 px-3 py-1 text-gray-700 hover:bg-gray-50"
            >
              Listeye dön
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-3xl space-y-4 px-4 py-6">
        {/* Kunye */}
        <Section title="Künye">
          <dl className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-2">
            <Info label="TC Kimlik No" value={s.tcKimlikNo} mono />
            <Info label="Doğum Tarihi" value={formatDate(s.dogumTarihi)} />
            <Info label="Telefon" value={s.telefon} />
            <Info label="Yetişkin mi" value={s.yetiskinMi ? 'Evet' : 'Hayır'} />
          </dl>
        </Section>

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
                    to={`/students/${k.id}`}
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
      </main>
    </div>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="space-y-3 rounded-lg border bg-white p-4">
      <h2 className="text-sm font-semibold text-gray-700">{title}</h2>
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
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <p className={tone === 'error' ? 'text-red-700' : 'text-gray-500'}>{children}</p>
    </div>
  );
}
