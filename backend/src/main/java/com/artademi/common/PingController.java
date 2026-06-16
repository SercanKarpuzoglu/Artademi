package com.artademi.common;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gecici saglik/zarf kontrolu: API zarfini uctan uca gormek icin.
 * Auth henuz yok, acik kalir. Sonraki adimlarda kaldirilabilir.
 */
@RestController
@RequestMapping("/api")
public class PingController {

    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.ok(Map.of("message", "pong"));
    }
}
