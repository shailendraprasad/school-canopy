package com.schoolcanopy.school;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SchoolRepository implements PanacheRepositoryBase<School, UUID> {

    public School findByPrefix(String prefix) {
        return find("prefix", prefix).firstResult();
    }

    public boolean existsByPrefix(String prefix) {
        return count("prefix", prefix) > 0;
    }
}
