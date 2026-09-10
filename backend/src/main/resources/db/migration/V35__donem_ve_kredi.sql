-- V35: Donem & kredi modeli (Dalga E, urun karari 2026-09-10: mevcut aylik aidat modelinin YERINE gecer).
--
-- donem: kurum bazli egitim donemi ("2026-27 Guz", 14 Eyl - 31 Oca). Grup bir doneme baglanir.
-- lesson_group: donem_id + donemlik_ucret (aylik_aidat = "aylik ucret" olarak kalir; OZEL'de ders_basi_ucret).
-- enrollment: odeme_plani AYLIK|DONEMLIK (NULL = AYLIK, eski kayitlar), donem_id (DONEMLIK'te grubun donemi).
-- ders_paketi: kaynak ELLE|KAYIT_DONEMLIK|AYLIK_KREDI, kaynak_donem "YYYY-MM" (aylik kredi mukerrer kalkani).
--   KREDI = ders paketi: donemlik kayit -> donemdeki ders sayisi kadar paket + tek tahakkuk;
--   aylik kayit -> her ay Otomatik Tahakkuk ile o ayin ders sayisi kadar 0 TL'lik kredi paketi (aidat ayri).
CREATE TABLE donem (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID         NOT NULL,
    ad                 VARCHAR(120) NOT NULL,
    baslangic          DATE         NOT NULL,
    bitis              DATE         NOT NULL,
    aktif              BOOLEAN      NOT NULL DEFAULT TRUE,
    olusturulma_tarihi TIMESTAMPTZ  NOT NULL DEFAULT now(),
    guncellenme_tarihi TIMESTAMPTZ  NOT NULL DEFAULT now(),
    silindi_tarihi     TIMESTAMPTZ,
    silen              VARCHAR(100)
);
CREATE INDEX ix_donem_tenant ON donem (tenant_id);

ALTER TABLE lesson_group
    ADD COLUMN donem_id       BIGINT REFERENCES donem (id),
    ADD COLUMN donemlik_ucret NUMERIC(10,2);

ALTER TABLE enrollment
    ADD COLUMN odeme_plani VARCHAR(10),
    ADD COLUMN donem_id    BIGINT REFERENCES donem (id);

ALTER TABLE ders_paketi
    ADD COLUMN kaynak       VARCHAR(20) NOT NULL DEFAULT 'ELLE',
    ADD COLUMN kaynak_donem VARCHAR(7);
CREATE INDEX ix_ders_paketi_kaynak ON ders_paketi (tenant_id, kaynak, kaynak_donem);
