package com.artademi.belge;

import com.artademi.common.exception.NotFoundException;
import com.artademi.platform.TenantService;
import com.artademi.student.Student;
import com.artademi.student.StudentRepository;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ogrenci kayit formu (PDF) — kurumun veliye imzalattigi belge.
 *
 * <p>Bos alanlar da BASILIR (ornegin telefon girilmemisse cizgi olarak): form basilip elle
 * doldurulabilsin. Bu yuzden alanlar "veri varsa goster" mantigiyla atlanmaz.
 *
 * <p>Veli bolumu yalnizca ogrenci YETISKIN DEGILSE basilir — yetiskin ogrencide veli alanlari
 * anlamsizdir ve formu gereksiz uzatir.
 */
@Service
public class KayitFormuService {

    private static final DateTimeFormatter TARIH =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", new Locale("tr", "TR"));

    /** Doldurulmamis alan icin: bos birakmak yerine cizgi — form elle doldurulabilsin. */
    private static final String BOS = "……………………………………";

    private final StudentRepository students;
    private final TenantService tenantService;
    private final PdfFontlari fontlar;

    public KayitFormuService(StudentRepository students, TenantService tenantService,
            PdfFontlari fontlar) {
        this.students = students;
        this.tenantService = tenantService;
        this.fontlar = fontlar;
    }

    /**
     * @throws NotFoundException ogrenci yoksa VEYA baska tenant'a aitse
     */
    @Transactional(readOnly = true)
    public byte[] uret(Long ogrenciId) {
        Student o = students.findScopedById(ogrenciId)
                .orElseThrow(() -> new NotFoundException("Öğrenci bulunamadı: " + ogrenciId));
        return ciz(o, tenantService.currentName());
    }

    @Transactional(readOnly = true)
    public String dosyaAdi(Long ogrenciId) {
        Student o = students.findScopedById(ogrenciId)
                .orElseThrow(() -> new NotFoundException("Öğrenci bulunamadı: " + ogrenciId));
        String ad = (o.getAd() + "_" + o.getSoyad())
                .replaceAll("[^A-Za-zÇĞİÖŞÜçğıöşü0-9_]", "");
        return "kayit_formu_" + ad + ".pdf";
    }

    private byte[] ciz(Student o, String kurum) {
        Document belge = new Document(PageSize.A4, 46, 46, 40, 40);
        ByteArrayOutputStream cikti = new ByteArrayOutputStream();
        PdfWriter.getInstance(belge, cikti);
        belge.open();

        boolean kurumBiliniyor = kurum != null && !kurum.isBlank();
        if (kurumBiliniyor) {
            Paragraph baslik = new Paragraph(kurum, fontlar.kalin(16));
            baslik.setAlignment(Element.ALIGN_CENTER);
            belge.add(baslik);
        }

        Paragraph tur = new Paragraph("ÖĞRENCİ KAYIT FORMU", fontlar.kalin(12));
        tur.setAlignment(Element.ALIGN_CENTER);
        tur.setSpacingBefore(3f);
        tur.setSpacingAfter(18f);
        belge.add(tur);

        belge.add(bolumBasligi("Öğrenci Bilgileri"));
        PdfPTable ogr = tablo();
        satir(ogr, "Adı Soyadı", o.getAd() + " " + o.getSoyad());
        satir(ogr, "T.C. Kimlik No", o.getTcKimlikNo());
        satir(ogr, "Doğum Tarihi", o.getDogumTarihi() == null ? BOS
                : o.getDogumTarihi().format(TARIH));
        satir(ogr, "Telefon", deger(o.getTelefon()));
        satir(ogr, "Ev Adresi", deger(o.getEvAdresi()));
        belge.add(ogr);

        if (!o.isYetiskinMi()) {
            belge.add(bolumBasligi("Veli Bilgileri"));
            PdfPTable veli = tablo();
            satir(veli, "Anne Adı", deger(o.getAnneAd()));
            satir(veli, "Anne T.C. No", deger(o.getAnneTcKimlikNo()));
            satir(veli, "Anne Telefon", deger(o.getAnneTelefon()));
            satir(veli, "Baba Adı", deger(o.getBabaAd()));
            satir(veli, "Baba T.C. No", deger(o.getBabaTcKimlikNo()));
            satir(veli, "Baba Telefon", deger(o.getBabaTelefon()));
            satir(veli, "Veli Mesleği", deger(o.getVeliMeslek()));
            satir(veli, "Veli E-posta", deger(o.getVeliMail()));
            belge.add(veli);
        }

        // --- Onay + imza
        Paragraph onay = new Paragraph(
                "Yukarıdaki bilgilerin doğru olduğunu, kurumun işleyiş ve ödeme koşullarını "
                        + "kabul ettiğimi beyan ederim. Kişisel verilerimin 6698 sayılı KVKK "
                        + "kapsamında işlenmesine onay veriyorum.",
                fontlar.normal(8.5f));
        onay.setSpacingBefore(22f);
        onay.setAlignment(Element.ALIGN_JUSTIFIED);
        belge.add(onay);

        PdfPTable imza = new PdfPTable(2);
        imza.setWidthPercentage(100);
        imza.setSpacingBefore(28f);
        imza.addCell(imzaHucresi("Tarih: " + LocalDate.now().format(TARIH)));
        imza.addCell(imzaHucresi(o.isYetiskinMi() ? "Öğrenci Adı - İmza" : "Veli Adı - İmza"));
        belge.add(imza);

        belge.close();
        return cikti.toByteArray();
    }

    private Paragraph bolumBasligi(String metin) {
        Paragraph p = new Paragraph(metin, fontlar.kalin(10.5f));
        p.setSpacingBefore(14f);
        p.setSpacingAfter(6f);
        return p;
    }

    private static PdfPTable tablo() {
        PdfPTable t = new PdfPTable(new float[] {1f, 2.2f});
        t.setWidthPercentage(100);
        return t;
    }

    private void satir(PdfPTable tablo, String etiket, String deger) {
        tablo.addCell(hucre(etiket, fontlar.normal(9.5f)));
        tablo.addCell(hucre(deger, fontlar.kalin(9.5f)));
    }

    private static PdfPCell hucre(String metin, org.openpdf.text.Font font) {
        PdfPCell h = new PdfPCell(new Phrase(metin, font));
        h.setBorder(Rectangle.NO_BORDER);
        h.setPaddingBottom(5f);
        return h;
    }

    private PdfPCell imzaHucresi(String metin) {
        PdfPCell h = new PdfPCell(new Phrase(metin, fontlar.normal(9)));
        h.setBorder(Rectangle.TOP);
        h.setBorderColor(new java.awt.Color(140, 140, 140));
        h.setPaddingTop(6f);
        h.setHorizontalAlignment(Element.ALIGN_CENTER);
        return h;
    }

    /** Bos/eksik alan -> cizgi (form elle doldurulabilsin). */
    private static String deger(String v) {
        return v == null || v.isBlank() ? BOS : v;
    }
}
