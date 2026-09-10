-- V36: Bransa varsayilan donem (9 Eylul talebi: "brans ve gruba atama tanimlama ekraninda donem
-- belirleme"). Kredi hesabi GRUBUN donemine bakmaya devam eder; brans donemi yalnizca yeni grup
-- acilirken on-doldurma icindir (grupta degistirilebilir).
ALTER TABLE branches ADD COLUMN donem_id BIGINT REFERENCES donem (id);
