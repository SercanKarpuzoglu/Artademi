package com.artademi.student.dto;

import com.artademi.student.StudentStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Manuel statu degisikligi istegi (PATCH /api/students/{id}/status).
 * Silme yerine PASIF'e almak da bu endpoint uzerinden yapilir.
 */
public record UpdateStatusRequest(
        @NotNull(message = "Statü zorunludur")
        StudentStatus status) {
}
