import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiException } from '../../api/client';
import type { BasvuruDurumu, BasvuruResponse } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Role } from '../../auth/roles';
import { formatDateTime } from '../../lib/format';
import BaglantiKarti from './BaglantiKarti';
import BasvuruDetayModal from './BasvuruDetayModal';
import DonusturModal from './DonusturModal';
import { useBasvurular, useDurumGuncelle } from './useBasvurular';

const PAGE_SIZE = 20;

const DURUM_TABS: { label: string; value: BasvuruDurumu | undefined }[] = [
  { label: 'Tümü', value: undefined },
  { label: 'Yeni', value: 'YENI' },
  { label: 'Arandı', value: 'ARANDI' },
  { label: 'Öğrenci Oldu', value: 'OGRENCIYE_DONUSTU' },
  { label: 'Olumsuz', value: 'OLUMSUZ' },
];

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
 * Online ön kayıt başvuruları.
 *
 * Bağlantı yönetimi (kurumun public form adresi) YALNIZCA ADMIN'e gösterilir: bu adres
 * kurumun dışarıya açılan yüzüdür, ön büro değiştirmemelidir.
 */
export default function BasvuruListPage() {
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const [durum, setDurum] = useState<BasvuruDurumu | undefined>(undefined);
  const [page, setPage] = useState(0);
  const [donusturulecek, setDonusturulecek] = useState<BasvuruResponse | null>(null);
  const [detay, setDetay] = useState<BasvuruResponse | null>(null);
  const [hata, setHata] = useState<string | null>(null);

  const q = useBasvurular({ durum, page, size: PAGE_SIZE });
  const durumMutation = useDurumGuncelle();

  const basvurular = q.data?.data ?? [];
  const meta = q.data?.meta ?? null;

  const durumDegistir = (id: number, yeni: BasvuruDurumu) => {
    setHata(null);
    durumMutation.mutate(
      { id, durum: yeni },
      {
        onError: (e) =>
          setHata(e instanceof ApiException ? e.message : 'Durum güncellenemedi'),
      },
    );
  };

  return (
    <div>
      <div className="topbar">
        <div>
          <h1>Ön Kayıt Başvuruları</h1>
          <div className="sub">Paylaştığınız formdan gelen talepler</div>
        </div>
      </div>

      {hasRole(Role.ADMIN) && <BaglantiKarti />}

      <div className="tabs mb-4">
        {DURUM_TABS.map((t) => (
          <button
            key={t.label}
            type="button"
            className={`tab ${durum === t.value ? 'active' : ''}`}
            onClick={() => {
              setDurum(t.value);
              setPage(0);
            }}
          >
            {t.label}
          </button>
        ))}
      </div>

      {hata && <div className="card mb-4 border-red/40 bg-red/10 text-[13px] text-red">{hata}</div>}

      <div className="card">
        {q.isLoading ? (
          <p className="py-8 text-center text-ink-soft">Yükleniyor…</p>
        ) : q.isError ? (
          <p className="py-8 text-center text-red">
            {q.error instanceof ApiException ? q.error.message : 'Başvurular yüklenemedi'}
          </p>
        ) : basvurular.length === 0 ? (
          <p className="py-8 text-center text-[13.5px] text-ink-soft">
            Bu filtrede başvuru yok. Formunuzu paylaştıkça talepler buraya düşer.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Ad Soyad</th>
                  <th>Telefon</th>
                  <th>Branş</th>
                  <th>Tarih</th>
                  <th>Durum</th>
                  <th className="t-right">İşlem</th>
                </tr>
              </thead>
              <tbody>
                {basvurular.map((b) => (
                  <Satir
                    key={b.id}
                    b={b}
                    islemde={durumMutation.isPending}
                    onAc={() => setDetay(b)}
                    onDurum={durumDegistir}
                    onDonustur={() => setDonusturulecek(b)}
                    onOgrenciAc={(id) => navigate(`/ogrenciler/${id}`)}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {meta && meta.totalPages > 1 && (
        <div className="mt-3 flex items-center justify-between text-[13px] text-ink-soft">
          <span>
            {meta.totalElements} başvuru — sayfa {meta.page + 1}/{meta.totalPages}
          </span>
          <div className="flex gap-2">
            <button
              type="button"
              className="btn btn-ghost"
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              Önceki
            </button>
            <button
              type="button"
              className="btn btn-ghost"
              disabled={page + 1 >= meta.totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Sonraki
            </button>
          </div>
        </div>
      )}

      {detay && (
        <BasvuruDetayModal
          basvuru={detay}
          islemde={durumMutation.isPending}
          onKapat={() => setDetay(null)}
          onDurum={(durum) => {
            durumDegistir(detay.id, durum);
            setDetay(null);
          }}
          onDonustur={() => {
            setDonusturulecek(detay);
            setDetay(null);
          }}
          onOgrenciAc={(id) => navigate(`/ogrenciler/${id}`)}
        />
      )}

      {donusturulecek && (
        <DonusturModal
          basvuru={donusturulecek}
          onKapat={() => setDonusturulecek(null)}
          onTamam={(ogrenciId) => {
            setDonusturulecek(null);
            navigate(`/ogrenciler/${ogrenciId}`);
          }}
        />
      )}
    </div>
  );
}

function Satir({
  b,
  islemde,
  onAc,
  onDurum,
  onDonustur,
  onOgrenciAc,
}: {
  b: BasvuruResponse;
  islemde: boolean;
  onAc: () => void;
  onDurum: (id: number, durum: BasvuruDurumu) => void;
  onDonustur: () => void;
  onOgrenciAc: (id: number) => void;
}) {
  const donusmus = b.durum === 'OGRENCIYE_DONUSTU';
  return (
    // Satir detayi acar (mesaj/e-posta yalnizca orada gorunur). Klavyeyle de erisilebilir.
    <tr
      className="cursor-pointer hover:bg-bg"
      onClick={onAc}
      tabIndex={0}
      role="button"
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onAc();
        }
      }}
    >
      <td>
        <b>
          {b.ad} {b.soyad}
        </b>
        {b.veliAdi && <div className="text-[12px] text-ink-soft">Veli: {b.veliAdi}</div>}
        {/* Mesaj tabloya sigmaz; en azindan VARLIGINI belli et ki hangi satiri acacagi bilinsin. */}
        {b.mesaj && <div className="text-[12px] text-ink-soft">✉︎ mesaj var</div>}
      </td>
      <td>
        <a href={`tel:${b.telefon}`} className="text-rasp" onClick={(e) => e.stopPropagation()}>
          {b.telefon}
        </a>
        {b.email && <div className="text-[12px] text-ink-soft">{b.email}</div>}
      </td>
      <td className="text-ink-soft">{b.bransAdi ?? '—'}</td>
      <td className="text-ink-soft">{formatDateTime(b.olusturulmaTarihi)}</td>
      <td>
        <span className={`badge ${DURUM_BADGE[b.durum]}`}>{DURUM_ETIKET[b.durum]}</span>
      </td>
      {/* Dugmeler satir tiklamasini TETIKLEMEMELI; aksi halde her islemde modal da acilir. */}
      <td className="t-right" onClick={(e) => e.stopPropagation()}>
        {donusmus ? (
          <button
            type="button"
            className="btn btn-ghost"
            onClick={() => b.ogrenciId && onOgrenciAc(b.ogrenciId)}
          >
            Öğrenciyi aç
          </button>
        ) : (
          <div className="flex justify-end gap-2">
            {b.durum === 'YENI' && (
              <button
                type="button"
                className="btn btn-ghost"
                disabled={islemde}
                onClick={() => onDurum(b.id, 'ARANDI')}
              >
                Arandı
              </button>
            )}
            {b.durum !== 'OLUMSUZ' && (
              <button
                type="button"
                className="btn btn-ghost"
                disabled={islemde}
                onClick={() => onDurum(b.id, 'OLUMSUZ')}
              >
                Olumsuz
              </button>
            )}
            <button type="button" className="btn btn-primary" onClick={onDonustur}>
              Öğrenciye dönüştür
            </button>
          </div>
        )}
      </td>
    </tr>
  );
}
