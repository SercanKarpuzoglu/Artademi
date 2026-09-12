package com.artademi.enrollment.dto;

import com.artademi.enrollment.OdemePlani;
import jakarta.validation.constraints.NotNull;

/** Deneme dersi kaydini plana gecirme: AYLIK ya da DONEMLIK. */
public record PlanaGecirRequest(@NotNull(message = "Plan zorunludur") OdemePlani odemePlani) {
}
