package com.schoolcanopy.academic;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AcademicYearRepository implements PanacheRepositoryBase<AcademicYear, UUID> {

    public AcademicYear findActiveBySchoolId(UUID schoolId) {
        return find("schoolId = ?1 AND status = 'ACTIVE'", schoolId).firstResult();
    }
}
