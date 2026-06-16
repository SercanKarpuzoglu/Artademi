package com.artademi.demo;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Tenant filtresi global olarak aktif oldugundan {@code findAll()} dahi yalnizca
 * aktif tenant'in kayitlarini dondurur.
 */
public interface DemoNoteRepository extends JpaRepository<DemoNote, Long> {
}
