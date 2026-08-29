import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { ApiException } from '../../api/client';
import {
  getAttendanceReport,
  indirDevamsizlikCsv,
  type AttendanceReportRow,
} from '../../api/reports';

const inputClass =
  'rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none';

/** Bu ayın başı — varsayılan aralık; yönetici en çok içinde bulunduğu aya bakar. */
function ayBasi(): string {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10);
}

function bugun(): string {
  return new Date().toISOString().slice(0, 10);
}

/** Katılım oranına göre renk — düşük oran göze çarpsın, tabloyu taramak zorunda kalmayın. */
function oranSinifi(oran: number): string {
  if (oran >= 80) return 'b-green';
  if (oran >= 50) return 'b-amber';
  return 'b-red';
}

/**
 * Devamsızlık raporu sekmesi.
 *
 * Satırlar backend'den katılım oranı ARTAN sırada gelir — yöneticinin görmek istediği önce
 * "en çok devamsızlık yapan" öğrencidir; alfabetik sıralama bu raporu işe yaramaz hale getirirdi.
 */
export default function AttendanceReportTab() {
  const [baslangic, setBaslangic] = useState(ayBasi());
  const [bitis, setBitis] = useState(bugun());
  const [indiriliyor, setIndiriliyor] = useState(false);

  const q = useQuery({
    queryKey: ['reports', 'attendance', baslangic, bitis],
    queryFn: () => getAttendanceReport({ baslangic, bitis }),
    placeholderData: keepPreviousData,
  });

  const csvIndir = async () => {
    setIndiriliyor(true);
    try {
      const { blob, dosyaAdi } = await indirDevamsizlikCsv({ baslangic, bitis });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = dosyaAdi;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } finally {
      setIndiriliyor(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="card flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-wrap items-end gap-3">
          <label className="block text-[13px]">
            <span className="mb-1 block text-ink-soft">Başlangıç</span>
            <input
              type="date"
              className={inputClass}
              value={baslangic}
              onChange={(e) => setBaslangic(e.target.value)}
            />
          </label>
          <label className="block text-[13px]">
            <span className="mb-1 block text-ink-soft">Bitiş</span>
            <input
              type="date"
              className={inputClass}
              value={bitis}
              onChange={(e) => setBitis(e.target.value)}
            />
          </label>
        </div>
        <button
          type="button"
          className="btn btn-ghost"
          disabled={indiriliyor || !q.data || q.data.satirlar.length === 0}
          onClick={csvIndir}
        >
          {indiriliyor ? 'Hazırlanıyor…' : 'Excel için indir (.csv)'}
        </button>
      </div>

      {q.isLoading ? (
        <div className="card text-center text-ink-soft">Yükleniyor…</div>
      ) : q.isError ? (
        <div className="card text-center text-red">
          {q.error instanceof ApiException ? q.error.message : 'Rapor yüklenemedi'}
        </div>
      ) : !q.data || q.data.satirlar.length === 0 ? (
        <div className="card py-8 text-center text-[13.5px] text-ink-soft">
          Bu tarih aralığında yoklama kaydı yok. Yoklama alındıkça rapor dolmaya başlar.
        </div>
      ) : (
        <div className="card">
          <p className="mb-3 text-[12.5px] text-ink-soft">
            Seçilen aralıkta <b>{q.data.toplamOturum}</b> ders yapıldı. Liste, katılımı en düşük
            öğrenciden başlar.
          </p>
          <div className="overflow-x-auto">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Öğrenci</th>
                  <th className="t-right">Toplam Ders</th>
                  <th className="t-right">Geldi</th>
                  <th className="t-right">Gelmedi</th>
                  <th className="t-right">İzinli</th>
                  <th className="t-right">Katılım</th>
                </tr>
              </thead>
              <tbody>
                {q.data.satirlar.map((r) => (
                  <Satir key={r.ogrenciId} r={r} />
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

function Satir({ r }: { r: AttendanceReportRow }) {
  const oran = typeof r.katilimOrani === 'string' ? Number(r.katilimOrani) : r.katilimOrani;
  return (
    <tr>
      <td>
        <b>{r.ogrenciAdSoyad}</b>
      </td>
      <td className="t-right text-ink-soft">{r.toplamDers}</td>
      <td className="t-right">{r.geldi}</td>
      <td className="t-right">{r.gelmedi}</td>
      <td className="t-right text-ink-soft">{r.izinli}</td>
      <td className="t-right">
        <span className={`badge ${oranSinifi(oran)}`}>%{oran.toFixed(0)}</span>
      </td>
    </tr>
  );
}
