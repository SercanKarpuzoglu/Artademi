import { useEffect, useState } from 'react';
import { ApiException } from '../../api/client';
import { basvuruGonder, getBasvuruFormu } from '../../api/basvuru';
import type { BasvuruFormBilgisi } from '../../api/types';

/**
 * Kurumun public ön kayıt formu — `/basvuru/:slug`.
 *
 * ⚠️ Bu sayfa Keycloak'ın DIŞINDA çalışır: main.tsx bu rotayı görürse kimlik doğrulamayı
 * hiç başlatmaz ve doğrudan burayı render eder. Bu yüzden burada `useAuth`, paylaşılan
 * `api` istemcisi veya AppShell KULLANILAMAZ — hepsi Keycloak'a bağlıdır. Veri erişimi
 * yalnızca `api/basvuru.ts`in public fonksiyonları üzerinden yapılır.
 *
 * Sayfa kendi stilini taşır (Tailwind sınıfları yerine gömülü stil): panelin tasarım
 * sisteminden bağımsız olmalı, çünkü veliye gösterilen tek ekran budur.
 */
export default function PublicBasvuruPage({ slug }: { slug: string }) {
  const [form, setForm] = useState<BasvuruFormBilgisi | null>(null);
  const [yukleniyor, setYukleniyor] = useState(true);
  const [bulunamadi, setBulunamadi] = useState(false);
  const [gonderiliyor, setGonderiliyor] = useState(false);
  const [gonderildi, setGonderildi] = useState(false);
  const [hata, setHata] = useState<string | null>(null);

  const [ad, setAd] = useState('');
  const [soyad, setSoyad] = useState('');
  const [telefon, setTelefon] = useState('');
  const [email, setEmail] = useState('');
  const [veliAdi, setVeliAdi] = useState('');
  const [bransId, setBransId] = useState('');
  const [mesaj, setMesaj] = useState('');
  // Honeypot: gerçek kullanıcı bu alanı göremez, bot doldurur.
  const [website, setWebsite] = useState('');

  useEffect(() => {
    let iptal = false;
    getBasvuruFormu(slug)
      .then((f) => {
        if (!iptal) setForm(f);
      })
      .catch(() => {
        if (!iptal) setBulunamadi(true);
      })
      .finally(() => {
        if (!iptal) setYukleniyor(false);
      });
    return () => {
      iptal = true;
    };
  }, [slug]);

  const gonder = async (e: React.FormEvent) => {
    e.preventDefault();
    setHata(null);
    setGonderiliyor(true);
    try {
      await basvuruGonder(slug, {
        ad,
        soyad,
        telefon,
        email: email || undefined,
        veliAdi: veliAdi || undefined,
        bransId: bransId ? Number(bransId) : null,
        mesaj: mesaj || undefined,
        website: website || undefined,
      });
      setGonderildi(true);
    } catch (err) {
      setHata(
        err instanceof ApiException ? err.message : 'Başvurunuz gönderilemedi. Tekrar deneyin.',
      );
    } finally {
      setGonderiliyor(false);
    }
  };

  if (yukleniyor) {
    return <Kabuk><p style={S.durum}>Yükleniyor…</p></Kabuk>;
  }

  if (bulunamadi || !form) {
    return (
      <Kabuk>
        <h1 style={S.baslik}>Form bulunamadı</h1>
        <p style={S.durum}>
          Bu başvuru bağlantısı geçerli değil ya da kaldırılmış. Lütfen kurumla iletişime geçin.
        </p>
      </Kabuk>
    );
  }

  if (gonderildi) {
    return (
      <Kabuk>
        <div style={S.basariIkon}>✓</div>
        <h1 style={S.baslik}>Başvurunuz alındı</h1>
        <p style={S.durum}>
          <b>{form.kurumAdi}</b> en kısa sürede sizinle iletişime geçecek. Teşekkür ederiz.
        </p>
      </Kabuk>
    );
  }

  return (
    <Kabuk>
      <div style={S.kurum}>{form.kurumAdi}</div>
      <h1 style={S.baslik}>Ön Kayıt Formu</h1>
      <p style={S.altBaslik}>
        Bilgilerinizi bırakın, sizi arayalım. Yıldızlı alanlar zorunludur.
      </p>

      <form onSubmit={gonder} style={S.form}>
        <div style={S.satir}>
          <Alan etiket="Ad *">
            <input style={S.input} value={ad} onChange={(e) => setAd(e.target.value)} required
              maxLength={100} autoComplete="given-name" />
          </Alan>
          <Alan etiket="Soyad *">
            <input style={S.input} value={soyad} onChange={(e) => setSoyad(e.target.value)}
              required maxLength={100} autoComplete="family-name" />
          </Alan>
        </div>

        <Alan etiket="Telefon *">
          <input style={S.input} value={telefon} onChange={(e) => setTelefon(e.target.value)}
            required maxLength={30} inputMode="tel" autoComplete="tel"
            placeholder="05XX XXX XX XX" />
        </Alan>

        <Alan etiket="E-posta">
          <input style={S.input} type="email" value={email}
            onChange={(e) => setEmail(e.target.value)} maxLength={255} autoComplete="email" />
        </Alan>

        <Alan etiket="Veli Adı" ipucu="Öğrenci 18 yaşından küçükse doldurun">
          <input style={S.input} value={veliAdi} onChange={(e) => setVeliAdi(e.target.value)}
            maxLength={200} />
        </Alan>

        {form.branslar.length > 0 && (
          <Alan etiket="İlgilendiğiniz Branş">
            <select style={S.input} value={bransId} onChange={(e) => setBransId(e.target.value)}>
              <option value="">Seçiniz (isteğe bağlı)</option>
              {form.branslar.map((b) => (
                <option key={b.id} value={b.id}>{b.ad}</option>
              ))}
            </select>
          </Alan>
        )}

        <Alan etiket="Mesajınız">
          <textarea style={{ ...S.input, minHeight: 90, resize: 'vertical' }} value={mesaj}
            onChange={(e) => setMesaj(e.target.value)} maxLength={1000}
            placeholder="Eklemek istedikleriniz…" />
        </Alan>

        {/* Honeypot — ekran okuyucudan ve gözden gizli; yalnızca botlar doldurur. */}
        <div aria-hidden="true" style={{ position: 'absolute', left: '-9999px' }}>
          <label htmlFor="website">Web sitesi</label>
          <input id="website" tabIndex={-1} autoComplete="off" value={website}
            onChange={(e) => setWebsite(e.target.value)} />
        </div>

        {hata && <div style={S.hata}>{hata}</div>}

        <button type="submit" style={S.btn} disabled={gonderiliyor}>
          {gonderiliyor ? 'Gönderiliyor…' : 'Başvurumu Gönder'}
        </button>

        <p style={S.kvkk}>
          Gönderdiğiniz bilgiler yalnızca {form.kurumAdi} tarafından, sizinle iletişime geçmek
          amacıyla kullanılır.
        </p>
      </form>
    </Kabuk>
  );
}

