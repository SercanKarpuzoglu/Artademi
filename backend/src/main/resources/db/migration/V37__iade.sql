-- V37: Iade (para geri verme). Urun karari 2026-09-20.
--
-- IPTAL ile IADE ayri seylerdir: iptal = "bu kayit yanlis girildi" (yumusak silme, V32) ve satiri
-- gizler; iade = "para alindi VE geri verildi" — ikisi de gercek, ikisi de defterde kalmali.
-- Iadeyi silmeyle yapmak kasayi da yanlislardi (para fiilen cikti ama biz giris satirini sildik).
--
-- Bu yuzden iade YENI BIR SATIRDIR ve tutari NEGATIFTIR. Bakiye, kasa bakiyesi ve Gelirler ozeti
-- ucu de SUM() ile calistigi icin negatif satir ucunu birden kendiliginden duzeltir; tek bir toplam
-- sorgusu degismek zorunda kalmaz. (Ayri bir iade tablosu, o sorgularin HEPSINE "- iade" eklemeyi
-- gerektirirdi; unutulan bir yer sessizce yanlis para gosterirdi.)
--
-- ⚠️ payment.tutar ve sale.adet/toplam_tutar artik ISARETLI. DB'de CHECK yok; pozitiflik yalnizca
-- yeni tahsilat/satis DTO'sunda (@Positive) zorlanir, iade yolu ondan gecmez.
ALTER TABLE payment ADD COLUMN iade_edilen_odeme_id BIGINT REFERENCES payment (id);
ALTER TABLE sale    ADD COLUMN iade_edilen_satis_id BIGINT REFERENCES sale (id);

-- "Bu kaydin iadesi var mi / toplam ne kadar iade edildi" her iade ve her silme on-kontrolunde sorulur.
CREATE INDEX idx_payment_iade_edilen ON payment (iade_edilen_odeme_id) WHERE iade_edilen_odeme_id IS NOT NULL;
CREATE INDEX idx_sale_iade_edilen    ON sale    (iade_edilen_satis_id) WHERE iade_edilen_satis_id IS NOT NULL;

-- Satis geliri bugune kadar HICBIR kasanin bakiyesine girmiyordu (tahsilat ve giderin kasa_id'si
-- vardi, satisin yoktu): urun satisi Gelirler'de gorunup kasada gorunmuyordu. Iade parayi kasadan
-- cikaracagi icin bu eksik artik kapatilmali. Eski satislarda NULL kalir -> bakiye degismez.
ALTER TABLE sale ADD COLUMN kasa_id BIGINT REFERENCES kasa (id);
CREATE INDEX idx_sale_kasa ON sale (kasa_id) WHERE kasa_id IS NOT NULL;
