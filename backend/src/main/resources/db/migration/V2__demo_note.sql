-- V2: tenant filtresini KANITLAMAK icin gecici demo tablosu.
-- Gercek is modulleri (ogrenci, ders, yoklama, ...) sonraki adimlarda gelecek;
-- bu tablo yalnizca tenant izolasyonunu uctan uca gostermek icindir.

CREATE TABLE demo_note (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT       NOT NULL,
    text      VARCHAR(500) NOT NULL
);

-- tenant_id ile filtrelendigi icin indeks (is tablolarinda standart).
CREATE INDEX idx_demo_note_tenant ON demo_note (tenant_id);

-- Seed: tenant 1'e 2 not, tenant 2'ye 1 not.
INSERT INTO demo_note (tenant_id, text) VALUES
    (1, 'Tenant 1 - not A'),
    (1, 'Tenant 1 - not B'),
    (2, 'Tenant 2 - not C');
