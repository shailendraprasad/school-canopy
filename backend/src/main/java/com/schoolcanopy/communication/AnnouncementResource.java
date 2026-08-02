package com.schoolcanopy.communication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.schoolcanopy.academic.ClassRepository;
import com.schoolcanopy.academic.SchoolClass;
import com.schoolcanopy.academic.Section;
import com.schoolcanopy.academic.SectionRepository;
import com.schoolcanopy.common.ApiResponse;
import com.schoolcanopy.common.PaginationMeta;
import com.schoolcanopy.common.PaginationParams;
import com.schoolcanopy.common.exceptions.ForbiddenException;
import com.schoolcanopy.common.exceptions.ResourceNotFoundException;
import com.schoolcanopy.common.exceptions.ValidationException;
import com.schoolcanopy.rbac.RequestContext;

@Path("/api/school/announcements")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AnnouncementResource {

    @Inject AnnouncementRepository announcementRepository;
    @Inject ClassRepository classRepository;
    @Inject SectionRepository sectionRepository;
    @Inject RequestContext requestContext;

    @POST
    @Transactional
    public Response create(Map<String, String> body) {
        requireStaff();
        Announcement announcement = new Announcement();
        announcement.setSchoolId(requestContext.getSchoolId());
        announcement.setAuthorId(requestContext.getUserId());
        announcement.setCreatedAt(LocalDateTime.now());
        applyFields(announcement, body, true);
        announcementRepository.persist(announcement);
        return Response.status(Response.Status.CREATED).entity(ApiResponse.success(toDto(announcement))).build();
    }

    @GET
    public Response list(PaginationParams pagination) {
        requireStaff();
        UUID schoolId = requestContext.getSchoolId();
        long total = announcementRepository.count("schoolId = ?1 AND status = 'PUBLISHED'", schoolId);
        List<Announcement> announcements = announcementRepository
                .find("schoolId = ?1 AND status = 'PUBLISHED'", io.quarkus.panache.common.Sort.descending("publishAt"), schoolId)
                .page(pagination.getOffset() / pagination.getLimit(), pagination.getLimit()).list();
        return Response.ok(ApiResponse.success(announcements.stream().map(this::toDto).toList(),
                new PaginationMeta(total, pagination.getOffset(), pagination.getLimit()))).build();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") UUID id) {
        requireStaff();
        return Response.ok(ApiResponse.success(toDto(requireAnnouncement(id)))).build();
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") UUID id, Map<String, String> body) {
        requireStaff();
        Announcement announcement = requireAnnouncement(id);
        applyFields(announcement, body, false);
        return Response.ok(ApiResponse.success(toDto(announcement))).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") UUID id) {
        requireStaff();
        Announcement announcement = requireAnnouncement(id);
        announcementRepository.delete(announcement);
        return Response.ok(ApiResponse.success(null)).build();
    }

    private void requireStaff() {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isTeacher()) {
            throw new ForbiddenException();
        }
    }

    private Announcement requireAnnouncement(UUID id) {
        Announcement announcement = announcementRepository.findById(id);
        if (announcement == null || !announcement.getSchoolId().equals(requestContext.getSchoolId())) {
            throw new ResourceNotFoundException("Announcement not found");
        }
        return announcement;
    }

    private void applyFields(Announcement announcement, Map<String, String> body, boolean isCreate) {
        String title = body.get("title");
        String bodyText = body.get("body");
        String category = body.getOrDefault("category", announcement.getCategory() != null ? announcement.getCategory() : "GENERAL");
        String scopeType = body.getOrDefault("scopeType", announcement.getScopeType() != null ? announcement.getScopeType() : "SCHOOL");
        String scopeId = body.get("scopeId");
        String status = body.getOrDefault("status", announcement.getStatus() != null ? announcement.getStatus() : "DRAFT");

        if (title != null) {
            if (title.isBlank() || title.length() > 150) {
                throw new ValidationException("title", "INVALID", "Title is required (max 150 characters)");
            }
            announcement.setTitle(title.trim());
        } else if (isCreate) {
            throw new ValidationException("title", "INVALID", "Title is required (max 150 characters)");
        }

        if (bodyText != null) {
            if (bodyText.isBlank() || bodyText.length() > 5000) {
                throw new ValidationException("body", "INVALID", "Body is required (max 5000 characters)");
            }
            announcement.setBody(bodyText.trim());
        } else if (isCreate) {
            throw new ValidationException("body", "INVALID", "Body is required (max 5000 characters)");
        }

        if (body.containsKey("scopeType") || body.containsKey("scopeId") || isCreate) {
            String effectiveScopeId = scopeId != null ? scopeId
                    : (announcement.getScopeId() != null ? announcement.getScopeId().toString() : null);
            validateScope(scopeType, effectiveScopeId);
            announcement.setScopeType(scopeType);
            announcement.setScopeId(resolveScopeId(scopeType, effectiveScopeId));
        }

        announcement.setCategory(category);
        announcement.setStatus(status);

        if ("PUBLISHED".equalsIgnoreCase(status) && announcement.getPublishAt() == null) {
            announcement.setPublishAt(LocalDateTime.now());
        }
    }

    private void validateScope(String scopeType, String scopeId) {
        if ("CLASS".equals(scopeType) || "SECTION".equals(scopeType)) {
            if (scopeId == null || scopeId.isBlank()) {
                throw new ValidationException("scopeId", "REQUIRED", "Please select a class or section for this audience");
            }
        }
    }

    private UUID resolveScopeId(String scopeType, String scopeId) {
        if ("SCHOOL".equals(scopeType)) return null;
        return scopeId != null ? UUID.fromString(scopeId) : null;
    }

    private Map<String, Object> toDto(Announcement a) {
        var dto = new java.util.HashMap<String, Object>();
        dto.put("id", a.getId());
        dto.put("title", a.getTitle());
        dto.put("body", a.getBody());
        dto.put("category", a.getCategory());
        dto.put("scopeType", a.getScopeType());
        dto.put("scopeId", a.getScopeId());
        dto.put("status", a.getStatus());
        dto.put("createdAt", a.getCreatedAt().toString());
        if (a.getPublishAt() != null) dto.put("publishAt", a.getPublishAt().toString());

        ScopeInfo scope = resolveScopeInfo(a.getScopeType(), a.getScopeId());
        dto.put("scopeLabel", scope.label());
        if (scope.classId() != null) dto.put("scopeClassId", scope.classId());
        return dto;
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
