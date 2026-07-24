package com.schoolcanopy.user;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserAccountRepository implements PanacheRepositoryBase<UserAccount, UUID> {

    public UserAccount findByEmail(String email) {
        return find("email", email).firstResult();
    }

    public boolean existsByEmail(String email) {
        return count("email", email) > 0;
    }

    public boolean existsByEmailAndSchoolId(String email, UUID schoolId) {
        return count("email = ?1 AND schoolId = ?2", email, schoolId) > 0;
    }

    public long countByRoleAndSchoolId(String role, UUID schoolId) {
        return count("role = ?1 AND schoolId = ?2", role, schoolId);
    }
}