function Kabuk({ children }: { children: React.ReactNode }) {
  return (
    <div style={S.sayfa}>
      <div style={S.kart}>{children}</div>
      <div style={S.imza}>Artademi ile hazırlandı</div>
    </div>
  );
}

function Alan({
  etiket,
  ipucu,
  children,
}: {
  etiket: string;
  ipucu?: string;
  children: React.ReactNode;
}) {
  return (
    <label style={S.alan}>
      <span style={S.etiket}>{etiket}</span>
      {children}
      {ipucu && <span style={S.ipucu}>{ipucu}</span>}
    </label>
  );
}

const S: Record<string, React.CSSProperties> = {
  sayfa: {
    minHeight: '100vh',
    background: '#faf7f9',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '32px 16px',
    fontFamily: "'Manrope', system-ui, -apple-system, sans-serif",
    color: '#241019',
  },
  kart: {
    width: '100%',
    maxWidth: 520,
    background: '#fff',
    border: '1px solid rgba(36,16,25,.10)',
    borderRadius: 16,
    padding: '32px 28px',
    boxShadow: '0 1px 2px rgba(36,16,25,.05), 0 8px 24px rgba(36,16,25,.06)',
  },
  kurum: {
    fontSize: 12.5,
    fontWeight: 600,
    letterSpacing: '.12em',
    textTransform: 'uppercase',
    color: '#c2185b',
    marginBottom: 6,
  },
  baslik: { fontSize: 26, fontWeight: 700, margin: 0, letterSpacing: '-.01em' },
  altBaslik: { fontSize: 14, color: '#7a6570', marginTop: 8, marginBottom: 0 },
  form: { display: 'flex', flexDirection: 'column', gap: 14, marginTop: 22 },
  satir: { display: 'flex', gap: 12, flexWrap: 'wrap' },
  alan: { display: 'flex', flexDirection: 'column', gap: 5, flex: '1 1 180px' },
  etiket: { fontSize: 13, fontWeight: 600 },
  ipucu: { fontSize: 12, color: '#7a6570' },
  input: {
    width: '100%',
    boxSizing: 'border-box',
    borderRadius: 10,
    border: '1px solid rgba(36,16,25,.18)',
    background: '#fff',
    color: '#241019',
    padding: '10px 12px',
    fontSize: 14,
    fontFamily: 'inherit',
  },
  btn: {
    marginTop: 6,
    padding: '13px 18px',
    borderRadius: 10,
    border: 'none',
    background: '#c2185b',
    color: '#fff',
    fontSize: 15,
    fontWeight: 700,
    fontFamily: 'inherit',
    cursor: 'pointer',
  },
  hata: {
    borderRadius: 10,
    border: '1px solid rgba(192,57,43,.35)',
    background: 'rgba(192,57,43,.08)',
    color: '#c0392b',
    padding: '10px 12px',
    fontSize: 13.5,
  },
  kvkk: { fontSize: 12, color: '#7a6570', marginTop: 4, marginBottom: 0, lineHeight: 1.5 },
  durum: { fontSize: 14.5, color: '#7a6570', marginTop: 10 },
  basariIkon: {
    width: 46,
    height: 46,
    borderRadius: '50%',
    background: '#e6f3ec',
    color: '#2e7d5b',
    display: 'grid',
    placeItems: 'center',
    fontSize: 24,
    fontWeight: 700,
    marginBottom: 14,
  },
  imza: { marginTop: 18, fontSize: 12, color: '#a08e98' },
};
