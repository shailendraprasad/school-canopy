package com.schoolcanopy.academic;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ClassRepository implements PanacheRepositoryBase<SchoolClass, UUID> {
}
