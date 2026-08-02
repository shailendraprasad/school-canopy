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

import com.schoolcanopy.common.ApiResponse;
import com.schoolcanopy.common.exceptions.ConflictException;
import com.schoolcanopy.common.exceptions.ForbiddenException;
import com.schoolcanopy.common.exceptions.ResourceNotFoundException;
import com.schoolcanopy.common.exceptions.ValidationException;
import com.schoolcanopy.rbac.RequestContext;

@Path("/api/school/subjects")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SubjectResource {

    @Inject RequestContext requestContext;
    @Inject SubjectRepository subjectRepository;
    @Inject AcademicYearService academicYearService;
    @Inject AcademicYearRepository academicYearRepository;
    @Inject EntityManager em;

    @GET
    public Response list() {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff() && !requestContext.isTeacher()) {
            throw new ForbiddenException();
        }
        UUID schoolId = requestContext.getSchoolId();
        List<Subject> subjects = subjectRepository.find("schoolId = ?1 AND status = 'ACTIVE' ORDER BY name", schoolId).list();
        return Response.ok(ApiResponse.success(subjects.stream().map(this::toDto).toList())).build();
    }

    @POST
    @Transactional
    public Response create(Map<String, String> body) {
        if (!requestContext.isSchoolAdministrator()) throw new ForbiddenException();

        String name = body.get("name");
        if (name == null || name.isBlank()) {
            throw new ValidationException("name", "REQUIRED", "Subject name is required");
        }

        UUID schoolId = requestContext.getSchoolId();
        Subject existing = subjectRepository.find("schoolId = ?1 AND LOWER(name) = ?2", schoolId, name.trim().toLowerCase()).firstResult();
        if (existing != null) {
            throw new ConflictException("name", "DUPLICATE", "A subject with this name already exists");
        }

        Subject subject = new Subject();
        subject.setSchoolId(schoolId);
        subject.setName(name.trim());
        subject.setCode(body.get("code") != null ? body.get("code").trim().toUpperCase() : null);
        subject.setStatus("ACTIVE");
        subject.setCreatedAt(LocalDateTime.now());
        subjectRepository.persist(subject);

        return Response.status(Response.Status.CREATED).entity(ApiResponse.success(toDto(subject))).build();
    }

    @POST
    @Path("/{subjectId}/assign")
    @Transactional
    public Response assignTeacher(@PathParam("subjectId") UUID subjectId, Map<String, String> body) {
        if (!requestContext.isSchoolAdministrator()) throw new ForbiddenException();

        String teacherIdStr = body.get("teacherId");
        String sectionIdStr = body.get("sectionId");
        if (teacherIdStr == null || sectionIdStr == null) {
            throw new ValidationException("fields", "REQUIRED", "teacherId and sectionId are required");
        }

        Subject subject = subjectRepository.findById(subjectId);
        if (subject == null || !subject.getSchoolId().equals(requestContext.getSchoolId())) {
            throw new ResourceNotFoundException("Subject not found");
        }

        UUID schoolId = requestContext.getSchoolId();
        AcademicYear activeYear = academicYearService.requireActiveYear(schoolId);
        UUID teacherId = UUID.fromString(teacherIdStr);
        UUID sectionId = UUID.fromString(sectionIdStr);

        Long exists = (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM subject_teacher_assignment " +
                "WHERE subject_id = :subId AND section_id = :secId AND academic_year_id = :yearId AND status = 'ACTIVE'")
                .setParameter("subId", subjectId)
                .setParameter("secId", sectionId)
                .setParameter("yearId", activeYear.getId())
                .getSingleResult();
        if (exists > 0) {
            throw new ConflictException("assignment", "DUPLICATE", "This subject is already assigned to this section for the active year");
        }

        em.createNativeQuery(
                "INSERT INTO subject_teacher_assignment (id, school_id, subject_id, teacher_id, section_id, academic_year_id, status, assigned_at) " +
                "VALUES (gen_random_uuid(), :schoolId, :subId, :teacherId, :sectionId, :yearId, 'ACTIVE', NOW())")
                .setParameter("schoolId", schoolId)
                .setParameter("subId", subjectId)
                .setParameter("teacherId", teacherId)
                .setParameter("sectionId", sectionId)
                .setParameter("yearId", activeYear.getId())
                .executeUpdate();

        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.success(Map.of("subjectId", subjectId, "teacherId", teacherId, "sectionId", sectionId)))
                .build();
    }

    @GET
    @Path("/assignments")
    public Response listAssignments(@QueryParam("sectionId") UUID sectionId,
                                    @QueryParam("academicYearId") UUID academicYearId) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff() && !requestContext.isTeacher()) {
            throw new ForbiddenException();
        }

        UUID yearId = academicYearId;
        if (yearId == null) {
            AcademicYear active = academicYearRepository.findActiveBySchoolId(requestContext.getSchoolId());
            if (active == null) return Response.ok(ApiResponse.success(List.of())).build();
            yearId = active.getId();
        }

        StringBuilder sql = new StringBuilder(
                "SELECT sta.id, sub.id, sub.name, sub.code, u.id, u.name, sec.id, sec.name, c.name " +
                "FROM subject_teacher_assignment sta " +
                "JOIN subject sub ON sub.id = sta.subject_id " +
                "JOIN user_account u ON u.id = sta.teacher_id " +
                "JOIN section sec ON sec.id = sta.section_id " +
                "JOIN \"class\" c ON c.id = sec.class_id " +
                "WHERE sta.school_id = :schoolId AND sta.academic_year_id = :yearId AND sta.status = 'ACTIVE' ");
        if (sectionId != null) sql.append("AND sta.section_id = :sectionId ");
        sql.append("ORDER BY sub.name, c.name, sec.name");

        var q = em.createNativeQuery(sql.toString())
                .setParameter("schoolId", requestContext.getSchoolId())
                .setParameter("yearId", yearId);
        if (sectionId != null) q.setParameter("sectionId", sectionId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        var assignments = rows.stream().map(r -> Map.of(
                "id", r[0],
                "subjectId", r[1], "subjectName", r[2], "subjectCode", r[3] != null ? r[3] : "",
                "teacherId", r[4], "teacherName", r[5],
                "sectionId", r[6], "sectionName", r[7], "className", r[8]
        )).toList();
        return Response.ok(ApiResponse.success(assignments)).build();
    }

    @DELETE
    @Path("/assignments/{assignmentId}")
    @Transactional
    public Response unassign(@PathParam("assignmentId") UUID assignmentId) {
        if (!requestContext.isSchoolAdministrator()) throw new ForbiddenException();

        int updated = em.createNativeQuery(
                "UPDATE subject_teacher_assignment SET status = 'CLOSED', ended_at = NOW() " +
                "WHERE id = :id AND school_id = :schoolId AND status = 'ACTIVE'")
                .setParameter("id", assignmentId)
                .setParameter("schoolId", requestContext.getSchoolId())
                .executeUpdate();
        if (updated == 0) throw new ResourceNotFoundException("Assignment not found");
        return Response.ok(ApiResponse.success(null)).build();
    }

    private Map<String, Object> toDto(Subject s) {
        var m = new java.util.HashMap<String, Object>();
        m.put("id", s.getId());
        m.put("name", s.getName());
        m.put("code", s.getCode());
        m.put("status", s.getStatus());
        return m;
    }
}
