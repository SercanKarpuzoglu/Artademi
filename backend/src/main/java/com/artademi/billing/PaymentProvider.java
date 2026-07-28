package com.artademi.billing;

import com.artademi.billing.dto.CheckoutCustomer;
import com.artademi.billing.dto.CheckoutResult;
import com.artademi.billing.dto.CheckoutSession;

/**
 * Odeme saglayici portu (subscription-billing skill kurali: saglayici SOYUTLANIR ki ileride
 * iyzico → PayTR gecisi servis katmanina dokunmadan yapilabilsin).
 *
 * <p>Kart verisi ASLA bize ugramaz: checkout saglayicinin barindirdigi formda tamamlanir,
 * kart saklama/tekrarlayan tahsilat saglayici tarafindadir (PCI yuku onlarda).
 */
public interface PaymentProvider {

    /** Saglayici adi ("iyzico") — subscription.provider kolonuna yazilir. */
    String name();

    /** Barindirilan abonelik checkout'u baslatir; token + form icerigi doner. */
    CheckoutSession startCheckout(CheckoutCustomer customer);

    /** Checkout sonucunu token ile saglayicidan dogrular (client verisine guvenilmez). */
    CheckoutResult fetchCheckoutResult(String token);
}
