package com.artademi.telafi.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Telafi hakki tanimlama.
 *
 * @param kaynakOturumId hakkin dogdugu devamsizlik. OPSIYONEL: kurum devamsizliga bagli
 *     olmadan da (tatil, kurum kaynakli iptal) hak tanimlayabilmelidir.
 * @param sonKullanmaTarihi NULL = suresiz
 */
public record TelafiVerRequest(
        @NotNull(message = "Öğrenci zorunludur") Long ogrenciId,
        Long kaynakOturumId,
        LocalDate sonKullanmaTarihi,
        @Size(max = 500) String aciklama) {
}
