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

@Path("/api/school/section-enrollments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EnrollmentResource {

    @Inject RequestContext requestContext;
    @Inject EntityManager em;

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

        // Check if already assigned
        Long exists = (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM teacher_section_assignment WHERE teacher_id = :tid AND section_id = :sid")
                .setParameter("tid", teacherId)
                .setParameter("sid", sectionId)
                .getSingleResult();
        if (exists > 0) {
            throw new ConflictException("teacherId", "ALREADY_ASSIGNED", "Teacher is already assigned to this section");
        }

        em.createNativeQuery(
                "INSERT INTO teacher_section_assignment (id, teacher_id, section_id, school_id, assigned_at) VALUES (gen_random_uuid(), :tid, :sid, :schoolId, NOW())")
                .setParameter("tid", teacherId)
                .setParameter("sid", sectionId)
                .setParameter("schoolId", schoolId)
                .executeUpdate();

        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.success(Map.of("teacherId", teacherId, "sectionId", sectionId)))
                .build();
    }

    @GET
    @Path("/{sectionId}/teachers")
    public Response listTeachersForSection(@PathParam("sectionId") UUID sectionId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT u.id, u.name, u.email FROM user_account u " +
                "JOIN teacher_section_assignment tsa ON tsa.teacher_id = u.id " +
                "WHERE tsa.section_id = :sid")
                .setParameter("sid", sectionId)
                .getResultList();

        var teachers = rows.stream().map(r -> Map.of("id", r[0], "name", r[1], "email", r[2])).toList();
        return Response.ok(ApiResponse.success(teachers)).build();
    }

    @DELETE
    @Path("/{sectionId}/teachers/{teacherId}")
    @Transactional
    public Response unassignTeacher(@PathParam("sectionId") UUID sectionId, @PathParam("teacherId") UUID teacherId) {
        if (!requestContext.isSchoolAdministrator()) throw new ForbiddenException();

        int deleted = em.createNativeQuery(
                "DELETE FROM teacher_section_assignment WHERE teacher_id = :tid AND section_id = :sid")
                .setParameter("tid", teacherId)
                .setParameter("sid", sectionId)
                .executeUpdate();

        if (deleted == 0) throw new ResourceNotFoundException("Assignment not found");
        return Response.ok(ApiResponse.success(null)).build();
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

        // Get the class_id for this section
        Object classIdObj = em.createNativeQuery("SELECT class_id FROM section WHERE id = :sid")
                .setParameter("sid", sectionId)
                .getSingleResult();
        if (classIdObj == null) throw new ResourceNotFoundException("Section not found");
        UUID classId = (UUID) classIdObj;

        // Check one-section-per-class constraint
        Long existsInClass = (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM student_section_enrollment sse " +
                "JOIN section s ON s.id = sse.section_id " +
                "WHERE sse.student_id = :studentId AND s.class_id = :classId")
                .setParameter("studentId", studentId)
                .setParameter("classId", classId)
                .getSingleResult();
        if (existsInClass > 0) {
            throw new ConflictException("studentId", "ALREADY_ENROLLED",
                    "Student is already enrolled in a section of this class");
        }

        em.createNativeQuery(
                "INSERT INTO student_section_enrollment (id, student_id, section_id, school_id, enrolled_at) VALUES (gen_random_uuid(), :studentId, :sid, :schoolId, NOW())")
                .setParameter("studentId", studentId)
                .setParameter("sid", sectionId)
                .setParameter("schoolId", schoolId)
                .executeUpdate();

        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.success(Map.of("studentId", studentId, "sectionId", sectionId)))
                .build();
    }

    @DELETE
    @Path("/{sectionId}/students/{studentId}")
    @Transactional
    public Response unenrollStudent(@PathParam("sectionId") UUID sectionId, @PathParam("studentId") UUID studentId) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff()) throw new ForbiddenException();

        int deleted = em.createNativeQuery(
                "DELETE FROM student_section_enrollment WHERE student_id = :studentId AND section_id = :sid")
                .setParameter("studentId", studentId)
                .setParameter("sid", sectionId)
                .executeUpdate();

        if (deleted == 0) throw new ResourceNotFoundException("Enrollment not found");

        return Response.ok(ApiResponse.success(null)).build();
    }

    @GET
    @Path("/{sectionId}/students")
    public Response listStudentsForSection(@PathParam("sectionId") UUID sectionId) {
        // Teachers: verify they are assigned to this section
        if (requestContext.isTeacher()) {
            Long assigned = (Long) em.createNativeQuery(
                    "SELECT COUNT(*) FROM teacher_section_assignment WHERE teacher_id = :tid AND section_id = :sid")
                    .setParameter("tid", requestContext.getUserId())
                    .setParameter("sid", sectionId)
                    .getSingleResult();
            if (assigned == 0) {
                throw new ForbiddenException();
            }
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT s.id, s.student_id, s.name, s.status FROM student s " +
                "JOIN student_section_enrollment sse ON sse.student_id = s.id " +
                "WHERE sse.section_id = :sid AND s.status = 'ACTIVE'")
                .setParameter("sid", sectionId)
                .getResultList();

        var students = rows.stream().map(r -> Map.of("id", r[0], "studentId", r[1], "name", r[2], "status", r[3])).toList();
        return Response.ok(ApiResponse.success(students)).build();
    }
}
