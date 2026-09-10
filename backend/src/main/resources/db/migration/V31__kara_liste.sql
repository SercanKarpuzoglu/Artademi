-- V31: Kara liste (Dalga B / 9 Eylul talepleri). Kurum sorunlu ogrenciyi isaretler; gruba yazarken
-- uyari cikar ve sebep gosterilir. Ogrenci silinmez, statusu degismez; yalnizca isaret + aciklama.
ALTER TABLE students
    ADD COLUMN kara_liste BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN kara_liste_aciklama TEXT,
    ADD COLUMN kara_liste_tarihi TIMESTAMPTZ,
    ADD COLUMN kara_liste_ekleyen VARCHAR(100);
