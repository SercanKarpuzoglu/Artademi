package com.artademi.teacher;

import com.artademi.branch.Branch;
import com.artademi.common.tenant.TenantAware;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Ogretmen <-> Brans cok-coga iliskisinin ACIK baglanti entity'si.
 *
 * <p>Neden acik entity? Duz {@code @ManyToMany} kullanilsaydi join tablosuna {@code tenant_id}
 * NOT NULL otomatik konamaz ve global tenant filtresi join satirina uygulanamazdi. Bu entity
 * {@link TenantAware}'den turedigi icin her baglanti satiri da {@code tenant_id} tasir ve
 * filtreye tabidir (yazma sirasinda @PrePersist TenantContext'ten set eder).
 *
 * <p>{@code branch} alanini {@link Branch} entity'sine @ManyToOne ile baglamak, branch yuklemesinde
 * de Branch tenant filtresinin uygulanmasini saglar (savunma katmani / defense-in-depth).
 */
@Entity
@Table(name = "teacher_branch")
public class TeacherBranch extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    protected TeacherBranch() {
        // JPA icin
    }

    /** Verilen brans icin yeni baglanti olusturur (teacher, Teacher.setBranchLinks'te baglanir). */
    public static TeacherBranch of(Branch branch) {
        TeacherBranch tb = new TeacherBranch();
        tb.branch = branch;
        return tb;
    }

    public Long getId() {
        return id;
    }

    public Teacher getTeacher() {
        return teacher;
    }

    public void setTeacher(Teacher teacher) {
        this.teacher = teacher;
    }

    public Branch getBranch() {
        return branch;
    }

    public void setBranch(Branch branch) {
        this.branch = branch;
    }
}
