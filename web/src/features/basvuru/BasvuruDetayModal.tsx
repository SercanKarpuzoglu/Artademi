import type { ReactNode } from 'react';
import type { BasvuruDurumu, BasvuruResponse } from '../../api/types';
import { formatDateTime } from '../../lib/format';

const DURUM_ETIKET: Record<BasvuruDurumu, string> = {
  YENI: 'Yeni',
  ARANDI: 'Arandı',
  OGRENCIYE_DONUSTU: 'Öğrenci Oldu',
  OLUMSUZ: 'Olumsuz',
};

const DURUM_BADGE: Record<BasvuruDurumu, string> = {
  YENI: 'b-amber',
  ARANDI: 'b-blue',
  OGRENCIYE_DONUSTU: 'b-green',
  OLUMSUZ: 'b-red',
};

/**
 * Başvuru detayı.
 *
 * Listede yalnızca özet alanlar var; velinin yazdığı **mesaj**, e-posta ve veli adı
 * sadece burada görünür. Bu alanlar formda toplanıyor ama tabloya sığmıyor — mesajı
 * okumadan başvuruyu değerlendirmek mümkün değil, o yüzden detay bir modal olarak açılır.
 */
export default function BasvuruDetayModal({
  basvuru,
  islemde,
  onKapat,
  onDurum,
  onDonustur,
  onOgrenciAc,
}: {
  basvuru: BasvuruResponse;
  islemde: boolean;
  onKapat: () => void;
  onDurum: (durum: BasvuruDurumu) => void;
  onDonustur: () => void;
  onOgrenciAc: (ogrenciId: number) => void;
}) {
  const donusmus = basvuru.durum === 'OGRENCIYE_DONUSTU';

  return (
    <div
      className="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4"
      role="dialog"
      aria-modal="true"
      onClick={onKapat}
    >
      <div
        className="card max-h-[90vh] w-full max-w-lg overflow-y-auto"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-start justify-between gap-3">
          <div>
            <h3 className="text-[17px] font-semibold">
              {basvuru.ad} {basvuru.soyad}
            </h3>
            <div className="mt-1 text-[12.5px] text-ink-soft">
              {formatDateTime(basvuru.olusturulmaTarihi)} tarihinde başvurdu
            </div>
          </div>
          <span className={`badge ${DURUM_BADGE[basvuru.durum]}`}>
            {DURUM_ETIKET[basvuru.durum]}
          </span>
        </div>

        <div className="space-y-3">
          <Satir etiket="Telefon">
            <a href={`tel:${basvuru.telefon}`} className="text-rasp">
              {basvuru.telefon}
            </a>
          </Satir>

          <Satir etiket="E-posta">
            {basvuru.email ? (
              <a href={`mailto:${basvuru.email}`} className="text-rasp">
                {basvuru.email}
              </a>
            ) : (
              <span className="text-ink-soft">—</span>
            )}
          </Satir>

          <Satir etiket="Veli Adı">
            {basvuru.veliAdi ?? <span className="text-ink-soft">—</span>}
          </Satir>

          <Satir etiket="İlgilendiği Branş">
            {basvuru.bransAdi ?? <span className="text-ink-soft">Belirtilmedi</span>}
          </Satir>
        </div>

        <div className="mt-4">
          <div className="mb-1 text-[12.5px] font-semibold text-ink-soft">Mesajı</div>
          {basvuru.mesaj ? (
            <p className="whitespace-pre-wrap rounded-[10px] border border-line bg-bg px-3 py-2 text-[13.5px] leading-relaxed">
              {basvuru.mesaj}
            </p>
          ) : (
            <p className="text-[13px] text-ink-soft">Mesaj bırakılmamış.</p>
          )}
        </div>

        <div className="mt-5 flex flex-wrap justify-end gap-2">
          <button type="button" className="btn btn-ghost" onClick={onKapat}>
            Kapat
          </button>

          {donusmus ? (
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => basvuru.ogrenciId && onOgrenciAc(basvuru.ogrenciId)}
            >
              Öğrenciyi aç
            </button>
          ) : (
            <>
              {basvuru.durum === 'YENI' && (
                <button
                  type="button"
                  className="btn btn-ghost"
                  disabled={islemde}
                  onClick={() => onDurum('ARANDI')}
                >
                  Arandı olarak işaretle
                </button>
              )}
              {basvuru.durum !== 'OLUMSUZ' && (
                <button
                  type="button"
                  className="btn btn-ghost"
                  disabled={islemde}
                  onClick={() => onDurum('OLUMSUZ')}
                >
                  Olumsuz
                </button>
              )}
              <button type="button" className="btn btn-primary" onClick={onDonustur}>
                Öğrenciye dönüştür
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function Satir({ etiket, children }: { etiket: string; children: ReactNode }) {
  return (
    <div className="flex gap-3 text-[13.5px]">
      <span className="w-36 shrink-0 text-ink-soft">{etiket}</span>
      <span className="min-w-0 break-words">{children}</span>
    </div>
  );
}
