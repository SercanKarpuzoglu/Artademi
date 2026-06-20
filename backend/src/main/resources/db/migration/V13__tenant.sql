-- V13: Tenant (kiraci) birinci-sinif entity.
--
-- ⚠️ Bu tablo TENANT FILTRESINE TABI DEGILDIR. Tenant kaydi tenant'larin USTUNDEdir; global
-- Hibernate tenant filtresine girerse kendi kaydini filtreler (gorunmez olur). Bu yuzden Tenant
-- entity'si TenantAware'i GENISLETMEZ ve burada tenant_id KOLONU YOKTUR.
--
-- PK = tenant UUID; JWT'deki tenant_id claim'i ile birebir ayni deger. Diger is tablolarindaki
-- tenant_id kolonlari mantiken bu tabloya isaret eder (mevcut yapiya dokunulmadi).
--
-- status: AKTIF / ASKIDA (abonelik ileride askiya alabilsin). Simdilik default AKTIF.

CREATE TABLE tenant (
    id         UUID         PRIMARY KEY,
    ad         VARCHAR(200) NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'AKTIF',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Seed: mevcut tek tenant (handoff test verisi — bkz. design-reference "Lina Sanat Merkezi").
INSERT INTO tenant (id, ad, status) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Lina Sanat Merkezi', 'AKTIF');
