package com.artademi.common.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * Tum TENANT is entity'lerinin turedigi taban sinif. {@code tenant_id} kolonunu
 * ve <b>her zaman acik (fail-closed)</b> global Hibernate tenant filtresini saglar.
 *
 * <p><b>Otomatik + her zaman acik filtre:</b> {@code tenantFilter},
 * {@code autoEnabled = true} sayesinde HER Hibernate oturumunda otomatik etkinlesir;
 * parametresi {@link TenantIdResolver} ile cozulur. Sorgu hangi yoldan gelirse gelsin
 * (servis, repository, dogrudan) tenant'a kisitlanir. {@link TenantContext} bossa
 * resolver -1 dondurur => BOS sonuc. Bir repository {@code tenant_id} kosulunu yazmayi
 * "unutsa" bile veri sizmaz.
 *
 * <p><b>Otomatik yazma:</b> insert sirasinda {@code tenant_id}, {@code TenantContext}'ten
 * set edilir (zaten dolu degilse).
 *
 * <p>NOT: platform tablolari ({@code tenant}, {@code subscription}, {@code plan})
 * bu sinifi GENISLETMEZ; onlar tenant filtresinden muaftir.
 */
@MappedSuperclass
@FilterDef(
        name = "tenantFilter",
        autoEnabled = true,
        parameters = @ParamDef(name = "tenantId", type = UUID.class, resolver = TenantIdResolver.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public abstract class TenantAware {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    @PrePersist
    void applyTenantOnPersist() {
        if (tenantId == null) {
            tenantId = TenantContext.get();
        }
    }
}
