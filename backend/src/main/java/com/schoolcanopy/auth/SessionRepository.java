package com.schoolcanopy.auth;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SessionRepository implements PanacheRepositoryBase<Session, String> {

    public Session findByToken(String token) {
        return findById(token);
    }

    public void deleteByToken(String token) {
        deleteById(token);
    }

    public void deleteByUserId(UUID userId) {
        delete("userId", userId);
    }

    public void deleteBySchoolId(UUID schoolId) {
        delete("userId IN (SELECT u.id FROM UserAccount u WHERE u.schoolId = ?1)", schoolId);
    }
}
