package com.artademi.belge;

import com.artademi.common.exception.NotFoundException;
import com.artademi.finance.Payment;
import com.artademi.finance.PaymentRepository;
import com.artademi.platform.TenantService;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tahsilat makbuzu (PDF) uretir.
 *
 * <p>Makbuz kurumun VELIYE verdigi belgedir; kurum adi ustte, tutar hem rakam hem YAZI ile
 * yazilir (Turk makbuz teamulu — rakam tahrif edilirse yazi ile celisir).
 *
 * <p>Makbuz numarasi ayri bir sayac DEGIL, tahsilat kaydinin id'sidir: ayri sayac tutmak
 * bosluk/mukerrer numara riski dogurur; id zaten tenant icinde benzersiz ve degismezdir.
 */
@Service
public class MakbuzService {

    private static final DateTimeFormatter TARIH =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", new Locale("tr", "TR"));

    private final PaymentRepository payments;
    private final TenantService tenantService;
    private final PdfFontlari fontlar;

    public MakbuzService(PaymentRepository payments, TenantService tenantService,
            PdfFontlari fontlar) {
        this.payments = payments;
        this.tenantService = tenantService;
        this.fontlar = fontlar;
    }

    /**
     * Tahsilat icin makbuz PDF'i uretir.
     *
     * @throws NotFoundException tahsilat yoksa VEYA baska tenant'a aitse (findScopedById)
     */
    @Transactional(readOnly = true)
    public byte[] uret(Long tahsilatId) {
        Payment p = payments.findScopedById(tahsilatId)
                .orElseThrow(() -> new NotFoundException("Tahsilat bulunamadı: " + tahsilatId));
        // currentName() sozlesmesi geregi null DONEBILIR (tenant kaydi yoksa). Belgeye "null"
        // basmak yerine kurum adini tamamen atlariz; makbuz no/tutar/ogrenci ile gecerli kalir.
        return ciz(p, tenantService.currentName());
    }

    /** Makbuz dosya adi — indirilen dosyanin taninabilir olmasi icin ogrenci adi ve tarih. */
    @Transactional(readOnly = true)
    public String dosyaAdi(Long tahsilatId) {
        Payment p = payments.findScopedById(tahsilatId)
                .orElseThrow(() -> new NotFoundException("Tahsilat bulunamadı: " + tahsilatId));
        String ad = (p.getOgrenci().getAd() + "_" + p.getOgrenci().getSoyad())
                .replaceAll("[^A-Za-zÇĞİÖŞÜçğıöşü0-9_]", "");
        return "makbuz_" + tahsilatId + "_" + ad + ".pdf";
    }

