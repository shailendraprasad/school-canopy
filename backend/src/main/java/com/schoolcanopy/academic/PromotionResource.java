package com.schoolcanopy.academic;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.schoolcanopy.audit.AuditService;
import com.schoolcanopy.common.ApiResponse;
import com.schoolcanopy.common.exceptions.ForbiddenException;
import com.schoolcanopy.common.exceptions.ValidationException;
import com.schoolcanopy.rbac.RequestContext;

@Path("/api/school/promotions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PromotionResource {

    @Inject RequestContext requestContext;
    @Inject EntityManager em;
    @Inject AcademicYearRepository academicYearRepository;
    @Inject AcademicYearService academicYearService;
    @Inject AuditService auditService;

    @GET
    @Path("/preview")
    public Response preview(@QueryParam("fromYearId") UUID fromYearId,
                            @QueryParam("sectionId") UUID sectionId) {
        if (!requestContext.isSchoolAdministrator()) throw new ForbiddenException();
        UUID schoolId = requestContext.getSchoolId();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT s.id, s.student_id, s.name, s.status " +
                "FROM student s " +
                "JOIN student_section_enrollment sse ON sse.student_id = s.id " +
                "WHERE sse.section_id = :sectionId AND sse.academic_year_id = :yearId " +
                "AND sse.status = 'ACTIVE' AND s.status = 'ACTIVE' " +
                "ORDER BY s.name")
                .setParameter("sectionId", sectionId)
                .setParameter("yearId", fromYearId)
                .getResultList();

        var students = rows.stream().map(r -> Map.of(
                "id", r[0], "studentId", r[1], "name", r[2], "status", r[3]
        )).toList();
        return Response.ok(ApiResponse.success(students)).build();
    }

    @POST
    @Transactional
    @SuppressWarnings("unchecked")
    public Response promote(Map<String, Object> body) {
        if (!requestContext.isSchoolAdministrator()) throw new ForbiddenException();

        UUID schoolId = requestContext.getSchoolId();
        String fromYearIdStr = (String) body.get("fromYearId");
        String toYearIdStr = (String) body.get("toYearId");
        List<Map<String, String>> actions = (List<Map<String, String>>) body.get("actions");

        if (fromYearIdStr == null || toYearIdStr == null || actions == null || actions.isEmpty()) {
            throw new ValidationException("body", "REQUIRED", "fromYearId, toYearId, and actions are required");
        }

        UUID fromYearId = UUID.fromString(fromYearIdStr);
        UUID toYearId = UUID.fromString(toYearIdStr);
        AcademicYear fromYear = academicYearService.requireWritableYear(fromYearId, schoolId);
        AcademicYear toYear = academicYearRepository.findById(toYearId);
        if (toYear == null || !toYear.getSchoolId().equals(schoolId)) {
            throw new ValidationException("toYearId", "INVALID", "Target academic year not found");
        }
        if (!"PLANNED".equals(toYear.getStatus()) && !"ACTIVE".equals(toYear.getStatus())) {
            throw new ValidationException("toYearId", "CLOSED", "Target academic year must be planned or active");
        }

        int promoted = 0, retained = 0, graduated = 0, left = 0;

        for (Map<String, String> action : actions) {
            UUID studentId = UUID.fromString(action.get("studentId"));
            String type = action.getOrDefault("action", "PROMOTE").toUpperCase();
            UUID toSectionId = action.get("toSectionId") != null && !action.get("toSectionId").isBlank()
                    ? UUID.fromString(action.get("toSectionId")) : null;

            UUID retainSectionId = null;
            if ("RETAIN".equals(type)) {
                retainSectionId = (UUID) em.createNativeQuery(
                        "SELECT section_id FROM student_section_enrollment WHERE student_id = :sid AND academic_year_id = :yearId AND status = 'ACTIVE'")
                        .setParameter("sid", studentId).setParameter("yearId", fromYearId).getSingleResult();
            }

            academicYearService.closeActiveEnrollment(studentId, fromYearId);

            switch (type) {
                case "PROMOTE" -> {
                    if (toSectionId == null) {
                        throw new ValidationException("toSectionId", "REQUIRED", "Destination section required for promote");
                    }
                    academicYearService.createActiveEnrollment(studentId, toSectionId, schoolId, toYearId);
                    promoted++;
                }
                case "RETAIN" -> {
                    if (retainSectionId == null) {
                        throw new ValidationException("section", "NOT_FOUND", "No active section found to retain");
                    }
                    academicYearService.createActiveEnrollment(studentId, retainSectionId, schoolId, toYearId);
                    retained++;
                }
                case "GRADUATE" -> {
                    em.createNativeQuery("UPDATE student SET status = 'GRADUATED', updated_at = NOW() WHERE id = :id")
                            .setParameter("id", studentId).executeUpdate();
                    graduated++;
                }
                case "LEAVE" -> {
                    em.createNativeQuery("UPDATE student SET status = 'WITHDRAWN', updated_at = NOW() WHERE id = :id")
                            .setParameter("id", studentId).executeUpdate();
                    left++;
                }
                default -> throw new ValidationException("action", "INVALID", "Unknown action: " + type);
            }
        }

        if ("PLANNED".equals(toYear.getStatus())) {
            AcademicYear current = academicYearRepository.findActiveBySchoolId(schoolId);
            if (current != null && current.getId().equals(fromYearId)) {
                current.setStatus("CLOSED");
                academicYearRepository.persist(current);
            }
            toYear.setStatus("ACTIVE");
            academicYearRepository.persist(toYear);
        }

        auditService.log("STUDENTS_PROMOTED", schoolId, toYearId.toString(), requestContext.getUserId(),
                String.format("promoted=%d retained=%d graduated=%d left=%d", promoted, retained, graduated, left));

        return Response.ok(ApiResponse.success(Map.of(
                "promoted", promoted, "retained", retained, "graduated", graduated, "left", left
        ))).build();
    }
}
