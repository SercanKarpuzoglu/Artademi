package com.artademi.teacher;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Oturum sahibini (JWT {@code sub}) → {@link Teacher} kaydina cozen TEK mekanizma (keycloak-auth).
 * {@code sub} → {@code Teacher.keycloakUserId} eslemesidir; yeni bir atama sistemi DEGIL.
 *
 * <p>Hem {@code AttendanceAccessGuard} (yoklama erisim daraltmasi) hem grup {@code /mine} ucu bunu
 * kullanir — kopyalama YOK, tek kaynak. {@code findByKeycloakUserId} JPQL'i global tenant filtresine
 * tabi oldugundan yalnizca aktif tenant'in ogretmeni cozulur (baska tenant'inki ASLA).
 *
 * <p>Eslesme yoksa {@link Optional#empty()} doner (firlatmaz); cagiran tarafa karar birakir: guard
 * 403'e cevirir, {@code /mine} bos liste dondurur.
 */
@Component
public class CurrentTeacherResolver {

    private final TeacherRepository teacherRepository;

    public CurrentTeacherResolver(TeacherRepository teacherRepository) {
        this.teacherRepository = teacherRepository;
    }

    /** Oturum sahibinin ogretmen kaydi; kimlik/sub yoksa veya eslesme yoksa empty. */
    public Optional<Teacher> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String sub = (auth instanceof JwtAuthenticationToken jwtAuth)
                ? jwtAuth.getToken().getSubject()
                : null;
        if (sub == null) {
            return Optional.empty();
        }
        return teacherRepository.findByKeycloakUserId(sub);
    }
}
