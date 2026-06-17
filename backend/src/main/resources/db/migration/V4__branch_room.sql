-- V4: Brans (branches) + Salon (rooms) tanim tablolari.
-- Ikisi de tenant-aware: her satir tenant_id (UUID, NOT NULL) tasir ve global Hibernate
-- tenant filtresine tabidir. tenant_id ASLA istemciden gelmez; yazma sirasinda
-- TenantContext'ten otomatik set edilir.
--
-- Silme YOK: kayitlar silinmez, "aktif" alani ile pasiflestirilir.
-- Seed YOK.

CREATE TABLE branches (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID         NOT NULL,
    ad                 VARCHAR(150) NOT NULL,
    aciklama           VARCHAR(500),
    aktif              BOOLEAN      NOT NULL DEFAULT TRUE,
    olusturulma_tarihi TIMESTAMPTZ  NOT NULL,
    guncellenme_tarihi TIMESTAMPTZ  NOT NULL
);

-- Bilesik indekslerin ilk kolonu tenant_id (cogu sorgu onunla filtrelenir).
CREATE INDEX idx_branches_tenant ON branches (tenant_id);
CREATE INDEX idx_branches_tenant_ad ON branches (tenant_id, ad);

CREATE TABLE rooms (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID         NOT NULL,
    ad                 VARCHAR(150) NOT NULL,
    kapasite           INTEGER,
    aciklama           VARCHAR(500),
    aktif              BOOLEAN      NOT NULL DEFAULT TRUE,
    olusturulma_tarihi TIMESTAMPTZ  NOT NULL,
    guncellenme_tarihi TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_rooms_tenant ON rooms (tenant_id);
CREATE INDEX idx_rooms_tenant_ad ON rooms (tenant_id, ad);
