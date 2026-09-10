-- V33: Dalga C — yoklama kilidi + uygulama ici bildirim + "yoklama alinmadi" e-posta ayari.
--
-- attendance_session.kaydedildi_tarihi: egitmen "Kaydet" dedikten sonra oturum EGITMEN icin kilitlenir
-- (yonetici/ofis duzeltir). NULL = henuz kaydedilmemis (acildi ama Kaydet'e basilmadi).
ALTER TABLE attendance_session
    ADD COLUMN kaydedildi_tarihi TIMESTAMPTZ,
    ADD COLUMN kaydeden VARCHAR(100);

-- Egitmene "bugun yoklama almadin" e-postasi (uygulama ici bildirim her zaman gider; e-posta tercihe bagli).
ALTER TABLE bildirim_ayari ADD COLUMN yoklama_alinmadi_eposta BOOLEAN NOT NULL DEFAULT FALSE;

-- Uygulama ici bildirim (zil + 30 sn'de bir sorgu + toast). Okunma isareti satir bazinda (kucuk kurum:
-- ofis 1-2 kisi); kullanici bazli okunma gerekirse ayri tablo.
CREATE TABLE uygulama_bildirimi (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID         NOT NULL,
    tip                VARCHAR(40)  NOT NULL,
    -- Virgulle ayrilmis roller: ADMIN,FRONTDESK … Cagiranin rolleriyle kesisiyorsa gorunur.
    hedef_roller       VARCHAR(120) NOT NULL,
    -- Doluysa YALNIZ bu kullanici (Keycloak sub; egitmen bildirimleri) gorur.
    hedef_kullanici    VARCHAR(120),
    baslik             VARCHAR(200) NOT NULL,
    metin              TEXT,
    baglanti           VARCHAR(200),
    okundu_tarihi      TIMESTAMPTZ,
    olusturulma_tarihi TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_uygulama_bildirimi_tenant ON uygulama_bildirimi (tenant_id, id DESC);
