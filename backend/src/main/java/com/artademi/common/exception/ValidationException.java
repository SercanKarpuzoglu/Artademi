package com.artademi.common.exception;

import java.util.Map;

/**
 * Is kurali dogrulama hatasi (or. uygun olmayan statudeki ogrenci gruba yazilamaz).
 * Bean Validation disindaki, servis katmaninda enforce edilen kosullar icindir.
 * GlobalExceptionHandler bunu 400 / VALIDATION_ERROR'a cevirir.
 *
 * <p><b>Alan bazli hata ({@code error.fields}).</b> Bean Validation hatalari zaten alan adiyla
 * doner ve web formlari bunu {@code setError(alan, ...)} ile inputun altina yazar. Servis
 * katmanindan atilan hatalar ise uzun sure yalnizca {@code message} tasiyordu; kullanici hangi
 * alani duzeltecegini formdan goremiyor, hata yalnizca form ustundeki kutuda cikiyordu. Bir
 * kurala ait alan adi BELLIYSE {@link #alan(String, String)} ile atin — sozlesme (api-contract)
 * degismez, yalnizca {@code fields} dolar.
 *
 * <p>Alan adi <b>istek DTO'sundaki alan adiyla ayni</b> olmali (web formu anahtari o isimle
 * eslestirir). Alanla iliskilendirilemeyen kurallar (or. "kendi hesabinizi silemezsiniz")
 * {@code fields} tasimadan atilir — uydurma bir alan adi formda yanlis inputu isaretler.
 */
public class ValidationException extends RuntimeException {

    /** Alan adi -> mesaj; alan bilgisi yoksa {@code null}. */
    private final transient Map<String, String> fields;

    public ValidationException(String message) {
        this(message, null);
    }

    public ValidationException(String message, Map<String, String> fields) {
        super(message);
        this.fields = (fields == null || fields.isEmpty()) ? null : Map.copyOf(fields);
    }

    /** Tek alana bagli kural: mesaj hem govdede hem {@code error.fields[alan]} icinde doner. */
    public static ValidationException alan(String alan, String mesaj) {
        return new ValidationException(mesaj, Map.of(alan, mesaj));
    }

    /** Alan bazli hatalar; yoksa {@code null} (ApiError.fields de null kalir). */
    public Map<String, String> getFields() {
        return fields;
    }
}
