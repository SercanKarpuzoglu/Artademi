-- V1: baslangic semasi (iskelet).
-- Gercek is tablolari (tenant, abonelik, ogrenci, ...) sonraki migration'larda gelecek.
-- Bu tablo yalnizca semanin kuruldugunu/dogrulandigini gostermek icindir.

CREATE TABLE schema_info (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    component  VARCHAR(100) NOT NULL,
    applied_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO schema_info (component) VALUES ('backend-baseline');
