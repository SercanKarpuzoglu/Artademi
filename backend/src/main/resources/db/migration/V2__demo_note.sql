-- V2: tenant filtresini KANITLAMAK icin gecici demo tablosu.
-- Gercek is modulleri (ogrenci, ders, yoklama, ...) sonraki adimlarda gelecek;
-- bu tablo yalnizca tenant izolasyonunu uctan uca gostermek icindir.
-- NOT: V2 yalnizca yerelde demo amaclidir; volume sifirlaninca yeniden calisir.

CREATE TABLE demo_note (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id UUID         NOT NULL,
    text      VARCHAR(500) NOT NULL
);

-- tenant_id ile filtrelendigi icin indeks (is tablolarinda standart).
CREATE INDEX idx_demo_note_tenant ON demo_note (tenant_id);

-- Seed: SABIT, BILINEN iki tenant UUID'si (tekrar test edilebilsin diye).
--   tenant A = 11111111-1111-1111-1111-111111111111 -> 2 not
--   tenant B = 22222222-2222-2222-2222-222222222222 -> 1 not
INSERT INTO demo_note (tenant_id, text) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Tenant A - not A1'),
    ('11111111-1111-1111-1111-111111111111', 'Tenant A - not A2'),
    ('22222222-2222-2222-2222-222222222222', 'Tenant B - not B1');
