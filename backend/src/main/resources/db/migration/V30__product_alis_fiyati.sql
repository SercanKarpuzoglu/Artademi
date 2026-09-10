-- V30: Urune alis (maliyet) fiyati — Dalga A / 9 Eylul talepleri ("stok satis ekraninda alis rakamlari da olsun").
-- NULL = bilinmiyor. Kar marji SAKLANMAZ, ekranda satis - alis olarak hesaplanir.
ALTER TABLE product ADD COLUMN alis_fiyati NUMERIC(12,2);
