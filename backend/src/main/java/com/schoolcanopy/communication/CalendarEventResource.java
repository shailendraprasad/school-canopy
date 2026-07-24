package com.schoolcanopy.communication;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.schoolcanopy.common.ApiResponse;
import com.schoolcanopy.common.exceptions.ForbiddenException;
import com.schoolcanopy.common.exceptions.ValidationException;
import com.schoolcanopy.rbac.RequestContext;

import io.quarkus.panache.common.Sort;

@Path("/api/school/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CalendarEventResource {

    @Inject CalendarEventRepository calendarEventRepository;
    @Inject RequestContext requestContext;

    @POST
    @Transactional
    public Response create(Map<String, String> body) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isTeacher()) {
            throw new ForbiddenException();
        }

        String title = body.get("title");
        String dateStr = body.get("eventDate");
        String startTimeStr = body.get("startTime");
        String endTimeStr = body.get("endTime");
        String location = body.get("location");
        String scopeType = body.getOrDefault("scopeType", "SCHOOL");
        String scopeId = body.get("scopeId");

        if (title == null || title.isBlank() || title.length() > 150) {
            throw new ValidationException("title", "INVALID", "Title is required (1-150 characters)");
        }
        if (dateStr == null || dateStr.isBlank()) {
            throw new ValidationException("eventDate", "REQUIRED", "Event date is required");
        }

        LocalDate eventDate = LocalDate.parse(dateStr);
        if (eventDate.isBefore(LocalDate.now())) {
            throw new ValidationException("eventDate", "INVALID", "Event date cannot be in the past");
        }

        LocalTime startTime = startTimeStr != null ? LocalTime.parse(startTimeStr) : null;
        LocalTime endTime = endTimeStr != null ? LocalTime.parse(endTimeStr) : null;

        if (startTime != null && endTime != null && !endTime.isAfter(startTime)) {
            throw new ValidationException("endTime", "INVALID", "End time must be after start time");
        }

        if (location != null && location.length() > 200) {
            throw new ValidationException("location", "TOO_LONG", "Location must not exceed 200 characters");
        }

        CalendarEvent event = new CalendarEvent();
        event.setSchoolId(requestContext.getSchoolId());
        event.setAuthorId(requestContext.getUserId());
        event.setTitle(title.trim());
        event.setEventDate(eventDate);
        event.setStartTime(startTime);
        event.setEndTime(endTime);
        event.setLocation(location != null ? location.trim() : null);
        event.setScopeType(scopeType);
        event.setScopeId(scopeId != null ? UUID.fromString(scopeId) : null);
        event.setCreatedAt(LocalDateTime.now());
        calendarEventRepository.persist(event);

        return Response.status(Response.Status.CREATED).entity(ApiResponse.success(toDto(event))).build();
    }

    @GET
    public Response list() {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isTeacher()) {
            throw new ForbiddenException();
        }

        UUID schoolId = requestContext.getSchoolId();
        List<CalendarEvent> events = calendarEventRepository
                .find("schoolId = ?1 AND eventDate >= ?2", Sort.ascending("eventDate"), schoolId, LocalDate.now())
                .list();

        return Response.ok(ApiResponse.success(events.stream().map(this::toDto).toList())).build();
    }

    private Map<String, Object> toDto(CalendarEvent e) {
        var map = new java.util.HashMap<String, Object>();
        map.put("id", e.getId());
        map.put("title", e.getTitle());
        map.put("eventDate", e.getEventDate().toString());
        map.put("scopeType", e.getScopeType());
        if (e.getStartTime() != null) map.put("startTime", e.getStartTime().toString());
        if (e.getEndTime() != null) map.put("endTime", e.getEndTime().toString());
        if (e.getLocation() != null) map.put("location", e.getLocation());
        return map;
    }
}
