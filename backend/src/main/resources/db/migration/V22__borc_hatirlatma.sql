-- V22: borclu veli hatirlatma izi — TENANT-DUZEYI (tenant filtresine tabidir).
--
-- AMAC: ayni veliye kisa araliklarla tekrar tekrar mail atilmasini ONLEMEK. Bir okulun
-- velisine ust uste hatirlatma gitmesi, okulun velisiyle iliskisini bozar ve bizim alan
-- adimizin spam olarak isaretlenmesine yol acar (o zaman KENDI odeme uyarilarimiz da
-- spam'e duser). Bu tablo "en son ne zaman gonderildi" sorusunun cevabidir.

CREATE TABLE borc_hatirlatma
(
    id          UUID           PRIMARY KEY,
    tenant_id   UUID           NOT NULL,
    ogrenci_id  BIGINT         NOT NULL,
    -- Gonderim anindaki borc (sonradan degisse de "ne icin uyarildi" belli olsun).
    tutar       NUMERIC(12,2)  NOT NULL,
    alici       VARCHAR(200)   NOT NULL,
    -- Gonderen kullanici (sorumluluk izi).
    gonderen    VARCHAR(120)   NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- "Bu ogrenciye son X gunde gonderildi mi?" sorgusu bunun uzerinden calisir.
CREATE INDEX idx_borc_hatirlatma_ogrenci ON borc_hatirlatma (tenant_id, ogrenci_id, created_at DESC);
