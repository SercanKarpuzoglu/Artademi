import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { ApiException } from '../../api/client';
import { getTeacherQuality } from '../../api/reports';
import type { TeacherQualityRow } from '../../api/types';
import { RENK, YatayCubuk } from './charts';

const inputClass =
  'rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none';

function ayBasi(): string {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10);
}
function bugun(): string {
  return new Date().toISOString().slice(0, 10);
}
function oranSinifi(oran: number): string {
  if (oran >= 80) return 'b-green';
  if (oran >= 50) return 'b-amber';
  return 'b-red';
}

/**
 * Eğitmen kalitesi paneli (Dalga F, YALNIZ ADMIN): yük (grup, öğrenci, haftalık saat) ve tarih aralığında
 * planlanan derse karşı alınan yoklama, kaydedilmemiş oturum, öğrencilerinin katılım oranı.
 * Satırlar katılım oranı ARTAN gelir — önce dikkat gerektiren eğitmen.
 */
export default function TeacherQualityTab() {
  const [baslangic, setBaslangic] = useState(ayBasi());
  const [bitis, setBitis] = useState(bugun());
  const q = useQuery({
    queryKey: ['reports', 'teacher-quality', baslangic, bitis],
    queryFn: () => getTeacherQuality({ baslangic, bitis }),
  });
  const satirlar = q.data?.satirlar ?? [];
  const ad = (r: TeacherQualityRow) => `${r.ad} ${r.soyad}`;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3">
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-gray-700">Başlangıç</span>
          <input type="date" className={inputClass} value={baslangic} onChange={(e) => setBaslangic(e.target.value)} />
        </label>
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-gray-700">Bitiş</span>
          <input type="date" className={inputClass} value={bitis} onChange={(e) => setBitis(e.target.value)} />
        </label>
      </div>

      {q.isLoading ? (
        <div className="card text-center text-ink-soft">Yükleniyor…</div>
      ) : q.isError ? (
        <div className="card text-center text-red">{q.error instanceof ApiException ? q.error.message : 'Bir hata oluştu'}</div>
      ) : satirlar.length === 0 ? (
        <div className="card text-center text-ink-soft">Aktif eğitmen yok</div>
      ) : (
        <>
          <div className="grid gap-4 lg:grid-cols-2">
            <div className="card space-y-2">
              <h3>Öğrenci devamlılığı (%)</h3>
              <p className="text-[12.5px] text-ink-soft">Eğitmenin gruplarında GELDİ / (GELDİ + GELMEDİ)</p>
              <YatayCubuk
                data={satirlar.map((r) => ({ name: ad(r), oran: Number(r.katilimOrani) }))}
                seriler={[{ key: 'oran', name: 'Katılım %', renk: RENK.green }]}
                format={(v) => `%${v}`}
              />
            </div>
            <div className="card space-y-2">
              <h3>Eğitmen yükü (haftalık ders saati)</h3>
              <p className="text-[12.5px] text-ink-soft">Aktif gruplarının program toplamı</p>
              <YatayCubuk
                data={satirlar.map((r) => ({ name: ad(r), saat: Number(r.haftalikDersSaati) }))}
                seriler={[{ key: 'saat', name: 'Saat / hafta', renk: RENK.blue }]}
                format={(v) => `${v} sa`}
              />
            </div>
          </div>

          <div className="card">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Eğitmen</th>
                  <th className="t-right">Grup</th>
                  <th className="t-right">Öğrenci</th>
                  <th className="t-right">Saat/hafta</th>
                  <th className="t-right">Planlanan ders</th>
                  <th className="t-right">Alınan yoklama</th>
                  <th className="t-right">Alınmayan</th>
                  <th className="t-right">Kaydedilmemiş</th>
                  <th>Katılım</th>
                </tr>
              </thead>
              <tbody>
                {satirlar.map((r) => (
                  <tr key={r.ogretmenId}>
                    <td>
                      <b>{ad(r)}</b>
                    </td>
                    <td className="t-right">{r.aktifGrup}</td>
                    <td className="t-right">{r.ogrenciSayisi}</td>
                    <td className="t-right">{Number(r.haftalikDersSaati)}</td>
                    <td className="t-right">{r.planlananDers}</td>
                    <td className="t-right">{r.oturumSayisi}</td>
                    <td className="t-right">
                      {r.alinmayanYoklama > 0 ? <span className="badge b-red">{r.alinmayanYoklama}</span> : <span className="text-ink-soft">0</span>}
                    </td>
                    <td className="t-right">
                      {r.kaydedilmemisOturum > 0 ? <span className="badge b-amber">{r.kaydedilmemisOturum}</span> : <span className="text-ink-soft">0</span>}
                    </td>
                    <td>
                      <span className={`badge ${oranSinifi(Number(r.katilimOrani))}`}>%{Number(r.katilimOrani)}</span>
                      <span className="ml-2 text-[12px] text-ink-soft">{r.geldi} geldi · {r.gelmedi} gelmedi · {r.izinli} izinli</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
