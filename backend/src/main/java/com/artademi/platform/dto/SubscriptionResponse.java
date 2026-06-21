package com.artademi.platform.dto;

import com.artademi.platform.PaymentStatus;
import com.artademi.platform.Plan;
import com.artademi.platform.Subscription;
import com.artademi.platform.SubscriptionStatus;
import java.time.LocalDate;

/** Abonelik ozeti/detayi (platform). Tenant listesinde ve subscription ucunda doner. */
public record SubscriptionResponse(
        SubscriptionStatus status,
        Plan plan,
        LocalDate currentPeriodStart,
        LocalDate currentPeriodEnd,
        LocalDate graceEndsAt,
        PaymentStatus paymentStatus) {

    public static SubscriptionResponse from(Subscription s) {
        return new SubscriptionResponse(
                s.getStatus(),
                s.getPlan(),
                s.getCurrentPeriodStart(),
                s.getCurrentPeriodEnd(),
                s.getGraceEndsAt(),
                s.getPaymentStatus());
    }
}
