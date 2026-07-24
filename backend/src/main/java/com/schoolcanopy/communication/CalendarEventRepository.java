package com.schoolcanopy.communication;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CalendarEventRepository implements PanacheRepositoryBase<CalendarEvent, UUID> {
}
