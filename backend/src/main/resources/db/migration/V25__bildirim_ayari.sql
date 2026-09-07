-- V25: Otomatik bildirim ayarlari + devamsizlik bildirimi izi.
--
-- ⚠️ HEPSI VARSAYILAN KAPALI (opt-in). Gerekce: BorcHatirlatmaService bilincli olarak ELLE
-- tetikleniyordu ("otomatik borc takibi, okulun velisiyle iliskisini yonetmesini elinden alir").
-- Otomatiklestirme bu karari IPTAL ETMEZ, kurumun tercihine birakir: acmayan kurum bugunku
-- elle akista kalir.
--
-- Mailler bizim alan adimizdan gidiyor; veliler spam isaretlerse KENDI odeme uyarilarimiz da
-- spam'e duser. Bu yuzden otomatik gonderim de soguma + tavan kurallarina tabidir.

CREATE TABLE bildirim_ayari (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id                UUID        NOT NULL,

    -- Borclu velilere otomatik odeme hatirlatmasi (mevcut 7 gun soguma + gunluk tavan gecerli).
    borc_hatirlatma_otomatik BOOLEAN     NOT NULL DEFAULT FALSE,

    -- Ogrenci derse gelmediginde velisine ayni aksam bilgi maili.
    devamsizlik_bildirimi    BOOLEAN     NOT NULL DEFAULT FALSE,

    -- Kurum yoneticilerine haftalik finansal ozet.
    haftalik_ozet            BOOLEAN     NOT NULL DEFAULT FALSE,
    -- ISO-8601: 1=Pazartesi … 7=Pazar.
    haftalik_ozet_gunu       SMALLINT    NOT NULL DEFAULT 1,

    olusturulma_tarihi       TIMESTAMPTZ NOT NULL,
    guncellenme_tarihi       TIMESTAMPTZ NOT NULL
);

-- Kurum basina TEK satir; ikinci satir olusursa hangisinin gecerli oldugu belirsiz kalirdi.
CREATE UNIQUE INDEX uq_bildirim_ayari_tenant ON bildirim_ayari (tenant_id);

-- Devamsizlik bildirimi izi — MUKERRER GONDERIM KALKANI.
-- Job her aksam calisir; ayni yoklama kaydi icin ikinci kez mail GITMEMELI (yoklama sonradan
-- duzeltilse bile veli iki kez uyarilmamali).
CREATE TABLE devamsizlik_bildirimi (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID        NOT NULL,
    ogrenci_id         BIGINT      NOT NULL REFERENCES students (id),
    oturum_id          BIGINT      NOT NULL REFERENCES attendance_session (id),
    alici              VARCHAR(255) NOT NULL,
    olusturulma_tarihi TIMESTAMPTZ NOT NULL
);

-- Idempotans kilidi: ayni ogrenci + ayni oturum icin tek kayit.
CREATE UNIQUE INDEX uq_devamsizlik_bildirimi ON devamsizlik_bildirimi (tenant_id, ogrenci_id, oturum_id);
CREATE INDEX idx_devamsizlik_bildirimi_tenant ON devamsizlik_bildirimi (tenant_id);
