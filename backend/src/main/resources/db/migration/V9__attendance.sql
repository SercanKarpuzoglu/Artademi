-- V9: Yoklama (attendance) — oturum + ogrenci girisleri. Tenant-aware: her satir tenant_id (UUID,
-- NOT NULL) tasir ve global Hibernate tenant filtresine tabidir. tenant_id ASLA istemciden gelmez;
-- yazma sirasinda TenantContext'ten otomatik set edilir.
--
-- attendance_session: bir grubun (grup_id, zorunlu) belirli bir tarihteki (tarih, zorunlu) yoklama
-- oturumu. Oturumun var olmasi = o tarih icin yoklama alinmis demektir (ayri statu alani YOK).
-- program_id (opsiyonel) oturumun hangi haftalik ders saatine ait oldugunu baglar.
--
-- attendance_entry: oturumdaki her ogrenci icin tek bir durum satiri (GELDI/GELMEDI/IZINLI). Oturum
-- olusturulurken gruptaki AKTIF kayitli ogrenciler icin GELMEDI varsayilani ile otomatik uretilir.
--
-- Referanslarin (grup, program, ogrenci) BASKA tenant'a ait olamamasi uygulama katmaninda
-- (findScopedById) garanti edilir; buradaki FK'lar yalnizca referans butunlugu icindir, tenant
-- izolasyonu DEGIL.
--
-- Silme YOK. Seed YOK.

CREATE TABLE attendance_session (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID         NOT NULL,
    grup_id            BIGINT       NOT NULL REFERENCES lesson_group (id),
    tarih              DATE         NOT NULL,
    program_id         BIGINT       REFERENCES schedule (id),
    notu               TEXT,
    olusturulma_tarihi TIMESTAMPTZ  NOT NULL,
    guncellenme_tarihi TIMESTAMPTZ  NOT NULL,
    -- Ayni tenant'ta ayni grup icin ayni tarihte tek oturum olabilir (mukerrer yoklama engeli).
    CONSTRAINT uq_attendance_session_grup_tarih UNIQUE (tenant_id, grup_id, tarih)
);

-- Bilesik indekslerin ilk kolonu tenant_id (cogu sorgu onunla filtrelenir).
CREATE INDEX idx_attendance_session_tenant ON attendance_session (tenant_id);

CREATE TABLE attendance_entry (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID         NOT NULL,
    session_id         BIGINT       NOT NULL REFERENCES attendance_session (id),
    ogrenci_id         BIGINT       NOT NULL REFERENCES students (id),
    durum              VARCHAR(20)  NOT NULL,
    olusturulma_tarihi TIMESTAMPTZ  NOT NULL,
    guncellenme_tarihi TIMESTAMPTZ  NOT NULL,
    -- Bir oturumda bir ogrenci icin tek giris olabilir.
    CONSTRAINT uq_attendance_entry_session_ogrenci UNIQUE (session_id, ogrenci_id)
);

-- Bilesik indekslerin ilk kolonu tenant_id (cogu sorgu onunla filtrelenir).
CREATE INDEX idx_attendance_entry_tenant ON attendance_entry (tenant_id);
CREATE INDEX idx_attendance_entry_session ON attendance_entry (session_id);
