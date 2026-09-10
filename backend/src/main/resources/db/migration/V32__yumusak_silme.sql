-- V32: Yumusak silme (Dalga B-3, urun karari 2026-09-10). Yonetici "Sil" der; satir SILINMEZ, silindi_tarihi
-- damgalanir ve Hibernate @SQLRestriction ile tum sorgulardan gizlenir. Para izi ve Islem Kaydi bozulmaz;
-- silinen kayit /api/silme/silinenler'den geri alinabilir. Bire-bir referanslar (odeme -> ogrenci) yuklenmeye
-- devam eder; sadece listelerde/aramalarda gorunmez.
ALTER TABLE students           ADD COLUMN silindi_tarihi TIMESTAMPTZ, ADD COLUMN silen VARCHAR(100);
ALTER TABLE lesson_group       ADD COLUMN silindi_tarihi TIMESTAMPTZ, ADD COLUMN silen VARCHAR(100);
ALTER TABLE teachers           ADD COLUMN silindi_tarihi TIMESTAMPTZ, ADD COLUMN silen VARCHAR(100);
ALTER TABLE rooms              ADD COLUMN silindi_tarihi TIMESTAMPTZ, ADD COLUMN silen VARCHAR(100);
ALTER TABLE branches           ADD COLUMN silindi_tarihi TIMESTAMPTZ, ADD COLUMN silen VARCHAR(100);
ALTER TABLE sube               ADD COLUMN silindi_tarihi TIMESTAMPTZ, ADD COLUMN silen VARCHAR(100);
ALTER TABLE product            ADD COLUMN silindi_tarihi TIMESTAMPTZ, ADD COLUMN silen VARCHAR(100);
ALTER TABLE tedarikci          ADD COLUMN silindi_tarihi TIMESTAMPTZ, ADD COLUMN silen VARCHAR(100);
ALTER TABLE kasa               ADD COLUMN silindi_tarihi TIMESTAMPTZ, ADD COLUMN silen VARCHAR(100);
ALTER TABLE accrual            ADD COLUMN silindi_tarihi TIMESTAMPTZ, ADD COLUMN silen VARCHAR(100);
ALTER TABLE payment            ADD COLUMN silindi_tarihi TIMESTAMPTZ, ADD COLUMN silen VARCHAR(100);
ALTER TABLE expense            ADD COLUMN silindi_tarihi TIMESTAMPTZ, ADD COLUMN silen VARCHAR(100);
ALTER TABLE sale               ADD COLUMN silindi_tarihi TIMESTAMPTZ, ADD COLUMN silen VARCHAR(100);
ALTER TABLE ders_paketi        ADD COLUMN silindi_tarihi TIMESTAMPTZ, ADD COLUMN silen VARCHAR(100);
ALTER TABLE telafi_hakki       ADD COLUMN silindi_tarihi TIMESTAMPTZ, ADD COLUMN silen VARCHAR(100);
ALTER TABLE basvuru            ADD COLUMN silindi_tarihi TIMESTAMPTZ, ADD COLUMN silen VARCHAR(100);
ALTER TABLE schedule           ADD COLUMN silindi_tarihi TIMESTAMPTZ, ADD COLUMN silen VARCHAR(100);
ALTER TABLE attendance_session ADD COLUMN silindi_tarihi TIMESTAMPTZ, ADD COLUMN silen VARCHAR(100);
ALTER TABLE attendance_entry   ADD COLUMN silindi_tarihi TIMESTAMPTZ, ADD COLUMN silen VARCHAR(100);
