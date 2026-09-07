import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { getSessions } from '../../api/attendance';
import { ApiException } from '../../api/client';
import {
  getTelafiAdaylari,
  getTelafiler,
  telafiIptal,
  telafiKullan,
  telafiVer,
} from '../../api/telafi';
import type { TelafiAdayi, TelafiDurumu, TelafiResponse } from '../../api/types';
import { formatDate } from '../../lib/format';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none';

const DURUM_TABS: { label: string; value: TelafiDurumu | undefined }[] = [
  { label: 'Tümü', value: undefined },
  { label: 'Bekleyen', value: 'BEKLIYOR' },
  { label: 'Kullanılan', value: 'KULLANILDI' },
  { label: 'İptal', value: 'IPTAL' },
];

const DURUM_ETIKET: Record<TelafiDurumu, string> = {
  BEKLIYOR: 'Bekliyor',
  KULLANILDI: 'Kullanıldı',
  IPTAL: 'İptal',
};

const DURUM_BADGE: Record<TelafiDurumu, string> = {
  BEKLIYOR: 'b-amber',
  KULLANILDI: 'b-green',
  IPTAL: 'b-red',
};

/**
 * Telafi ders hakları.
 *
 * ⚠️ Hak OTOMATİK doğmaz — "Telafi verilebilecek devamsızlıklar" listesi yalnızca öneridir;
 * hakkı kurum tanır. Her devamsızlıktan otomatik hak üretilseydi liste kullanılamaz hale
 * gelir ve kurumun kendi kuralı ("haber verdiyse telafi veririm") ezilirdi.
 */
