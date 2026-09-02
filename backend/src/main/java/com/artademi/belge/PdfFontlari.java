package com.artademi.belge;

import org.openpdf.text.Font;
import org.openpdf.text.pdf.BaseFont;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * PDF fontlari — TURKCE ICIN KRITIK.
 *
 * <p>PDF'in yerlesik (base-14) fontlari WinAnsi/Latin-1 kodlar; bu kumede
 * <b>ş, ğ, İ, ı yoktur</b>. Gomulu font kullanilmazsa makbuzda bu harfler bozuk cikar ya da
 * okuyucunun yerine koydugu fonta kalir. Bu yuzden DejaVu Sans {@code IDENTITY_H} (Unicode)
 * kodlamasiyla ve GOMULU olarak yuklenir; boylece belge her okuyucuda/yazicida ayni gorunur.
 *
 * <p>Font dosyasi {@code src/main/resources/fonts} altindadir (lisans: DEJAVU-LICENSE.txt,
 * gomme ve dagitima izinli). OpenPDF yalnizca KULLANILAN glifleri gomer, bu yuzden uretilen
 * PDF ~30-50 KB kalir.
 *
 * <p>Fontlar bir kez yuklenir ve paylasilir: {@link BaseFont} olusturmak pahalidir ve her
 * makbuzda yeniden yuklemek gereksiz IO'dur.
 */
@Component
public class PdfFontlari {

    private static final String NORMAL_YOL = "fonts/DejaVuSans.ttf";
    private static final String KALIN_YOL = "fonts/DejaVuSans-Bold.ttf";

    private final BaseFont normal;
    private final BaseFont kalin;

    public PdfFontlari() {
        this.normal = yukle(NORMAL_YOL);
        this.kalin = yukle(KALIN_YOL);
    }

    /** Verilen punto ve renkte normal font. */
    public Font normal(float punto) {
        return new Font(normal, punto);
    }

    /** Verilen puntoda kalin font (baslik/tutar icin). */
    public Font kalin(float punto) {
        return new Font(kalin, punto);
    }

    /**
     * Font dosyasini classpath'ten okuyup gomulu BaseFont uretir.
     *
     * <p>Bayt dizisinden yukleriz: JAR icindeki kaynak bir dosya yolu DEGILDIR, dolayisiyla
     * {@code createFont(path, ...)} paketlenmis uygulamada bulunamaz.
     */
    private static BaseFont yukle(String yol) {
        try (InputStream in = new ClassPathResource(yol).getInputStream()) {
            byte[] baytlar = StreamUtils.copyToByteArray(in);
            return BaseFont.createFont(yol, BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                    true, baytlar, null);
        } catch (IOException e) {
            // Font yoksa makbuz Turkce karakterleri bozuk basar; sessizce devam etmek yerine
            // uygulama acilisinda patlamak DOGRUDUR (hata konfigurasyondadir, veride degil).
            throw new IllegalStateException("PDF fontu yüklenemedi: " + yol, e);
        }
    }
}
