import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ApiException } from '../../api/client';
import {
  getBorcluAdaylar,
  hatirlatmaGonder,
  type BorcluAday,
  type HatirlatmaSonucu,
} from '../../api/reminders';

function para(v: string | number): string {
  const n = typeof v === 'string' ? Number(v) : v;
  return new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(n);
}

/**
 * Borçlu veli hatırlatması (ADMIN + FRONTDESK_ACCOUNTING).
 *
 * Gönderim bilinçli olarak ELLE tetiklenir: hangi veliye ne zaman yazılacağına okul karar
 * vermeli. Sistem yalnızca listeyi hazırlar, kimin neden gönderilemeyeceğini açıkça söyler
 * ve aynı veliye üst üste mail gitmesini engeller.
 */
export default function BorcHatirlatmaPage() {
  const qc = useQueryClient();
  const [secili, setSecili] = useState<Set<number>>(new Set());
  const [sonuc, setSonuc] = useState<HatirlatmaSonucu | null>(null);
  const [hata, setHata] = useState<string | null>(null);

  const q = useQuery({ queryKey: ['reminders', 'candidates'], queryFn: getBorcluAdaylar });

  const gonderim = useMutation({
    mutationFn: hatirlatmaGonder,
    onSuccess: (r) => {
      setSonuc(r);
      setSecili(new Set());
      qc.invalidateQueries({ queryKey: ['reminders'] });
    },
    onError: (e) =>
      setHata(e instanceof ApiException ? e.message : 'Hatırlatmalar gönderilemedi'),
  });

  const adaylar = q.data ?? [];
  const gonderilebilirler = adaylar.filter((a) => a.gonderilebilir);
  const hepsiSecili = gonderilebilirler.length > 0 && secili.size === gonderilebilirler.length;

  const degistir = (id: number) => {
    setSecili((mevcut) => {
      const yeni = new Set(mevcut);
      if (yeni.has(id)) yeni.delete(id);
      else yeni.add(id);
      return yeni;
    });
  };

  const tumunuSec = () => {
    setSecili(hepsiSecili ? new Set() : new Set(gonderilebilirler.map((a) => a.ogrenciId)));
  };

  return (
    <div>
      <div className="topbar">
        <div>
          <h1>Borç Hatırlatma</h1>
          <div className="sub">Borcu olan öğrencilerin velilerine ödeme hatırlatması</div>
        </div>
        <button
          type="button"
          className="btn btn-primary"
          disabled={secili.size === 0 || gonderim.isPending}
          onClick={() => {
            setHata(null);
            setSonuc(null);
            gonderim.mutate([...secili]);
          }}
        >
          {gonderim.isPending ? 'Gönderiliyor…' : `Seçilenlere gönder (${secili.size})`}
        </button>
      </div>

      {hata && (
        <div className="card mb-4 border-red/40 bg-red/10 text-[13px] text-red">{hata}</div>
      )}

      {sonuc && (
        <div className="card mb-4">
          <p className="mb-2 text-[13.5px]">
            <b>{sonuc.gonderilen}</b> hatırlatma gönderildi
            {sonuc.atlanan > 0 && `, ${sonuc.atlanan} tanesi atlandı`}.
          </p>
          <ul className="space-y-1 text-[12.5px]">
            {sonuc.satirlar.map((s) => (
              <li key={s.ogrenciId} className={s.gonderildi ? 'text-green' : 'text-ink-soft'}>
                {s.gonderildi ? '✓' : '—'} {s.adSoyad}: {s.aciklama}
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="card">
        <p className="mb-3 text-[12.5px] text-ink-soft">
          Aynı veliye 7 gün içinde ikinci kez hatırlatma gönderilmez. Mail, kurumunuzun adıyla
          iletilir; veli doğrudan size dönebilir.
        </p>

        {q.isLoading ? (
          <p className="py-8 text-center text-ink-soft">Yükleniyor…</p>
        ) : q.isError ? (
          <p className="py-8 text-center text-red">
            {q.error instanceof ApiException ? q.error.message : 'Liste yüklenemedi'}
          </p>
        ) : adaylar.length === 0 ? (
          <p className="py-8 text-center text-[13.5px] text-ink-soft">
            Borcu olan öğrenci yok — tahsilatlarınız güncel.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="data-table">
              <thead>
                <tr>
                  <th style={{ width: 40 }}>
                    <input
                      type="checkbox"
                      checked={hepsiSecili}
                      onChange={tumunuSec}
                      disabled={gonderilebilirler.length === 0}
                      aria-label="Tümünü seç"
                    />
                  </th>
                  <th>Öğrenci</th>
                  <th className="t-right">Bakiye</th>
                  <th>Veli E-posta</th>
                  <th>Durum</th>
                </tr>
              </thead>
              <tbody>
                {adaylar.map((a) => (
                  <Satir
                    key={a.ogrenciId}
                    a={a}
                    secili={secili.has(a.ogrenciId)}
                    onDegistir={() => degistir(a.ogrenciId)}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

function Satir({
  a,
  secili,
  onDegistir,
}: {
  a: BorcluAday;
  secili: boolean;
  onDegistir: () => void;
}) {
  return (
    <tr className={a.gonderilebilir ? '' : 'opacity-60'}>
      <td>
        <input
          type="checkbox"
          checked={secili}
          onChange={onDegistir}
          disabled={!a.gonderilebilir}
          aria-label={`${a.adSoyad} seç`}
        />
      </td>
      <td>
        <b>{a.adSoyad}</b>
      </td>
      <td className="t-right font-semibold text-red">{para(a.bakiye)}</td>
      <td className="text-ink-soft">{a.veliMail ?? '—'}</td>
      <td>
        {a.gonderilebilir ? (
          <span className="badge b-green">Gönderilebilir</span>
        ) : (
          <span className="text-[12px] text-ink-soft">{a.engelSebebi}</span>
        )}
      </td>
    </tr>
  );
}