    private byte[] ciz(Payment p, String kurum) {
        // A5 DIKEY. Not: PageSize.A5.rotate() sayfaya 90° rotasyon bayragi koyar ve
        // okuyucular icerigi YAN cevirir — makbuz icin istenmeyen sonuc.
        Document belge = new Document(PageSize.A5, 36, 36, 32, 32);
        ByteArrayOutputStream cikti = new ByteArrayOutputStream();
        PdfWriter.getInstance(belge, cikti);
        belge.open();

        // --- Baslik: kurum adi + belge turu
        boolean kurumBiliniyor = kurum != null && !kurum.isBlank();
        if (kurumBiliniyor) {
            Paragraph baslik = new Paragraph(kurum, fontlar.kalin(15));
            baslik.setAlignment(Element.ALIGN_CENTER);
            belge.add(baslik);
        }

        Paragraph tur = new Paragraph("TAHSİLAT MAKBUZU", fontlar.kalin(11));
        tur.setAlignment(Element.ALIGN_CENTER);
        tur.setSpacingBefore(2f);
        tur.setSpacingAfter(14f);
        belge.add(tur);

        // --- Kunye: makbuz no + tarih
        PdfPTable kunye = new PdfPTable(2);
        kunye.setWidthPercentage(100);
        kunye.addCell(hucre("Makbuz No: " + p.getId(), fontlar.normal(9), Element.ALIGN_LEFT));
        kunye.addCell(hucre("Tarih: " + p.getOdemeTarihi().format(TARIH), fontlar.normal(9),
                Element.ALIGN_RIGHT));
        belge.add(kunye);

        // --- Bilgi satirlari
        PdfPTable bilgi = new PdfPTable(new float[] {1f, 2.4f});
        bilgi.setWidthPercentage(100);
        bilgi.setSpacingBefore(12f);
        satir(bilgi, "Öğrenci", p.getOgrenci().getAd() + " " + p.getOgrenci().getSoyad());
        if (p.getGrup() != null) {
            satir(bilgi, "Grup", p.getGrup().getAd());
        }
        satir(bilgi, "Ödeme Yöntemi", odemeYontemi(p));
        if (p.getAciklama() != null && !p.getAciklama().isBlank()) {
            satir(bilgi, "Açıklama", p.getAciklama());
        }
        belge.add(bilgi);

        // --- Tutar kutusu: rakam + yazi
        PdfPTable tutar = new PdfPTable(1);
        tutar.setWidthPercentage(100);
        tutar.setSpacingBefore(14f);

        PdfPCell rakam = new PdfPCell(new Phrase(paraFormatla(p.getTutar()), fontlar.kalin(17)));
        rakam.setHorizontalAlignment(Element.ALIGN_CENTER);
        rakam.setPaddingTop(7f);
        rakam.setPaddingBottom(4f);
        rakam.setBorderColor(new Color(120, 120, 120));
        tutar.addCell(rakam);

        PdfPCell yazi = new PdfPCell(
                new Phrase("YALNIZ " + TutarYaziya.makbuzSatiri(p.getTutar()), fontlar.normal(8.5f)));
        yazi.setHorizontalAlignment(Element.ALIGN_CENTER);
        yazi.setPaddingBottom(7f);
        yazi.setBorderColor(new Color(120, 120, 120));
        yazi.setBorderWidthTop(0);
        tutar.addCell(yazi);
        belge.add(tutar);

        // --- Imza alani
        Paragraph imza = new Paragraph("Teslim Alan / Kaşe - İmza", fontlar.normal(8.5f));
        imza.setAlignment(Element.ALIGN_RIGHT);
        imza.setSpacingBefore(34f);
        belge.add(imza);

        if (kurumBiliniyor) {
            Paragraph dipnot = new Paragraph(
                    "Bu belge " + kurum + " tarafından düzenlenmiştir.", fontlar.normal(7));
            dipnot.setAlignment(Element.ALIGN_CENTER);
            dipnot.setSpacingBefore(16f);
            belge.add(dipnot);
        }

        belge.close();
        return cikti.toByteArray();
    }

    private void satir(PdfPTable tablo, String etiket, String deger) {
        tablo.addCell(hucre(etiket, fontlar.normal(9.5f), Element.ALIGN_LEFT));
        tablo.addCell(hucre(deger, fontlar.kalin(9.5f), Element.ALIGN_LEFT));
    }

    private static PdfPCell hucre(String metin, org.openpdf.text.Font font, int hizalama) {
        PdfPCell h = new PdfPCell(new Phrase(metin, font));
        h.setBorder(org.openpdf.text.Rectangle.NO_BORDER);
        h.setHorizontalAlignment(hizalama);
        h.setPaddingBottom(4f);
        return h;
    }

    /** Tutari Turk formatinda yazar: 2.500,50 ₺ */
    private static String paraFormatla(BigDecimal tutar) {
        java.text.NumberFormat f = java.text.NumberFormat.getNumberInstance(new Locale("tr", "TR"));
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return f.format(tutar) + " ₺";
    }

    private static String odemeYontemi(Payment p) {
        return switch (p.getOdemeYontemi()) {
            case NAKIT -> "Nakit";
            case KART -> "Kredi/Banka Kartı";
            case HAVALE -> "Havale / EFT";
        };
    }
}
