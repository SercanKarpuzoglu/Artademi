-- V20: abonelik yasam dongusu tamamlayicilari — PLATFORM-DUZEYI.
--
-- 1) KENDI ISTEGIYLE IPTAL: kurum aboneligini kendi sonlandirabilmeli. Sozlesmemiz (mesafeli satis,
--    md. "Iptalin sonucu") iptalin ODENMIS DONEMIN SONUNDA gecerli olacagini taahhut ediyor —
--    bu yuzden aninda kesmiyoruz, "donem sonunda iptal et" bayragi tutuyoruz.
--
-- 2) MANUEL MUAFIYET: SUPER_ADMIN, odeme alinmamis olsa bile bir kurumu acik tutabilmeli
--    (demo, sozlesmeli musteri, telafi vb.). Muafiyet olmadan gunluk is, super admin'in elle
--    actigi kurumu ertesi gece TEKRAR askiya aliyordu — sessiz geri alma.

ALTER TABLE subscription
    -- Kullanici iptal etti; erisim donem sonuna kadar SURER, sonra IPTAL'e duser.
    ADD COLUMN cancel_at_period_end BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN canceled_at          TIMESTAMPTZ,
    -- SUPER_ADMIN muafiyeti: gunluk degerlendirme bu abonelige DOKUNMAZ.
    ADD COLUMN muaf_mi              BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN muafiyet_notu        VARCHAR(300);

COMMENT ON COLUMN subscription.cancel_at_period_end IS
    'Kurum iptal talebinde bulundu; donem sonunda IPTAL olur, o ana kadar erisim acik.';
COMMENT ON COLUMN subscription.muaf_mi IS
    'SUPER_ADMIN muafiyeti: odeme olmasa da askiya alma/grace uygulanmaz.';
