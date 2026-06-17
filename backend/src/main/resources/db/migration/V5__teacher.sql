-- V5: Ogretmen (teachers) + Ogretmen-Brans baglantisi (teacher_branch).
-- Ikisi de tenant-aware: her satir tenant_id (UUID, NOT NULL) tasir ve global Hibernate
-- tenant filtresine tabidir. tenant_id ASLA istemciden gelmez; yazma sirasinda
-- TenantContext'ten otomatik set edilir.
--
-- Ogretmen <-> Brans cok-coga iliskisi: @ManyToMany join tablosuna tenant_id NOT NULL
-- otomatik konamadigi icin ACIK baglanti entity'si (TeacherBranch) kullanilir; boylece
-- join satiri da tenant_id tasir ve filtreye tabidir.
--
-- Silme YOK: ogretmen silinmez, "aktif" alani ile pasiflestirilir.
-- Seed YOK.

CREATE TABLE teachers (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID          NOT NULL,
    ad                 VARCHAR(100)  NOT NULL,
    soyad              VARCHAR(100)  NOT NULL,
    telefon            VARCHAR(20),
    email              VARCHAR(255),
    keycloak_user_id   VARCHAR(255),
    hakedis_tipi       VARCHAR(20)   NOT NULL,
    saatlik_ucret      NUMERIC(10, 2),
    ciro_orani         NUMERIC(5, 2),
    aktif              BOOLEAN       NOT NULL DEFAULT TRUE,
    olusturulma_tarihi TIMESTAMPTZ   NOT NULL,
    guncellenme_tarihi TIMESTAMPTZ   NOT NULL
);

-- Bilesik indekslerin ilk kolonu tenant_id (cogu sorgu onunla filtrelenir).
CREATE INDEX idx_teachers_tenant ON teachers (tenant_id);
CREATE INDEX idx_teachers_tenant_soyad ON teachers (tenant_id, soyad);

CREATE TABLE teacher_branch (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  UUID   NOT NULL,
    teacher_id BIGINT NOT NULL REFERENCES teachers (id),
    branch_id  BIGINT NOT NULL REFERENCES branches (id),
    CONSTRAINT uq_teacher_branch UNIQUE (teacher_id, branch_id)
);

CREATE INDEX idx_teacher_branch_tenant ON teacher_branch (tenant_id);
CREATE INDEX idx_teacher_branch_teacher ON teacher_branch (teacher_id);
CREATE INDEX idx_teacher_branch_branch ON teacher_branch (branch_id);
