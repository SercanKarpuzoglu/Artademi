-- V28: Telafi dersi hakki.
--
-- Ogrenci derse gelmediginde kurum telafi hakki TANIYABILIR; bu hak sonradan bir derste
-- kullanilir ve iz birakir.
--
-- ⚠️ HAK OTOMATIK DOGMAZ. Her GELMEDI kaydindan otomatik telafi hakki uretilseydi liste
-- kullanilamaz hale gelirdi (bir donemde yuzlerce devamsizlik olur) ve kurumun "haber
-- verdiyse telafi veririm" gibi kendi kurali ezilirdi. Hakki KURUM verir.
--
-- ⚠️ SURE DOLMASI SAKLANMAZ, HESAPLANIR. "SURESI_DOLDU" diye bir durum tutulsaydi onu
-- her gece guncelleyen ayri bir job gerekirdi; job kacarsa durum yalan soylerdi.
-- Bunun yerine son_kullanma_tarihi tutulur, "doldu mu" sorusu okuma aninda cevaplanir.

CREATE TABLE telafi_hakki (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id            UUID        NOT NULL,
    ogrenci_id           BIGINT      NOT NULL REFERENCES students (id),

    -- Hakkin dogdugu devamsizlik. NULLABLE: kurum devamsizliga bagli olmadan da
    -- (ornegin tatil, kurum kaynakli iptal) telafi tanimlayabilmelidir.
    kaynak_oturum_id     BIGINT      REFERENCES attendance_session (id),

    verilme_tarihi       DATE        NOT NULL,
    -- NULL = suresiz. Sure koymak zorunlu degildir ama koymayan kurumda haklar birikir.
    son_kullanma_tarihi  DATE,

    -- BEKLIYOR | KULLANILDI | IPTAL   (SURESI_DOLDU YOK — hesaplanir, bkz. yukarisi)
    durum                VARCHAR(20) NOT NULL DEFAULT 'BEKLIYOR',

    -- Hak hangi derste kullanildi (kanit). KULLANILDI disinda NULL.
    kullanilan_oturum_id BIGINT      REFERENCES attendance_session (id),
    kullanim_tarihi      DATE,

    aciklama             VARCHAR(500),
    olusturulma_tarihi   TIMESTAMPTZ NOT NULL,
    guncellenme_tarihi   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_telafi_tenant ON telafi_hakki (tenant_id);
CREATE INDEX idx_telafi_tenant_durum ON telafi_hakki (tenant_id, durum);
CREATE INDEX idx_telafi_tenant_ogrenci ON telafi_hakki (tenant_id, ogrenci_id);

-- Ayni devamsizliktan IKI kez telafi hakki verilemez. Kismi indeks: kaynak_oturum_id NULL
-- olan (elle tanimlanan) haklar bu kisittan muaftir, aksi halde kurum ikinci bir elle
-- hak tanimlayamazdi.
CREATE UNIQUE INDEX uq_telafi_ogrenci_kaynak ON telafi_hakki (tenant_id, ogrenci_id, kaynak_oturum_id)
    WHERE kaynak_oturum_id IS NOT NULL;

-- ⚠️ FK'ler ayni tenant'i GARANTI ETMEZ; atamalar serviste findScopedById ile dogrulanir.
