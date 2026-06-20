import { ApiException } from '../../api/client';
import { formatDate, formatMoney } from '../../lib/format';
import { YONTEM_LABEL } from './financeDisplay';
import { useStudentBalance, useStudentFinance } from './useFinance';

/**
 * Öğrenci detayinda finans karti. YALNIZCA ADMIN / FRONTDESK_ACCOUNTING gostermeli (cagiran gating yapar).
 * Bakiye > 0 (borç) -> kirmizi; <= 0 -> nötr.
 */
export default function StudentFinanceCard({ studentId }: { studentId: number }) {
  const balanceQuery = useStudentBalance(studentId);
  const financeQuery = useStudentFinance(studentId);

  const bakiyeNum = (() => {
    const b = balanceQuery.data?.bakiye;
    if (b === undefined || b === null) return 0;
    return typeof b === 'number' ? b : Number(String(b).replace(',', '.'));
  })();
  const borc = bakiyeNum > 0;

  const sonTahakkuklar = (financeQuery.data?.tahakkuklar ?? []).slice(0, 3);
  const sonOdemeler = (financeQuery.data?.odemeler ?? []).slice(0, 3);

  return (
    <section className="card space-y-3">
      <h3>Finans</h3>

      {balanceQuery.isLoading ? (
        <p className="text-sm text-ink-soft">Yükleniyor…</p>
      ) : balanceQuery.isError ? (
        <p className="text-sm text-red">
          {balanceQuery.error instanceof ApiException
            ? balanceQuery.error.message
            : 'Bakiye yüklenemedi'}
        </p>
      ) : balanceQuery.data ? (
        <dl className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-3">
          <div>
            <dt className="text-xs text-gray-500">Toplam Tahakkuk</dt>
            <dd className="text-sm text-gray-800">
              <span className="amount">{formatMoney(balanceQuery.data.toplamTahakkuk)} ₺</span>
            </dd>
          </div>
          <div>
            <dt className="text-xs text-gray-500">Toplam Ödeme</dt>
            <dd className="text-sm text-gray-800">
              <span className="amount">{formatMoney(balanceQuery.data.toplamOdeme)} ₺</span>
            </dd>
          </div>
          <div>
            <dt className="text-xs text-gray-500">Bakiye</dt>
            <dd className={borc ? 'text-sm font-semibold text-red' : 'text-sm text-ink-soft'}>
              <span className="amount">{formatMoney(balanceQuery.data.bakiye)} ₺</span>
            </dd>
          </div>
        </dl>
      ) : null}

      {(sonTahakkuklar.length > 0 || sonOdemeler.length > 0) && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {sonTahakkuklar.length > 0 && (
            <div>
              <h4 className="mb-1 text-xs font-semibold text-gray-500">Son Tahakkuklar</h4>
              <ul className="divide-y divide-gray-100">
                {sonTahakkuklar.map((a) => (
                  <li key={a.id} className="flex items-center justify-between py-1.5 text-[13px]">
                    <span className="text-ink-soft">{a.donem ?? '—'}</span>
                    <span className="amount">{formatMoney(a.tutar)} ₺</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
          {sonOdemeler.length > 0 && (
            <div>
              <h4 className="mb-1 text-xs font-semibold text-gray-500">Son Ödemeler</h4>
              <ul className="divide-y divide-gray-100">
                {sonOdemeler.map((p) => (
                  <li key={p.id} className="flex items-center justify-between py-1.5 text-[13px]">
                    <span className="text-ink-soft">
                      {formatDate(p.odemeTarihi)} · {YONTEM_LABEL[p.odemeYontemi]}
                    </span>
                    <span className="amount">{formatMoney(p.tutar)} ₺</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </section>
  );
}
