package com.schoolcanopy.communication;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AnnouncementRepository implements PanacheRepositoryBase<Announcement, UUID> {
}
