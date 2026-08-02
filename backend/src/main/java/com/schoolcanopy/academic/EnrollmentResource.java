package com.schoolcanopy.academic;

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

@Path("/api/school/section-enrollments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EnrollmentResource {

    @Inject RequestContext requestContext;
    @Inject EntityManager em;
    @Inject AcademicYearService academicYearService;
    @Inject AcademicYearRepository academicYearRepository;

    // === Teacher-Section Assignment ===

    @POST
    @Path("/{sectionId}/teachers")
    @Transactional
    public Response assignTeacher(@PathParam("sectionId") UUID sectionId, Map<String, String> body) {
        if (!requestContext.isSchoolAdministrator()) throw new ForbiddenException();

        String teacherIdStr = body.get("teacherId");
        if (teacherIdStr == null) throw new ValidationException("teacherId", "REQUIRED", "Teacher ID is required");

        UUID teacherId = UUID.fromString(teacherIdStr);
        UUID schoolId = requestContext.getSchoolId();
        AcademicYear activeYear = academicYearService.requireActiveYear(schoolId);

        Long exists = (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM teacher_section_assignment WHERE teacher_id = :tid AND section_id = :sid " +
                "AND academic_year_id = :yearId AND status = 'ACTIVE'")
                .setParameter("tid", teacherId)
                .setParameter("sid", sectionId)
                .setParameter("yearId", activeYear.getId())
                .getSingleResult();
        if (exists > 0) {
            throw new ConflictException("teacherId", "ALREADY_ASSIGNED", "Teacher is already assigned to this section");
        }

        em.createNativeQuery(
                "INSERT INTO teacher_section_assignment (id, teacher_id, section_id, school_id, academic_year_id, status, assigned_at) " +
                "VALUES (gen_random_uuid(), :tid, :sid, :schoolId, :yearId, 'ACTIVE', NOW())")
                .setParameter("tid", teacherId)
                .setParameter("sid", sectionId)
                .setParameter("schoolId", schoolId)
                .setParameter("yearId", activeYear.getId())
                .executeUpdate();

        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.success(Map.of("teacherId", teacherId, "sectionId", sectionId)))
                .build();
    }

    @GET
    @Path("/{sectionId}/teachers")
    public Response listTeachersForSection(@PathParam("sectionId") UUID sectionId,
                                         @QueryParam("academicYearId") UUID academicYearId) {
        UUID yearId = academicYearId;
        if (yearId == null) {
            AcademicYear active = academicYearRepository.findActiveBySchoolId(requestContext.getSchoolId());
            if (active == null) return Response.ok(ApiResponse.success(List.of())).build();
            yearId = active.getId();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT u.id, u.name, u.email FROM user_account u " +
                "JOIN teacher_section_assignment tsa ON tsa.teacher_id = u.id " +
                "WHERE tsa.section_id = :sid AND tsa.academic_year_id = :yearId AND tsa.status = 'ACTIVE'")
                .setParameter("sid", sectionId)
                .setParameter("yearId", yearId)
                .getResultList();

        var teachers = rows.stream().map(r -> Map.of("id", r[0], "name", r[1], "email", r[2])).toList();
        return Response.ok(ApiResponse.success(teachers)).build();
    }

    @DELETE
    @Path("/{sectionId}/teachers/{teacherId}")
    @Transactional
    public Response unassignTeacher(@PathParam("sectionId") UUID sectionId, @PathParam("teacherId") UUID teacherId) {
        if (!requestContext.isSchoolAdministrator()) throw new ForbiddenException();
        AcademicYear activeYear = academicYearService.requireActiveYear(requestContext.getSchoolId());

        int updated = em.createNativeQuery(
                "UPDATE teacher_section_assignment SET status = 'CLOSED', ended_at = NOW() " +
                "WHERE teacher_id = :tid AND section_id = :sid AND academic_year_id = :yearId AND status = 'ACTIVE'")
                .setParameter("tid", teacherId)
                .setParameter("sid", sectionId)
                .setParameter("yearId", activeYear.getId())
                .executeUpdate();

        if (updated == 0) throw new ResourceNotFoundException("Assignment not found");
        return Response.ok(ApiResponse.success(null)).build();
    }

    @POST
    @Path("/copy-teacher-assignments")
    @Transactional
    public Response copyTeacherAssignments(Map<String, String> body) {
        if (!requestContext.isSchoolAdministrator()) throw new ForbiddenException();
        UUID fromYearId = UUID.fromString(body.get("fromYearId"));
        UUID toYearId = UUID.fromString(body.get("toYearId"));
        UUID schoolId = requestContext.getSchoolId();
        academicYearService.requireWritableYear(toYearId, schoolId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT teacher_id, section_id FROM teacher_section_assignment " +
                "WHERE school_id = :schoolId AND academic_year_id = :fromYearId AND status = 'ACTIVE'")
                .setParameter("schoolId", schoolId)
                .setParameter("fromYearId", fromYearId)
                .getResultList();

        int copied = 0;
        for (Object[] row : rows) {
            UUID tid = (UUID) row[0];
            UUID sid = (UUID) row[1];
            Long exists = (Long) em.createNativeQuery(
                    "SELECT COUNT(*) FROM teacher_section_assignment WHERE teacher_id = :tid AND section_id = :sid " +
                    "AND academic_year_id = :yearId AND status = 'ACTIVE'")
                    .setParameter("tid", tid).setParameter("sid", sid).setParameter("yearId", toYearId)
                    .getSingleResult();
            if (exists == 0) {
                em.createNativeQuery(
                        "INSERT INTO teacher_section_assignment (id, teacher_id, section_id, school_id, academic_year_id, status, assigned_at) " +
                        "VALUES (gen_random_uuid(), :tid, :sid, :schoolId, :yearId, 'ACTIVE', NOW())")
                        .setParameter("tid", tid).setParameter("sid", sid)
                        .setParameter("schoolId", schoolId).setParameter("yearId", toYearId)
                        .executeUpdate();
                copied++;
            }
        }
        return Response.ok(ApiResponse.success(Map.of("copied", copied))).build();
    }

    // === Student-Section Enrollment ===

    @POST
    @Path("/{sectionId}/students")
    @Transactional
    public Response enrollStudent(@PathParam("sectionId") UUID sectionId, Map<String, String> body) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff()) throw new ForbiddenException();

        String studentIdStr = body.get("studentId");
        if (studentIdStr == null) throw new ValidationException("studentId", "REQUIRED", "Student ID is required");

        UUID studentId = UUID.fromString(studentIdStr);
        UUID schoolId = requestContext.getSchoolId();
        AcademicYear activeYear = academicYearService.requireActiveYear(schoolId);

        Object classIdObj = em.createNativeQuery("SELECT class_id FROM section WHERE id = :sid")
                .setParameter("sid", sectionId)
                .getSingleResult();
        if (classIdObj == null) throw new ResourceNotFoundException("Section not found");
        UUID classId = (UUID) classIdObj;

        Long existsInClass = (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM student_section_enrollment sse " +
                "JOIN section s ON s.id = sse.section_id " +
                "WHERE sse.student_id = :studentId AND s.class_id = :classId " +
                "AND sse.academic_year_id = :yearId AND sse.status = 'ACTIVE'")
                .setParameter("studentId", studentId)
                .setParameter("classId", classId)
                .setParameter("yearId", activeYear.getId())
                .getSingleResult();
        if (existsInClass > 0) {
            throw new ConflictException("studentId", "ALREADY_ENROLLED",
                    "Student is already enrolled in a section of this class");
        }

        academicYearService.createActiveEnrollment(studentId, sectionId, schoolId, activeYear.getId());

        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.success(Map.of("studentId", studentId, "sectionId", sectionId)))
                .build();
    }

    @DELETE
    @Path("/{sectionId}/students/{studentId}")
    @Transactional
    public Response unenrollStudent(@PathParam("sectionId") UUID sectionId, @PathParam("studentId") UUID studentId) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff()) throw new ForbiddenException();
        AcademicYear activeYear = academicYearService.requireActiveYear(requestContext.getSchoolId());

        int updated = em.createNativeQuery(
                "UPDATE student_section_enrollment SET status = 'CLOSED', ended_at = NOW() " +
                "WHERE student_id = :studentId AND section_id = :sid AND academic_year_id = :yearId AND status = 'ACTIVE'")
                .setParameter("studentId", studentId)
                .setParameter("sid", sectionId)
                .setParameter("yearId", activeYear.getId())
                .executeUpdate();

        if (updated == 0) throw new ResourceNotFoundException("Enrollment not found");

        return Response.ok(ApiResponse.success(null)).build();
    }

    @GET
    @Path("/{sectionId}/students")
    public Response listStudentsForSection(@PathParam("sectionId") UUID sectionId) {
        AcademicYear activeYear = academicYearRepository.findActiveBySchoolId(requestContext.getSchoolId());
        if (activeYear == null) return Response.ok(ApiResponse.success(List.of())).build();

        if (requestContext.isTeacher()) {
            Long assigned = (Long) em.createNativeQuery(
                    "SELECT COUNT(*) FROM teacher_section_assignment WHERE teacher_id = :tid AND section_id = :sid " +
                    "AND academic_year_id = :yearId AND status = 'ACTIVE'")
                    .setParameter("tid", requestContext.getUserId())
                    .setParameter("sid", sectionId)
                    .setParameter("yearId", activeYear.getId())
                    .getSingleResult();
            if (assigned == 0) throw new ForbiddenException();
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT s.id, s.student_id, s.name, s.status FROM student s " +
                "JOIN student_section_enrollment sse ON sse.student_id = s.id " +
                "WHERE sse.section_id = :sid AND sse.academic_year_id = :yearId AND sse.status = 'ACTIVE' AND s.status = 'ACTIVE'")
                .setParameter("sid", sectionId)
                .setParameter("yearId", activeYear.getId())
                .getResultList();

        var students = rows.stream().map(r -> Map.of("id", r[0], "studentId", r[1], "name", r[2], "status", r[3])).toList();
        return Response.ok(ApiResponse.success(students)).build();
    }
}
