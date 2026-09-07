package com.artademi.bildirim.dto;

import com.artademi.bildirim.BildirimAyari;

/**
 * Kurumun bildirim tercihleri.
 *
 * @param haftalikOzetGunu ISO-8601: 1=Pazartesi … 7=Pazar
 */
public record BildirimAyariResponse(
        boolean borcHatirlatmaOtomatik,
        boolean devamsizlikBildirimi,
        boolean haftalikOzet,
        short haftalikOzetGunu) {

    public static BildirimAyariResponse from(BildirimAyari a) {
        return new BildirimAyariResponse(a.isBorcHatirlatmaOtomatik(), a.isDevamsizlikBildirimi(),
                a.isHaftalikOzet(), a.getHaftalikOzetGunu());
    }
}