export default function TelafiPage() {
  const qc = useQueryClient();
  const [durum, setDurum] = useState<TelafiDurumu | undefined>(undefined);
  const [adaylarAcik, setAdaylarAcik] = useState(false);
  const [kullanilacak, setKullanilacak] = useState<TelafiResponse | null>(null);
  const [hata, setHata] = useState<string | null>(null);

  const q = useQuery({ queryKey: ['telafi', durum], queryFn: () => getTelafiler({ durum }) });
  const adaylarQ = useQuery({
    queryKey: ['telafi', 'adaylar'],
    queryFn: getTelafiAdaylari,
    enabled: adaylarAcik,
  });

  const tazele = () => qc.invalidateQueries({ queryKey: ['telafi'] });

  const ver = useMutation({
    mutationFn: telafiVer,
    onSuccess: tazele,
    onError: (e) => setHata(e instanceof ApiException ? e.message : 'Telafi hakkı verilemedi'),
  });

  const iptal = useMutation({
    mutationFn: telafiIptal,
    onSuccess: tazele,
    onError: (e) => setHata(e instanceof ApiException ? e.message : 'İptal edilemedi'),
  });

  const liste = q.data ?? [];

  return (
    <div>
      <div className="topbar">
        <div>
          <h1>Telafi Dersleri</h1>
          <div className="sub">Kaçırılan derslerin telafi hakları</div>
        </div>
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => setAdaylarAcik((v) => !v)}
        >
          {adaylarAcik ? 'Kapat' : 'Devamsızlıklardan Hak Ver'}
        </button>
      </div>

      {hata && <div className="card mb-4 border-red/40 bg-red/10 text-[13px] text-red">{hata}</div>}

      {adaylarAcik && (
        <AdayListesi
          adaylar={adaylarQ.data ?? []}
          yukleniyor={adaylarQ.isLoading}
          islemde={ver.isPending}
          onVer={(a) =>
            ver.mutate(
              { ogrenciId: a.ogrenciId, kaynakOturumId: a.oturumId },
              { onSuccess: () => adaylarQ.refetch() },
            )
          }
        />
      )}

      <div className="tabs mb-4">
        {DURUM_TABS.map((t) => (
          <button
            key={t.label}
            type="button"
            className={`tab ${durum === t.value ? 'active' : ''}`}
            onClick={() => setDurum(t.value)}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className="card">
        {q.isLoading ? (
          <p className="py-8 text-center text-ink-soft">Yükleniyor…</p>
        ) : liste.length === 0 ? (
          <p className="py-8 text-center text-[13.5px] text-ink-soft">
            Bu filtrede telafi hakkı yok.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Öğrenci</th>
                  <th>Kaynak Ders</th>
                  <th>Verilme</th>
                  <th>Son Kullanma</th>
                  <th>Durum</th>
                  <th className="t-right">İşlem</th>
                </tr>
              </thead>
              <tbody>
                {liste.map((t) => (
                  <tr key={t.id} className={t.durum === 'IPTAL' ? 'opacity-60' : ''}>
                    <td>
                      <b>{t.ogrenciAdSoyad}</b>
                      {t.aciklama && (
                        <div className="text-[12px] text-ink-soft">{t.aciklama}</div>
                      )}
                    </td>
                    <td className="text-ink-soft">
                      {t.kaynakDers ? (
                        <>
                          {formatDate(t.kaynakDers)}
                          {t.kaynakGrup && <div className="text-[12px]">{t.kaynakGrup}</div>}
                        </>
                      ) : (
                        <span className="text-[12px]">Elle tanımlandı</span>
                      )}
                    </td>
                    <td className="text-ink-soft">{formatDate(t.verilmeTarihi)}</td>
                    <td className="text-ink-soft">
                      {t.sonKullanmaTarihi ? formatDate(t.sonKullanmaTarihi) : 'Süresiz'}
                    </td>
                    <td>
                      <span className={`badge ${DURUM_BADGE[t.durum]}`}>
                        {DURUM_ETIKET[t.durum]}
                      </span>
                      {t.suresiDoldu && (
                        <div className="mt-1 text-[12px] text-red">Süresi doldu</div>
                      )}
                      {t.durum === 'KULLANILDI' && t.kullanimTarihi && (
                        <div className="mt-1 text-[12px] text-ink-soft">
                          {formatDate(t.kullanimTarihi)}
                        </div>
                      )}
                    </td>
                    <td className="t-right">
                      {t.durum === 'BEKLIYOR' && (
                        <div className="flex justify-end gap-2">
                          <button
                            type="button"
                            className="btn btn-primary"
                            disabled={t.suresiDoldu}
                            title={t.suresiDoldu ? 'Süresi dolmuş hak kullanılamaz' : undefined}
                            onClick={() => setKullanilacak(t)}
                          >
                            Kullanıldı
                          </button>
                          <button
                            type="button"
                            className="btn btn-ghost"
                            disabled={iptal.isPending}
                            onClick={() => iptal.mutate(t.id)}
                          >
                            İptal
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {kullanilacak && (
        <KullanModal
          hak={kullanilacak}
          onKapat={() => setKullanilacak(null)}
          onTamam={() => {
            setKullanilacak(null);
            tazele();
          }}
          onHata={setHata}
        />
      )}
    </div>
  );
}

function AdayListesi({
  adaylar,
  yukleniyor,
  islemde,
  onVer,
}: {
  adaylar: TelafiAdayi[];
  yukleniyor: boolean;
  islemde: boolean;
  onVer: (a: TelafiAdayi) => void;
}) {
  return (
    <div className="card mb-4">
      <h2 className="mb-1 text-[15px] font-semibold">Telafi verilebilecek devamsızlıklar</h2>
      <p className="mb-3 text-[12.5px] text-ink-soft">
        Son 60 günün devamsızlıkları. Hak <b>otomatik verilmez</b> — hangisine telafi
        tanıyacağınıza siz karar verirsiniz. Hak verilen devamsızlık bu listeden çıkar.
      </p>

      {yukleniyor ? (
        <p className="py-4 text-center text-ink-soft">Yükleniyor…</p>
      ) : adaylar.length === 0 ? (
        <p className="py-4 text-center text-[13px] text-ink-soft">
          Hak verilmemiş devamsızlık yok.
        </p>
      ) : (
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead>
              <tr>
                <th>Öğrenci</th>
                <th>Ders</th>
                <th>Tarih</th>
                <th className="t-right">İşlem</th>
              </tr>
            </thead>
            <tbody>
              {adaylar.map((a) => (
                <tr key={`${a.ogrenciId}-${a.oturumId}`}>
                  <td>
                    <b>{a.ogrenciAdSoyad}</b>
                  </td>
                  <td className="text-ink-soft">{a.grupAdi ?? '—'}</td>
                  <td className="text-ink-soft">{formatDate(a.tarih)}</td>
                  <td className="t-right">
                    <button
                      type="button"
                      className="btn btn-ghost"
                      disabled={islemde}
                      onClick={() => onVer(a)}
                    >
                      Telafi Hakkı Ver
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function KullanModal({
  hak,
  onKapat,
  onTamam,
  onHata,
}: {
  hak: TelafiResponse;
  onKapat: () => void;
  onTamam: () => void;
  onHata: (m: string) => void;
}) {
  const [oturumId, setOturumId] = useState('');

  // Ders listesi backend'de tarihe göre YENİDEN ESKİYE sıralı gelir; ilk 100 kayıt
  // pratikte son dersleri kapsar. (Uç tarih aralığı desteklemiyor, tek tarih alıyor.)
  const q = useQuery({
    queryKey: ['telafi', 'dersler'],
    queryFn: () => getSessions({ size: 100 }),
  });

  const m = useMutation({
    mutationFn: () => telafiKullan(hak.id, Number(oturumId)),
    onSuccess: onTamam,
    onError: (e) => onHata(e instanceof ApiException ? e.message : 'İşaretlenemedi'),
  });

  const dersler = q.data?.data ?? [];

  return (
    <div
      className="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4"
      role="dialog"
      aria-modal="true"
      onClick={onKapat}
    >
      <div className="card w-full max-w-md" onClick={(e) => e.stopPropagation()}>
        <h3 className="mb-1 text-[16px] font-semibold">Telafi Kullanıldı</h3>
        <p className="mb-4 text-[12.5px] text-ink-soft">
          <b>{hak.ogrenciAdSoyad}</b> telafisini hangi derste yaptı? Ders, kanıt olarak
          kayda geçer.
        </p>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            m.mutate();
          }}
          className="space-y-3"
        >
          <label className="block text-[13px]">
            <span className="mb-1 block text-ink-soft">Ders *</span>
            <select
              className={inputClass}
              value={oturumId}
              onChange={(e) => setOturumId(e.target.value)}
              required
            >
              <option value="">Seçiniz</option>
              {dersler.map((d) => (
                <option key={d.id} value={d.id}>
                  {formatDate(d.tarih)} — {d.grup?.ad ?? 'Ders'}
                </option>
              ))}
            </select>
            {dersler.length === 0 && !q.isLoading && (
              <span className="mt-1 block text-[12px] text-ink-soft">
                Henüz ders kaydı yok. Önce yoklama oturumu açın.
              </span>
            )}
          </label>

          <div className="flex justify-end gap-2">
            <button type="button" className="btn btn-ghost" onClick={onKapat}>
              Vazgeç
            </button>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={m.isPending || !oturumId}
            >
              {m.isPending ? 'Kaydediliyor…' : 'Kullanıldı Olarak İşaretle'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
