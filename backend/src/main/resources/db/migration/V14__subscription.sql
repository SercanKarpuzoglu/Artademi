-- V14: Subscription (abonelik) — her tenant'a 1-1, PLATFORM-DUZEYI.
--
-- ⚠️ Tenant gibi TENANT FILTRESINE TABI DEGILDIR. Buradaki tenant_id bir FK'dir (hangi tenant'in
-- aboneligi), izolasyon kolonu DEGIL. Yalnizca SUPER_ADMIN / platform mantigi okur.
--
-- Grace period mantigi: donem (current_period_end) biter + odeme yoksa 14 gun TAM ERISIM
-- (status=ODEME_BEKLIYOR, tenant.status AKTIF kalir) -> grace de gecerse tenant ASKIDA'ya dusurulur.
-- Odeme entegrasyonu YOK; payment_status elle/uctan set edilir (iyzico/PayTR ayri faz).
--
-- status:  DENEME / AKTIF / ODEME_BEKLIYOR / ASKIDA / IPTAL
-- payment: BEKLIYOR / ODENDI / BASARISIZ

CREATE TABLE subscription (
    id                   UUID         PRIMARY KEY,
    tenant_id            UUID         NOT NULL UNIQUE REFERENCES tenant (id),
    plan                 VARCHAR(20)  NOT NULL DEFAULT 'AYLIK',
    status               VARCHAR(20)  NOT NULL DEFAULT 'AKTIF',
    current_period_start DATE         NOT NULL,
    current_period_end   DATE         NOT NULL,
    grace_ends_at        DATE,
    payment_status       VARCHAR(20)  NOT NULL DEFAULT 'ODENDI',
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_subscription_tenant ON subscription (tenant_id);

-- Seed: MEVCUT tum tenant'lara AKTIF + uzak donem (2030) + ODENDI abonelik ver; boylece dev'de
-- otomatik ASKIDA tetiklenmez. INSERT...SELECT: fresh test DB'de yalnizca Lina; dev'de Lina + Anka
-- + provisioning testlerinden kalan tenant'lar (hepsi FK-guvenli, gercekten var olanlar).
INSERT INTO subscription (
        id, tenant_id, plan, status,
        current_period_start, current_period_end, payment_status)
SELECT gen_random_uuid(), t.id, 'AYLIK', 'AKTIF',
       CURRENT_DATE, DATE '2030-01-01', 'ODENDI'
FROM tenant t;
