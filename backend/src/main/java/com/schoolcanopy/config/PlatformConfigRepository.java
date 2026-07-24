package com.schoolcanopy.config;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PlatformConfigRepository implements PanacheRepositoryBase<PlatformConfig, String> {

    public PlatformConfig findByKey(String key) {
        return findById(key);
    }
}
