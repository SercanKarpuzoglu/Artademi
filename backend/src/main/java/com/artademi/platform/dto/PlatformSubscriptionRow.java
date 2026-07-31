package com.artademi.platform.dto;

import com.artademi.platform.PaymentStatus;
import com.artademi.platform.Plan;
import com.artademi.platform.SubscriptionStatus;
import com.artademi.platform.TenantStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Odeme takibi satiri: bir kurumun abonelik + odeme durumu tek bakista.
 *
 * @param otomatikOdeme saglayiciya bagli mi (false = manuel takip gerekir)
 */
public record PlatformSubscriptionRow(
        UUID tenantId,
        String ad,
        TenantStatus tenantStatus,
        Plan plan,
        SubscriptionStatus abonelikStatus,
        PaymentStatus odemeStatus,
        LocalDate currentPeriodStart,
        LocalDate currentPeriodEnd,
        LocalDate graceEndsAt,
        boolean otomatikOdeme) {
}
