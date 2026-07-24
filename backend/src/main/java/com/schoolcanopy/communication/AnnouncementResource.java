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

import com.schoolcanopy.common.ApiResponse;
import com.schoolcanopy.common.PaginationMeta;
import com.schoolcanopy.common.PaginationParams;
import com.schoolcanopy.common.exceptions.ForbiddenException;
import com.schoolcanopy.common.exceptions.ValidationException;
import com.schoolcanopy.rbac.RequestContext;

@Path("/api/school/announcements")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AnnouncementResource {

    @Inject AnnouncementRepository announcementRepository;
    @Inject RequestContext requestContext;

    @POST
    @Transactional
    public Response create(Map<String, String> body) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isTeacher()) {
            throw new ForbiddenException();
        }

        String title = body.get("title");
        String bodyText = body.get("body");
        String category = body.getOrDefault("category", "GENERAL");
        String scopeType = body.getOrDefault("scopeType", "SCHOOL");
        String scopeId = body.get("scopeId");
        String status = body.getOrDefault("status", "DRAFT");

        if (title == null || title.isBlank() || title.length() > 150) {
            throw new ValidationException("title", "INVALID", "Title is required (max 150 characters)");
        }
        if (bodyText == null || bodyText.isBlank() || bodyText.length() > 5000) {
            throw new ValidationException("body", "INVALID", "Body is required (max 5000 characters)");
        }

        Announcement announcement = new Announcement();
        announcement.setSchoolId(requestContext.getSchoolId());
        announcement.setAuthorId(requestContext.getUserId());
        announcement.setTitle(title.trim());
        announcement.setBody(bodyText.trim());
        announcement.setCategory(category);
        announcement.setScopeType(scopeType);
        announcement.setScopeId(scopeId != null ? UUID.fromString(scopeId) : null);
        announcement.setStatus(status);
        announcement.setCreatedAt(LocalDateTime.now());

        if ("PUBLISHED".equalsIgnoreCase(status)) {
            announcement.setPublishAt(LocalDateTime.now());
        }

        announcementRepository.persist(announcement);

        return Response.status(Response.Status.CREATED).entity(ApiResponse.success(toDto(announcement))).build();
    }

    @GET
    public Response list(PaginationParams pagination) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isTeacher()) {
            throw new ForbiddenException();
        }

        UUID schoolId = requestContext.getSchoolId();
        long total = announcementRepository.count("schoolId = ?1 AND status = 'PUBLISHED'", schoolId);
        List<Announcement> announcements = announcementRepository
                .find("schoolId = ?1 AND status = 'PUBLISHED'", io.quarkus.panache.common.Sort.descending("publishAt"), schoolId)
                .page(pagination.getOffset() / pagination.getLimit(), pagination.getLimit()).list();

        return Response.ok(ApiResponse.success(announcements.stream().map(this::toDto).toList(),
                new PaginationMeta(total, pagination.getOffset(), pagination.getLimit()))).build();
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
        return dto;
    }
}
