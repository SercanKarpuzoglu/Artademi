package com.artademi.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * TURK EXCEL'de sorunsuz acilan CSV uretir.
 *
 * <p>Iki detay onemli, ikisi de gercek kullanimda cikar:
 * <ul>
 *   <li><b>UTF-8 BOM</b> — olmadan Excel Turkce karakterleri bozar (ç, ğ, ş, ı → mojibake).</li>
 *   <li><b>Noktali virgul ayraci</b> — Turkce yerelde virgul ONDALIK ayracidir; virgullu CSV
 *       Excel'de tek sutuna yapisir. {@code ;} ile sutunlar dogru ayrilir.</li>
 * </ul>
 */
final class CsvYazici {

    private static final char AYRAC = ';';
    private static final String SATIR_SONU = "\r\n"; // Excel uyumu

    private final StringBuilder sb = new StringBuilder();

    void satir(Object... hucreler) {
        for (int i = 0; i < hucreler.length; i++) {
            if (i > 0) {
                sb.append(AYRAC);
            }
            sb.append(kacisla(hucreler[i]));
        }
        sb.append(SATIR_SONU);
    }

    /** BOM + icerik; dogrudan ZIP girisine yazilabilir. */
    byte[] baytlar() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xEF);
        out.write(0xBB);
        out.write(0xBF); // UTF-8 BOM
        Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        w.write(sb.toString());
        w.flush();
        return out.toByteArray();
    }

    /**
     * Hucreyi CSV kurallarina gore kacisla. Tirnak, ayrac veya satir sonu iceren deger
     * tirnaklanir; icerideki tirnak ikilenir. Aksi halde tek bir adres alani tabloyu bozar.
     */
    private static String kacisla(Object deger) {
        if (deger == null) {
            return "";
        }
        String s = bicimle(deger);
        boolean tirnakGerek = s.indexOf(AYRAC) >= 0 || s.indexOf('"') >= 0
                || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        if (!tirnakGerek) {
            return s;
        }
        return '"' + s.replace("\"", "\"\"") + '"';
    }

    /** Tarih/saat degerleri Excel'in anlayacagi sade bicimde; enum ve liste okunur halde. */
    private static String bicimle(Object deger) {
        if (deger instanceof LocalDate d) {
            return d.toString();                       // 2026-08-29
        }
        if (deger instanceof LocalDateTime dt) {
            return dt.toString().replace('T', ' ');
        }
        if (deger instanceof LocalTime t) {
            return t.toString();
        }
        if (deger instanceof Enum<?> e) {
            return e.name();
        }
        if (deger instanceof List<?> l) {
            return String.join(", ", l.stream().map(String::valueOf).toList());
        }
        return String.valueOf(deger);
    }
}
