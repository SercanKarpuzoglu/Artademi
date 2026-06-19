package com.artademi.attendance;

import com.artademi.group.Group;
import com.artademi.teacher.Teacher;
import com.artademi.teacher.TeacherRepository;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Yoklama erisim koprusu (keycloak-auth). @PreAuthorize rol kapilarinin OTESINDE, TEACHER rolunu
 * yalnizca KENDI gruplarina daraltan ince kurali enforce eder.
 *
 * <p>Kural ({@link #assertCanAccessGroup}):
 * <ul>
 *   <li>ADMIN / FRONTDESK / FRONTDESK_ACCOUNTING -> tum gruplara izinli.</li>
 *   <li>TEACHER -> JWT {@code sub} ile {@code teacherRepository.findByKeycloakUserId} cozulur;
 *       ogretmen yoksa 403. Varsa grubun ogretmeni bu ogretmen DEGILSE 403.</li>
 *   <li>Digerleri -> 403.</li>
 * </ul>
 *
 * <p>JWT {@code sub} ve roller dogrudan {@code SecurityContext}'ten okunur; tenant koprusu yine
 * tenant-filtreli ({@code findByKeycloakUserId} JPQL) calistigi icin baska tenant'in ogretmeni
 * eslesemez.
 */
@Component
public class AttendanceAccessGuard {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_FRONTDESK = "ROLE_FRONTDESK";
    private static final String ROLE_FRONTDESK_ACCOUNTING = "ROLE_FRONTDESK_ACCOUNTING";
    private static final String ROLE_TEACHER = "ROLE_TEACHER";

    private final TeacherRepository teacherRepository;

    public AttendanceAccessGuard(TeacherRepository teacherRepository) {
        this.teacherRepository = teacherRepository;
    }

    /**
     * Caller'in verilen gruba erisebilecegini dogrular; aksi halde 403 (AccessDeniedException).
     * Ofis rolleri tum gruplara erisir; TEACHER yalnizca kendi (ogretmeni oldugu) gruplarina.
     */
    public void assertCanAccessGroup(Group grup) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new AccessDeniedException("Yetki yok");
        }
        if (hasAnyRole(auth, ROLE_ADMIN, ROLE_FRONTDESK, ROLE_FRONTDESK_ACCOUNTING)) {
            return;
        }
        if (hasAnyRole(auth, ROLE_TEACHER)) {
            Teacher teacher = resolveTeacherOrDeny(auth);
            if (grup.getOgretmen() == null
                    || !grup.getOgretmen().getId().equals(teacher.getId())) {
                throw new AccessDeniedException("Bu grup için yetkiniz yok");
            }
            return;
        }
        throw new AccessDeniedException("Yetki yok");
    }

    /**
     * Caller TEACHER ise yalnizca kendi gruplariyla sinirlandirmak icin ogretmen id'sini doner;
     * ofis rolu varsa null (kisitlama yok) doner. TEACHER olup ogretmen eslesmezse 403.
     */
    public Long teacherScopeOgretmenId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new AccessDeniedException("Yetki yok");
        }
        if (hasAnyRole(auth, ROLE_ADMIN, ROLE_FRONTDESK, ROLE_FRONTDESK_ACCOUNTING)) {
            return null;
        }
        if (hasAnyRole(auth, ROLE_TEACHER)) {
            return resolveTeacherOrDeny(auth).getId();
        }
        throw new AccessDeniedException("Yetki yok");
    }

    private Teacher resolveTeacherOrDeny(Authentication auth) {
        String sub = subject(auth);
        if (sub == null) {
            throw new AccessDeniedException("Öğretmen kimliği çözülemedi");
        }
        return teacherRepository.findByKeycloakUserId(sub)
                .orElseThrow(() -> new AccessDeniedException("Öğretmen bulunamadı"));
    }

    private static String subject(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getSubject();
        }
        return null;
    }

    private static boolean hasAnyRole(Authentication auth, String... roles) {
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String name = authority.getAuthority();
            for (String role : roles) {
                if (role.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
