import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { getBildirimAyari, updateBildirimAyari } from '../../api/bildirim';
import { ApiException } from '../../api/client';
import type { BildirimAyari } from '../../api/types';

const GUNLER = [
  { value: 1, label: 'Pazartesi' },
  { value: 2, label: 'Salı' },
  { value: 3, label: 'Çarşamba' },
  { value: 4, label: 'Perşembe' },
  { value: 5, label: 'Cuma' },
  { value: 6, label: 'Cumartesi' },
  { value: 7, label: 'Pazar' },
];

/**
 * Otomatik bildirim tercihleri (ADMIN).
 *
 * Hepsi varsayılan KAPALIDIR ve bu bilinçlidir: bu ayarlar kurumun VELİLERİNE otomatik mail
 * gönderilmesini başlatır. Kurum açıkça açmadıkça hiçbir otomatik gönderim olmaz.
 */
export default function BildirimAyarlariPage() {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['bildirim-ayari'], queryFn: getBildirimAyari });
  const [form, setForm] = useState<BildirimAyari | null>(null);
  const [hata, setHata] = useState<string | null>(null);
  const [kaydedildi, setKaydedildi] = useState(false);

  useEffect(() => {
    if (q.data) setForm(q.data);
  }, [q.data]);

  const mutation = useMutation({
    mutationFn: updateBildirimAyari,
    onSuccess: (yeni) => {
      setForm(yeni);
      setKaydedildi(true);
      setTimeout(() => setKaydedildi(false), 2500);
      qc.invalidateQueries({ queryKey: ['bildirim-ayari'] });
    },
    onError: (e) => setHata(e instanceof ApiException ? e.message : 'Ayarlar kaydedilemedi'),
  });

  const degistir = (alan: keyof BildirimAyari, deger: boolean | number) => {
    if (!form) return;
    setForm({ ...form, [alan]: deger });
    setKaydedildi(false);
  };

  return (
    <div>
      <div className="topbar">
        <div>
          <h1>Bildirim Ayarları</h1>
          <div className="sub">Otomatik e-posta gönderimlerini buradan yönetin</div>
        </div>
        <button
          type="button"
          className="btn btn-primary"
          disabled={!form || mutation.isPending}
          onClick={() => {
            setHata(null);
            if (form) mutation.mutate(form);
          }}
        >
          {mutation.isPending ? 'Kaydediliyor…' : kaydedildi ? 'Kaydedildi ✓' : 'Kaydet'}
        </button>
      </div>

      {hata && <div className="card mb-4 border-red/40 bg-red/10 text-[13px] text-red">{hata}</div>}

      {q.isLoading || !form ? (
        <div className="card py-8 text-center text-ink-soft">Yükleniyor…</div>
      ) : (
        <div className="space-y-4">
          <div className="card">
            <p className="text-[12.5px] text-ink-soft">
              Bu ayarlar <b>kapalıyken</b> hiçbir otomatik e-posta gönderilmez. Borç hatırlatmasını
              elle göndermeye devam edebilirsiniz.
            </p>
          </div>

          <Secenek
            baslik="Otomatik borç hatırlatma"
            aciklama="Borcu olan öğrencilerin velilerine her sabah otomatik hatırlatma gönderilir. Aynı veliye 7 günde birden fazla mail gitmez ve günde en fazla 50 gönderim yapılır."
            acik={form.borcHatirlatmaOtomatik}
            onDegis={(v) => degistir('borcHatirlatmaOtomatik', v)}
          />

          <Secenek
            baslik="Devamsızlık bildirimi"
            aciklama="Öğrenci derse gelmediğinde velisine aynı akşam (20:00) bilgi maili gider. Aynı ders için ikinci kez gönderilmez."
            acik={form.devamsizlikBildirimi}
            onDegis={(v) => degistir('devamsizlikBildirimi', v)}
          />

          <Secenek
            baslik="Haftalık finansal özet"
            aciklama="Kurum yöneticilerine haftada bir, içinde bulunulan ayın gelir/gider özeti gönderilir."
            acik={form.haftalikOzet}
            onDegis={(v) => degistir('haftalikOzet', v)}
          >
            <label className="mt-3 flex items-center gap-2 text-[13px]">
              <span className="text-ink-soft">Gönderim günü</span>
              <select
                className="rounded-[10px] border border-line bg-card px-3 py-1.5 text-[13.5px] focus:border-rasp focus:outline-none"
                value={form.haftalikOzetGunu}
                onChange={(e) => degistir('haftalikOzetGunu', Number(e.target.value))}
                disabled={!form.haftalikOzet}
              >
                {GUNLER.map((g) => (
                  <option key={g.value} value={g.value}>
                    {g.label}
                  </option>
                ))}
              </select>
            </label>
          </Secenek>

          <div className="card">
            <p className="text-[12.5px] text-ink-soft">
              Mailler kurumunuzun adıyla gönderilir; veli sizi tanır. Gönderim hızı bilinçli olarak
              sınırlıdır — toplu mail, adresin spam'e düşmesine yol açar.
            </p>
          </div>
        </div>
      )}
    </div>
  );
}

function Secenek({
  baslik,
  aciklama,
  acik,
  onDegis,
  children,
}: {
  baslik: string;
  aciklama: string;
  acik: boolean;
  onDegis: (v: boolean) => void;
  children?: React.ReactNode;
}) {
  return (
    <div className="card">
      <label className="flex cursor-pointer items-start gap-3">
        <input
          type="checkbox"
          className="mt-1"
          checked={acik}
          onChange={(e) => onDegis(e.target.checked)}
        />
        <span className="min-w-0">
          <span className="block text-[14.5px] font-semibold">{baslik}</span>
          <span className="mt-1 block text-[12.5px] leading-relaxed text-ink-soft">{aciklama}</span>
        </span>
      </label>
      {children}
    </div>
  );
}
