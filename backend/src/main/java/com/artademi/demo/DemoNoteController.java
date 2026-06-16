package com.artademi.demo;

import com.artademi.common.ApiResponse;
import com.artademi.demo.dto.DemoNoteResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/demo-notes -> aktif tenant'in notlari (ApiResponse zarfiyla).
 * Tenant, controller'a ASLA parametre olarak gelmez; TenantContext'ten gelir.
 */
@RestController
@RequestMapping("/api/demo-notes")
public class DemoNoteController {

    private final DemoNoteService service;

    public DemoNoteController(DemoNoteService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<DemoNoteResponse>> list() {
        return ApiResponse.ok(service.getNotes());
    }
}
