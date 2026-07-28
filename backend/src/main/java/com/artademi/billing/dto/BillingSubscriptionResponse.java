package com.artademi.billing.dto;

import com.artademi.platform.Subscription;
import com.artademi.platform.dto.SubscriptionResponse;

/**
 * Odeme sayfasinin gosterdigi abonelik ozeti: platform abonelik detayi + otomatik odeme bagi.
 * Saglayici referans kodlari DISARI SIZDIRILMAZ (yalnizca bagli olup olmadigi soylenir).
 */
public record BillingSubscriptionResponse(
        SubscriptionResponse subscription,
        boolean otomatikOdemeAktif,
        String provider) {

    public static BillingSubscriptionResponse from(Subscription s) {
        return new BillingSubscriptionResponse(
                SubscriptionResponse.from(s),
                s.getProviderSubscriptionRef() != null,
                s.getProvider());
    }
}
