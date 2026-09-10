import { useEffect, useMemo, useState } from 'react';
import { ApiException } from '../../api/client';
import type { SessionResponse, YoklamaDurumu } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Role } from '../../auth/roles';
import SilButonu from '../../components/SilButonu';
import { formatDate, formatDateTime } from '../../lib/format';
import { useUpdateEntries } from './useAttendance';

const OFIS = [Role.ADMIN, Role.FRONTDESK, Role.FRONTDESK_ACCOUNTING] as const;
const WRITE_ROLES = [Role.ADMIN, Role.FRONTDESK, Role.TEACHER] as const;

/**
 * Yoklama paneli (Dalga C tasarımı): öğrenciler yukarıdan aşağıya, her satırda Geldi / Gelmedi
 * düğmeleri (ofis için İzinli de). Eğitmen bir kez Kaydet der, sonrası kilitli — yönetici/ofis
 * düzeltir. Aynı panel Yoklama sayfasında ve Yoklama Listesi'nde kullanılır.
 */
export default function RollPanel({
  session,
  kompakt,
}: {
  session: SessionResponse;
  /** Listede satır içinde gömülü kullanım: başlık ve silme düğmesi gizlenir. */
  kompakt?: boolean;
}) {
  const { hasAnyRole } = useAuth();
  const ofis = hasAnyRole(OFIS);
  const yazabilir = hasAnyRole(WRITE_ROLES);
  // Eğitmen (ofis değil) ve oturum kaydedilmişse kilit.
  const kilitli = !ofis && session.kaydedildi;
  const canWrite = yazabilir && !kilitli;

  const updateMut = useUpdateEntries();
  const [feedback, setFeedback] = useState<{ tone: 'ok' | 'err'; text: string } | null>(null);
  const [durumlar, setDurumlar] = useState<Record<number, YoklamaDurumu>>(() =>
    Object.fromEntries(session.entries.map((e) => [e.ogrenci.id, e.durum])),
  );

  // Oturum tazelenince (kayıt sonrası refetch dahil) yerel durumları eşitle; geri bildirim mesajı
  // yalnız BAŞKA bir oturuma geçince silinir — aksi halde "kaydedildi" yazısı anında kaybolur.
  useEffect(() => {
    setDurumlar(Object.fromEntries(session.entries.map((e) => [e.ogrenci.id, e.durum])));
  }, [session.id, session.entries]);
  useEffect(() => {
    setFeedback(null);
  }, [session.id]);

  function sec(ogrenciId: number, d: YoklamaDurumu) {
    if (!canWrite) return;
    setFeedback(null);
    setDurumlar((prev) => ({ ...prev, [ogrenciId]: d }));
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

  const degisti = session.entries.some((e) => (durumlar[e.ogrenci.id] ?? 'GELMEDI') !== e.durum);

  async function onSave() {
    setFeedback(null);
    const items = session.entries.map((e) => ({
      ogrenciId: e.ogrenci.id,
      durum: durumlar[e.ogrenci.id] ?? 'GELMEDI',
    }));
    try {
      await updateMut.mutateAsync({ sessionId: session.id, items });
      setFeedback({
        tone: 'ok',
        text: ofis ? 'Yoklama kaydedildi.' : 'Yoklama kaydedildi. Düzeltme gerekirse yöneticinize iletin.',
      });
    } catch (e) {
      const text =
        e instanceof ApiException
          ? e.code === 'KILITLI'
            ? 'Bu yoklama kaydedilmiş; düzeltme için yöneticinize başvurun.'
            : e.message
          : 'Beklenmeyen bir hata oluştu.';
      setFeedback({ tone: 'err', text });
    }
  }

  return (
    <section className={kompakt ? 'space-y-2' : 'card space-y-2'}>
      {!kompakt && (
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3>
            Yoklama · {session.grup?.ad ?? '—'} · {formatDate(session.tarih)}
          </h3>
          <KayitDurumu session={session} />
        </div>
      )}

      {kilitli && (
        <div className="rounded-[12px] border border-amber/40 bg-amber-soft px-4 py-2.5 text-[13px]">
          <b className="text-amber">Kaydedildi ve kilitli.</b> Yanlış işaretlediyseniz yöneticiniz
          düzeltebilir.
        </div>
      )}

      {session.entries.length === 0 ? (
        <p className="text-sm text-ink-soft">Bu oturumda kayıtlı öğrenci yok.</p>
      ) : (
        <ul className="divide-y divide-line">
          {session.entries.map((e) => {
            const d = durumlar[e.ogrenci.id] ?? 'GELMEDI';
            return (
              <li key={e.ogrenci.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                <span className="text-[14px] font-semibold">
                  {e.ogrenci.ad} {e.ogrenci.soyad}
                </span>
                <div className="flex gap-1.5" role="radiogroup" aria-label={`${e.ogrenci.ad} ${e.ogrenci.soyad} durumu`}>
                  <DurumDugmesi aktif={d === 'GELDI'} renk="green" disabled={!canWrite} onClick={() => sec(e.ogrenci.id, 'GELDI')}>
                    Geldi
                  </DurumDugmesi>
                  <DurumDugmesi aktif={d === 'GELMEDI'} renk="red" disabled={!canWrite} onClick={() => sec(e.ogrenci.id, 'GELMEDI')}>
                    Gelmedi
                  </DurumDugmesi>
                  {(ofis || d === 'IZINLI') && (
                    <DurumDugmesi aktif={d === 'IZINLI'} renk="amber" disabled={!canWrite || !ofis} onClick={() => sec(e.ogrenci.id, 'IZINLI')}>
                      İzinli
                    </DurumDugmesi>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      )}

      <div className="flex flex-wrap items-center justify-between gap-2 pt-2">
        <div className="text-[13px] font-bold text-ink-soft">
          {counts.geldi} geldi · {counts.gelmedi} gelmedi · {counts.izinli} izinli
          {kompakt && (
            <span className="ml-3 font-normal">
              <KayitDurumu session={session} />
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          {!kompakt && (
            <SilButonu tur="yoklama-oturumu" id={session.id} ad={`Yoklama ${formatDate(session.tarih)}`} />
          )}
          {canWrite && (
            <button
              type="button"
              className="btn btn-primary"
              disabled={updateMut.isPending || session.entries.length === 0 || (session.kaydedildi && !degisti)}
              onClick={onSave}
            >
              {updateMut.isPending ? 'Kaydediliyor…' : session.kaydedildi ? 'Düzeltmeyi Kaydet' : 'Kaydet'}
            </button>
          )}
        </div>
      </div>

      {feedback && (
        <p className={`text-[13px] font-semibold ${feedback.tone === 'ok' ? 'text-green' : 'text-red'}`}>
          {feedback.text}
        </p>
      )}
    </section>
  );
}

function KayitDurumu({ session }: { session: SessionResponse }) {
  if (!session.kaydedildi) return <span className="badge b-gray">Henüz kaydedilmedi</span>;
  return (
    <span className="badge b-green" title={session.kaydedildiTarihi ? formatDateTime(session.kaydedildiTarihi) : undefined}>
      Kaydedildi{session.kaydeden ? ` · ${session.kaydeden}` : ''}
    </span>
  );
}

const RENK: Record<'green' | 'red' | 'amber', string> = {
  green: 'border-green bg-green-soft text-green',
  red: 'border-red bg-red-soft text-red',
  amber: 'border-amber bg-amber-soft text-amber',
};

function DurumDugmesi({
  aktif,
  renk,
  disabled,
  onClick,
  children,
}: {
  aktif: boolean;
  renk: 'green' | 'red' | 'amber';
  disabled: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={aktif}
      disabled={disabled}
      onClick={onClick}
      className={`rounded-full border-[1.5px] px-3 py-1 text-[12.5px] font-bold transition ${
        aktif ? RENK[renk] : 'border-line bg-card text-ink-soft'
      } disabled:cursor-not-allowed disabled:opacity-70`}
    >
      {children}
    </button>
  );
}
