package com.artademi.demo;

import com.artademi.demo.dto.DemoNoteResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo not servisi. {@code @Transactional} oldugundan, cagrildiginda
 * TenantFilterActivator aktif oturumda tenant filtresini etkinlestirir.
 */
@Service
public class DemoNoteService {

    private final DemoNoteRepository repository;

    public DemoNoteService(DemoNoteRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DemoNoteResponse> getNotes() {
        return repository.findAll().stream()
                .map(DemoNoteResponse::from)
                .toList();
    }
}
