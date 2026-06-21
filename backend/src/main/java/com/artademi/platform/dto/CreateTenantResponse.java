package com.artademi.platform.dto;

/**
 * {@code POST /api/platform/tenants} yaniti: olusan tenant + ilk admin provisioning sonucu.
 *
 * <p>Sira (a): tenant ONCE DB'ye commit edilir, SONRA Keycloak admin yaratilir. Keycloak basarisiz
 * olursa tenant GERI ALINMAZ ("silme yok" ilkesi); {@code admin.provisioned=false} ve {@code warning}
 * doldurulur (HTTP yine 201 — tenant gercekten olustu). Basarili olunca {@code warning} null'dur.
 */
public record CreateTenantResponse(
        PlatformTenantResponse tenant,
        AdminInfo admin,
        String warning) {

    /** Provision edilen (ya da edilemeyen) ilk admin ozeti. */
    public record AdminInfo(String username, String email, boolean provisioned) {
    }

    /** Admin basariyla yaratildi. */
    public static CreateTenantResponse provisioned(
            PlatformTenantResponse tenant, String username, String email) {
        return new CreateTenantResponse(tenant, new AdminInfo(username, email, true), null);
    }

    /** Admin yaratilamadi; tenant kaldi, uyari doner. */
    public static CreateTenantResponse failed(
            PlatformTenantResponse tenant, String email, String warning) {
        return new CreateTenantResponse(tenant, new AdminInfo(null, email, false), warning);
    }
}
