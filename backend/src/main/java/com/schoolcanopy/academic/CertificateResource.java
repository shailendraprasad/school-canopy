package com.schoolcanopy.academic;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.schoolcanopy.common.ApiResponse;
import com.schoolcanopy.common.exceptions.ForbiddenException;
import com.schoolcanopy.common.exceptions.ResourceNotFoundException;
import com.schoolcanopy.rbac.RequestContext;
import com.schoolcanopy.school.School;
import com.schoolcanopy.school.SchoolRepository;

@Path("/api/school/certificates")
@Produces(MediaType.APPLICATION_JSON)
public class CertificateResource {

    @Inject RequestContext requestContext;
    @Inject EntityManager em;
    @Inject AcademicYearRepository academicYearRepository;
    @Inject StudentRepository studentRepository;
    @Inject SchoolRepository schoolRepository;

    @GET
    @Path("/{studentId}")
    public Response getCertificate(@PathParam("studentId") UUID studentId,
                                   @QueryParam("academicYearId") UUID academicYearId,
                                   @QueryParam("type") @DefaultValue("TRANSFER") String type) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff()) {
            throw new ForbiddenException();
        }

        Student student = studentRepository.findById(studentId);
        if (student == null) throw new ResourceNotFoundException("Student not found");

        UUID yearId = academicYearId;
        if (yearId == null) {
            AcademicYear active = academicYearRepository.findActiveBySchoolId(requestContext.getSchoolId());
            if (active == null) throw new ResourceNotFoundException("No academic year found");
            yearId = active.getId();
        }

        AcademicYear year = academicYearRepository.findById(yearId);
        if (year == null) throw new ResourceNotFoundException("Academic year not found");

        School school = schoolRepository.findById(student.getSchoolId());

        @SuppressWarnings("unchecked")
        List<Object[]> enrollmentRows = em.createNativeQuery(
                "SELECT c.name, sec.name, sse.enrolled_at, sse.ended_at, sse.status " +
                "FROM student_section_enrollment sse " +
                "JOIN section sec ON sec.id = sse.section_id " +
                "JOIN \"class\" c ON c.id = sec.class_id " +
                "WHERE sse.student_id = :sid AND sse.academic_year_id = :yearId " +
                "ORDER BY sse.enrolled_at DESC LIMIT 1")
                .setParameter("sid", studentId)
                .setParameter("yearId", yearId)
                .getResultList();

        String className = null, sectionName = null, enrolledAt = null, endedAt = null, enrollmentStatus = null;
        if (!enrollmentRows.isEmpty()) {
            Object[] e = enrollmentRows.get(0);
            className = (String) e[0];
            sectionName = (String) e[1];
            enrolledAt = e[2] != null ? e[2].toString() : null;
            endedAt = e[3] != null ? e[3].toString() : null;
            enrollmentStatus = (String) e[4];
        }

        @SuppressWarnings("unchecked")
        List<Object[]> attRows = em.createNativeQuery(
                "SELECT COUNT(a.id), " +
                "COUNT(CASE WHEN a.status = 'PRESENT' THEN 1 END), " +
                "COUNT(CASE WHEN a.status = 'ABSENT' THEN 1 END), " +
                "COUNT(CASE WHEN a.status = 'LATE' THEN 1 END) " +
                "FROM attendance a WHERE a.student_id = :sid AND a.academic_year_id = :yearId")
                .setParameter("sid", studentId)
                .setParameter("yearId", yearId)
                .getResultList();

        long totalDays = 0, presentDays = 0, absentDays = 0, lateDays = 0;
        if (!attRows.isEmpty()) {
            Object[] a = attRows.get(0);
            totalDays = ((Number) a[0]).longValue();
            presentDays = ((Number) a[1]).longValue();
            absentDays = ((Number) a[2]).longValue();
            lateDays = ((Number) a[3]).longValue();
        }

        var cert = new java.util.HashMap<String, Object>();
        cert.put("type", type.toUpperCase());
        cert.put("schoolName", school != null ? school.getName() : "");
        cert.put("studentName", student.getFirstName() + " " + student.getLastName());
        cert.put("studentCode", student.getStudentId());
        cert.put("studentStatus", student.getStatus());
        cert.put("academicYearName", year.getName());
        cert.put("academicYearStart", year.getStartsOn().toString());
        cert.put("academicYearEnd", year.getEndsOn().toString());
        cert.put("className", className);
        cert.put("sectionName", sectionName);
        cert.put("enrolledAt", enrolledAt);
        cert.put("endedAt", endedAt);
        cert.put("enrollmentStatus", enrollmentStatus);
        cert.put("attendance", Map.of(
                "totalDays", totalDays,
                "presentDays", presentDays,
                "absentDays", absentDays,
                "lateDays", lateDays,
                "percentage", totalDays > 0 ? Math.round((presentDays + lateDays) * 100.0 / totalDays) : 0
        ));
        cert.put("issuedAt", java.time.LocalDate.now().toString());

        return Response.ok(ApiResponse.success(cert)).build();
    }
}
