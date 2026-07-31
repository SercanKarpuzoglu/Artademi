-- V18: platform denetim izi (audit) — PLATFORM-DUZEYI, tenant filtresine TABI DEGIL.
--
-- Amac: "kim, ne zaman, hangi kuruma ne yapti" sorusunun cevabi. Kurum acma/askiya alma/silme,
-- kullanici ekleme/silme, abonelik/odeme mudahalesi gibi geri donusu olan islemler kaydedilir.
--
-- ⚠️ target_ad bir SNAPSHOT'tur (FK degil): kurum silinse/adi degisse bile iz okunabilir kalmali.
--    Denetim kaydi asla guncellenmez/silinmez — yalnizca INSERT ve SELECT.

CREATE TABLE platform_audit
(
    id           UUID         PRIMARY KEY,
    -- Islemi yapan (JWT preferred_username); sistem tetikliyorsa 'sistem'.
    actor        VARCHAR(120) NOT NULL,
    -- Islem tipi (uygulama enum'u; DB'de metin: yeni tip eklemek migration gerektirmesin).
    action       VARCHAR(40)  NOT NULL,
    -- Etkilenen kurum (varsa). FK YOK: kurum kaydi degisse bile iz kalir.
    target_tenant_id UUID,
    -- Kurum/kullanici adinin o ANDAKI hali (silinse bile okunabilir olsun diye).
    target_ad    VARCHAR(200),
    -- Insan tarafindan okunur ozet: "AKTIF → ASKIDA", "kullanici eklendi: ayse.k" vb.
    detail       VARCHAR(500),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Konsol listesi her zaman "en yeni once" okur.
CREATE INDEX idx_platform_audit_created_at ON platform_audit (created_at DESC);
-- Kurum detayindan o kuruma ait izi cekmek icin.
CREATE INDEX idx_platform_audit_tenant ON platform_audit (target_tenant_id);
