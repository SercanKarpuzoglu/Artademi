package com.artademi.payout;

/**
 * Hakedis (payout) kaydinin durumu.
 *
 * <ul>
 *   <li>{@link #HESAPLANDI} — hesaplandi, henuz odenmedi (varsayilan baslangic).</li>
 *   <li>{@link #ODENDI} — ogretmene odendi ({@code odemeTarihi} dolar).</li>
 * </ul>
 */
public enum PayoutDurumu {
    HESAPLANDI,
    ODENDI
}
