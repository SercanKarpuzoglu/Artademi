-- V26: Kurum bazli GIZLI ayar saklama (sifreli).
--
-- Neden gerekli: bugune kadar tek dis entegrasyon iyzico ve onun anahtari TEK ve
-- PLATFORM duzeyinde (.env). SMS/WhatsApp gibi entegrasyonlarda ise HER KURUMUN kendi
-- kimlik bilgisi olur ve bunlar veritabaninda durmak zorundadir. Duz metin kabul edilemez:
-- veritabani yedegi sizarsa tum musterilerimizin saglayici hesaplari ele gecer.
--
-- ⚠️ Bu tabloya YALNIZCA sifrelenmis deger yazilir. Sifrelenmemis ayar buraya konmaz;
-- normal (gizli olmayan) tercihler icin bildirim_ayari gibi kendi tablosu kullanilir.
--
-- Deger bicimi: "v1:<base64 IV>:<base64 sifreli metin+etiket>" (AES-256-GCM).
-- Surum oneki BILINCLIDIR: ileride anahtar rotasyonu gerektiginde eski kayitlar
-- v1 ile okunmaya devam eder, yeniler v2 yazilir.

CREATE TABLE gizli_ayar (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID         NOT NULL,

    -- Nokta ile ayrilmis anahtar, ornek: 'sms.netgsm.kullanici', 'whatsapp.waba_id'
    anahtar            VARCHAR(100) NOT NULL,

    -- Sifreli deger. TEXT: sifreli metin duz metinden uzundur, sinir koymak riskli.
    deger_sifreli      TEXT         NOT NULL,

    -- Maskeli gosterim icin duz metnin SON 4 karakteri (ornek: "••••4821").
    -- Tam deger ASLA disari verilmez; yonetici "dogru anahtari mi girdim" diye
    -- bakabilsin diye yalnizca bu kadari saklanir.
    maske              VARCHAR(8),

    -- Kim en son degistirdi (denetim izi).
    guncelleyen        VARCHAR(150),

    olusturulma_tarihi TIMESTAMPTZ  NOT NULL,
    guncellenme_tarihi TIMESTAMPTZ  NOT NULL
);

-- Kurum + anahtar TEKtir; ikinci satir olusursa hangisinin gecerli oldugu belirsiz kalirdi.
CREATE UNIQUE INDEX uq_gizli_ayar ON gizli_ayar (tenant_id, anahtar);
CREATE INDEX idx_gizli_ayar_tenant ON gizli_ayar (tenant_id);
