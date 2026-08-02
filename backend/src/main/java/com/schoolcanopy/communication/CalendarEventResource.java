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
import com.schoolcanopy.common.exceptions.ResourceNotFoundException;
import com.schoolcanopy.common.exceptions.ValidationException;
import com.schoolcanopy.academic.AcademicYear;
import com.schoolcanopy.academic.AcademicYearRepository;
import com.schoolcanopy.academic.AcademicYearService;
import com.schoolcanopy.academic.ClassRepository;
import com.schoolcanopy.academic.SchoolClass;
import com.schoolcanopy.academic.Section;
import com.schoolcanopy.academic.SectionRepository;
import com.schoolcanopy.rbac.RequestContext;

import io.quarkus.panache.common.Sort;

@Path("/api/school/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CalendarEventResource {

    @Inject CalendarEventRepository calendarEventRepository;
    @Inject ClassRepository classRepository;
    @Inject SectionRepository sectionRepository;
    @Inject RequestContext requestContext;
    @Inject AcademicYearService academicYearService;
    @Inject AcademicYearRepository academicYearRepository;

    @POST
    @Transactional
    public Response create(Map<String, String> body) {
        requireStaff();
        CalendarEvent event = new CalendarEvent();
        UUID schoolId = requestContext.getSchoolId();
        AcademicYear activeYear = academicYearService.requireActiveYear(schoolId);
        event.setSchoolId(schoolId);
        event.setAuthorId(requestContext.getUserId());
        event.setAcademicYearId(activeYear.getId());
        event.setCreatedAt(LocalDateTime.now());
        applyFields(event, body, true);
        calendarEventRepository.persist(event);
        return Response.status(Response.Status.CREATED).entity(ApiResponse.success(toDto(event))).build();
    }

    @GET
    public Response list(@QueryParam("academicYearId") UUID academicYearId) {
        requireStaff();
        UUID schoolId = requestContext.getSchoolId();
        UUID yearId = academicYearId;
        if (yearId == null) {
            AcademicYear active = academicYearRepository.findActiveBySchoolId(schoolId);
            if (active == null) return Response.ok(ApiResponse.success(List.of())).build();
            yearId = active.getId();
        }

        List<CalendarEvent> events = calendarEventRepository
                .find("schoolId = ?1 AND academicYearId = ?2 AND eventDate >= ?3",
                        Sort.ascending("eventDate"), schoolId, yearId, LocalDate.now())
                .list();

        return Response.ok(ApiResponse.success(events.stream().map(this::toDto).toList())).build();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") UUID id) {
        requireStaff();
        return Response.ok(ApiResponse.success(toDto(requireEvent(id)))).build();
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") UUID id, Map<String, String> body) {
        requireStaff();
        CalendarEvent event = requireEvent(id);
        applyFields(event, body, false);
        return Response.ok(ApiResponse.success(toDto(event))).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") UUID id) {
        requireStaff();
        CalendarEvent event = requireEvent(id);
        calendarEventRepository.delete(event);
        return Response.ok(ApiResponse.success(null)).build();
    }

    private void requireStaff() {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isTeacher()) {
            throw new ForbiddenException();
        }
    }

    private CalendarEvent requireEvent(UUID id) {
        CalendarEvent event = calendarEventRepository.findById(id);
        if (event == null || !event.getSchoolId().equals(requestContext.getSchoolId())) {
            throw new ResourceNotFoundException("Event not found");
        }
        return event;
    }

    private void applyFields(CalendarEvent event, Map<String, String> body, boolean isCreate) {
        String title = body.get("title");
        String dateStr = body.get("eventDate");
        String startTimeStr = body.get("startTime");
        String endTimeStr = body.get("endTime");
        String location = body.containsKey("location") ? body.get("location") : event.getLocation();
        String scopeType = body.getOrDefault("scopeType", event.getScopeType() != null ? event.getScopeType() : "SCHOOL");
        String scopeId = body.get("scopeId");

        if (title != null) {
            if (title.isBlank() || title.length() > 150) {
                throw new ValidationException("title", "INVALID", "Title is required (1-150 characters)");
            }
            event.setTitle(title.trim());
        } else if (isCreate) {
            throw new ValidationException("title", "INVALID", "Title is required (1-150 characters)");
        }

        if (dateStr != null && !dateStr.isBlank()) {
            LocalDate eventDate = LocalDate.parse(dateStr);
            if (isCreate && eventDate.isBefore(LocalDate.now())) {
                throw new ValidationException("eventDate", "INVALID", "Event date cannot be in the past");
            }
            event.setEventDate(eventDate);
        } else if (isCreate) {
            throw new ValidationException("eventDate", "REQUIRED", "Event date is required");
        }

        LocalTime startTime = startTimeStr != null && !startTimeStr.isBlank() ? LocalTime.parse(startTimeStr) : null;
        LocalTime endTime = endTimeStr != null && !endTimeStr.isBlank() ? LocalTime.parse(endTimeStr) : null;
        if (body.containsKey("startTime")) event.setStartTime(startTime);
        if (body.containsKey("endTime")) event.setEndTime(endTime);

        if (event.getStartTime() != null && event.getEndTime() != null && !event.getEndTime().isAfter(event.getStartTime())) {
            throw new ValidationException("endTime", "INVALID", "End time must be after start time");
        }

        if (location != null && location.length() > 200) {
            throw new ValidationException("location", "TOO_LONG", "Location must not exceed 200 characters");
        }
        if (body.containsKey("location")) {
            event.setLocation(location != null && !location.isBlank() ? location.trim() : null);
        }

        if (body.containsKey("scopeType") || body.containsKey("scopeId") || isCreate) {
            String effectiveScopeId = scopeId != null ? scopeId
                    : (event.getScopeId() != null ? event.getScopeId().toString() : null);
            if ("CLASS".equals(scopeType) || "SECTION".equals(scopeType)) {
                if (effectiveScopeId == null || effectiveScopeId.isBlank()) {
                    throw new ValidationException("scopeId", "REQUIRED", "Please select a class or section for this audience");
                }
            }
            event.setScopeType(scopeType);
            event.setScopeId("SCHOOL".equals(scopeType) ? null : UUID.fromString(effectiveScopeId));
        }
    }

    private Map<String, Object> toDto(CalendarEvent e) {
        var map = new java.util.HashMap<String, Object>();
        map.put("id", e.getId());
        map.put("title", e.getTitle());
        map.put("eventDate", e.getEventDate().toString());
        map.put("scopeType", e.getScopeType());
        map.put("scopeId", e.getScopeId());
        map.put("academicYearId", e.getAcademicYearId());
        if (e.getStartTime() != null) map.put("startTime", e.getStartTime().toString());
        if (e.getEndTime() != null) map.put("endTime", e.getEndTime().toString());
        if (e.getLocation() != null) map.put("location", e.getLocation());

        ScopeInfo scope = resolveScopeInfo(e.getScopeType(), e.getScopeId());
        map.put("scopeLabel", scope.label());
        if (scope.classId() != null) map.put("scopeClassId", scope.classId());
        return map;
    }

    private ScopeInfo resolveScopeInfo(String scopeType, UUID scopeId) {
        if ("SCHOOL".equals(scopeType)) {
            return new ScopeInfo("Whole school", null);
        }
        if (scopeId == null) {
            return new ScopeInfo(scopeType, null);
        }
        if ("CLASS".equals(scopeType)) {
            SchoolClass schoolClass = classRepository.findById(scopeId);
            return new ScopeInfo(schoolClass != null ? schoolClass.getName() : "Class", scopeId);
        }
        if ("SECTION".equals(scopeType)) {
            Section section = sectionRepository.findById(scopeId);
            if (section == null) return new ScopeInfo("Section", null);
            SchoolClass schoolClass = classRepository.findById(section.getClassId());
            String label = schoolClass != null ? schoolClass.getName() + " · Section " + section.getName() : "Section " + section.getName();
            return new ScopeInfo(label, section.getClassId());
        }
        return new ScopeInfo(scopeType, null);
    }

    private record ScopeInfo(String label, UUID classId) {}
}
