-- V19: odeme/abonelik bildirim izi — PLATFORM-DUZEYI, tenant filtresine TABI DEGIL.
--
-- AMAC: gunluk scheduler her gun calisir; ayni uyariyi 14 gun boyunca her gun gondermemek icin
-- "bu bildirim bu abonelige, bu DONEM icin gonderildi mi?" sorusunu bu tablo cevaplar.
--
-- ⚠️ Idempotency anahtari UNIQUE(subscription_id, tip, donem_anahtari):
--    donem_anahtari = ilgili abonelik doneminin bitis tarihi (currentPeriodEnd). Boylece
--    SONRAKI donemde ayni uyari tekrar gonderilebilir, ama ayni donem icinde bir kez gider.

CREATE TABLE billing_notification
(
    id              UUID         PRIMARY KEY,
    subscription_id UUID         NOT NULL,
    tenant_id       UUID         NOT NULL,
    -- ODEME_BASARISIZ / GRACE_BASLADI / GRACE_BITIYOR / ASKIYA_ALINDI
    tip             VARCHAR(40)  NOT NULL,
    donem_anahtari  VARCHAR(40)  NOT NULL,
    -- Kime gonderildigi (denetim; virgullu liste). Alici bulunamadiysa NULL.
    alici           VARCHAR(400),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Mukerrer gonderimi DB seviyesinde imkansiz kilar (uygulama kontrolu yarissa bile).
CREATE UNIQUE INDEX uq_billing_notification
    ON billing_notification (subscription_id, tip, donem_anahtari);

CREATE INDEX idx_billing_notification_tenant ON billing_notification (tenant_id);
