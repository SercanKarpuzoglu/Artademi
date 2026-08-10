-- V21: KURUM ICI islem kaydi (tenant denetim izi) — TENANT-DUZEYI, tenant filtresine TABIDIR.
--
-- Platform denetiminden (V18 platform_audit) FARKI: orasi SUPER_ADMIN'in kurumlar uzerindeki
-- islemlerini tutar; burasi bir KURUMUN KENDI ekibinin islemlerini tutar ve yalnizca o kurumun
-- yoneticisi gorur (tenant_id filtresi).
--
-- Kayit YONTEMI: her basarili degistirici HTTP istegi (POST/PUT/PATCH/DELETE) bir interceptor
-- tarafindan yazilir. Servisleri tek tek isaretlemek yerine bu yol secildi — boylece yeni bir
-- modul eklendiginde "iz birakmayi unutma" riski YOKTUR; kapsama kendiliginden tamdir.

CREATE TABLE tenant_audit
(
    id          UUID         PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    -- Islemi yapan (JWT preferred_username) + o anki adi (kullanici silinse de okunur kalsin).
    actor       VARCHAR(120) NOT NULL,
    actor_ad    VARCHAR(200),
    -- Insan tarafindan okunur eylem: "Öğrenci eklendi", "Yoklama kaydedildi" vb.
    eylem       VARCHAR(120) NOT NULL,
    -- Teknik iz (destek/hata ayiklama icin): HTTP metodu ve yol.
    metot       VARCHAR(10)  NOT NULL,
    yol         VARCHAR(300) NOT NULL,
    -- Yoldan cikarilan kayit kimligi (varsa) — "hangi ogrenci" sorusunu cevaplar.
    kayit_id    VARCHAR(80),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Liste her zaman "en yeni once" ve tenant bazinda okunur.
CREATE INDEX idx_tenant_audit_tenant_created ON tenant_audit (tenant_id, created_at DESC);
