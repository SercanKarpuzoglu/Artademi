package com.artademi.demo;

import com.artademi.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tenant filtresini kanitlayan gecici demo entity'si. {@link TenantAware}'den
 * turedigi icin {@code tenant_id} ve global tenant filtresine otomatik tabidir.
 */
@Entity
@Table(name = "demo_note")
public class DemoNote extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "text", nullable = false, length = 500)
    private String text;

    protected DemoNote() {
        // JPA icin
    }

    public DemoNote(String text) {
        this.text = text;
    }

    public Long getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
