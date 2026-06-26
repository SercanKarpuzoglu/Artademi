-- V15: Ogretmen hakedis tipleri (teacher_hakedis) — Model C. Tenant-aware: her satir tenant_id
-- (UUID, NOT NULL) tasir ve global Hibernate tenant filtresine tabidir. tenant_id ASLA istemciden
-- gelmez; yazma sirasinda TenantContext'ten otomatik set edilir.
--
-- Model C: hakedis tipi artik ogretmenin uzerinde TEK alan DEGIL. Ogretmen birden cok hakedis tipi
-- TANIMLAYABILIR (her tip icin oran/ucret); hangi tipin uygulanacagini GRUP belirler (V16). Bir
-- ogretmen + bir tip yalnizca BIR kez (UNIQUE (teacher_id, tip)).
--
-- Yalnizca tip ile eslesen tutar kolonu dolar:
--   SAATLIK    -> saatlik_ucret (NUMERIC(10,2))
--   CIRO_ORANI -> ciro_orani    (NUMERIC(5,2))
--   OZEL_DERS  -> ders_basi_ucret (NUMERIC(10,2))
--
-- Silme YOK.

CREATE TABLE teacher_hakedis (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       UUID          NOT NULL,
    teacher_id      BIGINT        NOT NULL REFERENCES teachers (id),
    tip             VARCHAR(20)   NOT NULL,
    saatlik_ucret   NUMERIC(10,2),
    ciro_orani      NUMERIC(5,2),
    ders_basi_ucret NUMERIC(10,2),
    CONSTRAINT uq_teacher_hakedis UNIQUE (teacher_id, tip)
);

-- Bilesik indekslerin ilk kolonu tenant_id (cogu sorgu onunla filtrelenir).
CREATE INDEX idx_teacher_hakedis_tenant ON teacher_hakedis (tenant_id);
CREATE INDEX idx_teacher_hakedis_teacher ON teacher_hakedis (teacher_id);

-- VERI GOCU: her ogretmenin MEVCUT tek hakedis tipi -> tek satira tasinir (Selin = SAATLIK 350
-- korunur). tenant_id ve ilgili tutar dogrudan teachers'tan kopyalanir.
INSERT INTO teacher_hakedis (tenant_id, teacher_id, tip, saatlik_ucret, ciro_orani)
SELECT tenant_id, id, hakedis_tipi, saatlik_ucret, ciro_orani
FROM teachers;

-- Eski tek-tip kolonlari kaldir (Model C'de artik teacher_hakedis tasiyor).
ALTER TABLE teachers DROP COLUMN hakedis_tipi;
ALTER TABLE teachers DROP COLUMN saatlik_ucret;
ALTER TABLE teachers DROP COLUMN ciro_orani;
