-- V17: iyzico odeme entegrasyonu (billing) — PLATFORM-DUZEYI.
--
-- 1) subscription'a saglayici baglari: hangi PSP (provider), iyzico musteri/abonelik referanslari
--    ve devam eden checkout'un token'i. Kart verisi BIZDE TUTULMAZ (PCI saglayicida).
-- 2) billing_event: webhook idempotency + denetim izi. iyzico ayni bildirimi 15 dk arayla 3 kez
--    tekrarlayabilir; UNIQUE(provider, dedup_key) ile cift isleme onlenir.
--
-- Tenant filtresine TABI DEGILDIR (V13/V14 gibi platform tablolari).

ALTER TABLE subscription
    ADD COLUMN provider                  VARCHAR(20),
    ADD COLUMN provider_customer_ref     VARCHAR(80),
    ADD COLUMN provider_subscription_ref VARCHAR(80),
    ADD COLUMN checkout_token            VARCHAR(80);

-- Webhook'lar subscriptionReferenceCode ile eslesir; hizli ve tekil olmali (NULL'lar serbest).
CREATE UNIQUE INDEX uq_subscription_provider_ref
    ON subscription (provider_subscription_ref)
    WHERE provider_subscription_ref IS NOT NULL;

CREATE UNIQUE INDEX uq_subscription_checkout_token
    ON subscription (checkout_token)
    WHERE checkout_token IS NOT NULL;

CREATE TABLE billing_event (
    id              UUID         PRIMARY KEY,
    provider        VARCHAR(20)  NOT NULL,
    event_type      VARCHAR(60)  NOT NULL,
    -- iyzico: eventType + orderReferenceCode (bir tahsilat denemesi basina tek success/failure)
    dedup_key       VARCHAR(160) NOT NULL,
    subscription_id UUID         REFERENCES subscription (id),
    tenant_id       UUID,
    payload         TEXT         NOT NULL,
    -- PROCESSED: abonelige uygulandi / IGNORED: eslesmedi ya da mukerrer
    status          VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_billing_event_dedup UNIQUE (provider, dedup_key)
);

CREATE INDEX idx_billing_event_subscription ON billing_event (subscription_id);
