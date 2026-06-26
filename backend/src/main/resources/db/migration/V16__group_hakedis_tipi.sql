-- V16: Grup hakedis tipi (lesson_group.hakedis_tipi) — Model C kalbi. Hakedis tipi GRUBA baglidir;
-- her grup tam olarak BIR tip ile odenir (cifte sayim imkansiz). Ogretmenin ilgili teacher_hakedis
-- (V15) oranı uygulanir.
--
-- Backfill: mevcut gruplar grup tipinden varsayilan tip alir (GRUP->SAATLIK, OZEL->OZEL_DERS).
-- Sonra NOT NULL.

ALTER TABLE lesson_group ADD COLUMN hakedis_tipi VARCHAR(20);

UPDATE lesson_group
SET hakedis_tipi = CASE
    WHEN tip = 'GRUP' THEN 'SAATLIK'
    WHEN tip = 'OZEL' THEN 'OZEL_DERS'
END;

ALTER TABLE lesson_group ALTER COLUMN hakedis_tipi SET NOT NULL;

-- Payout mukerrer engeli artik TIP bazinda: ayni ogretmen + donem icin her hakedis tipinden tek
-- payout satiri olabilir (Model C'de bir ogretmen ayni donemde birden cok tipte hakedis alabilir).
-- V11'deki (tenant_id, ogretmen_id, donem) kisitini birak, (tenant_id, ogretmen_id, donem,
-- hakedis_tipi) ile yeniden kur.
ALTER TABLE payout DROP CONSTRAINT uq_payout_tenant_ogretmen_donem;
ALTER TABLE payout ADD CONSTRAINT uq_payout_tenant_ogretmen_donem_tip
    UNIQUE (tenant_id, ogretmen_id, donem, hakedis_tipi);
