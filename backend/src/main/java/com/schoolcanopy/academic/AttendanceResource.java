package com.schoolcanopy.academic;

import java.time.LocalDate;
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
import com.schoolcanopy.common.exceptions.ForbiddenException;
import com.schoolcanopy.common.exceptions.ValidationException;
import com.schoolcanopy.rbac.RequestContext;

@Path("/api/school/attendance")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AttendanceResource {

    @Inject RequestContext requestContext;
    @Inject EntityManager em;
    @Inject AcademicYearService academicYearService;
    @Inject AcademicYearRepository academicYearRepository;

    @POST
    @Transactional
    public Response markAttendance(Map<String, Object> body) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isTeacher()) {
            throw new ForbiddenException();
        }

        String sectionIdStr = (String) body.get("sectionId");
        String dateStr = (String) body.get("date");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> records = (List<Map<String, String>>) body.get("records");

        if (sectionIdStr == null || dateStr == null || records == null || records.isEmpty()) {
            throw new ValidationException("fields", "REQUIRED", "sectionId, date, and records are required");
        }

        UUID sectionId = UUID.fromString(sectionIdStr);
        UUID schoolId = requestContext.getSchoolId();
        AcademicYear activeYear = academicYearService.requireActiveYear(schoolId);
        academicYearService.requireWritableYear(activeYear.getId(), schoolId);

        if (requestContext.isTeacher()) {
            verifyTeacherSectionAccess(sectionId, activeYear.getId());
        }

        LocalDate date = LocalDate.parse(dateStr);
        UUID markedBy = requestContext.getUserId();

        em.createNativeQuery("DELETE FROM attendance WHERE section_id = :sid AND date = :date AND academic_year_id = :yearId")
                .setParameter("sid", sectionId)
                .setParameter("date", date)
                .setParameter("yearId", activeYear.getId())
                .executeUpdate();

        for (Map<String, String> record : records) {
            String studentIdStr = record.get("studentId");
            String status = record.getOrDefault("status", "PRESENT");
            if (!List.of("PRESENT", "ABSENT", "LATE").contains(status.toUpperCase())) status = "PRESENT";

            em.createNativeQuery(
                    "INSERT INTO attendance (id, school_id, student_id, section_id, academic_year_id, date, status, marked_by, created_at) " +
                    "VALUES (gen_random_uuid(), :schoolId, :studentId, :sectionId, :yearId, :date, :status, :markedBy, NOW())")
                    .setParameter("schoolId", schoolId)
                    .setParameter("studentId", UUID.fromString(studentIdStr))
                    .setParameter("sectionId", sectionId)
                    .setParameter("yearId", activeYear.getId())
                    .setParameter("date", date)
                    .setParameter("status", status.toUpperCase())
                    .setParameter("markedBy", markedBy)
                    .executeUpdate();
        }

        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.success(Map.of("sectionId", sectionId, "date", dateStr, "count", records.size())))
                .build();
    }

    @GET
    public Response getAttendance(@QueryParam("sectionId") String sectionIdStr, @QueryParam("date") String dateStr) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isTeacher()) {
            throw new ForbiddenException();
        }
        if (sectionIdStr == null || dateStr == null) {
            throw new ValidationException("params", "REQUIRED", "sectionId and date are required");
        }

        UUID sectionId = UUID.fromString(sectionIdStr);
        AcademicYear activeYear = academicYearRepository.findActiveBySchoolId(requestContext.getSchoolId());
        if (activeYear == null) return Response.ok(ApiResponse.success(List.of())).build();

        if (requestContext.isTeacher()) verifyTeacherSectionAccess(sectionId, activeYear.getId());
        LocalDate date = LocalDate.parse(dateStr);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT a.student_id, s.name, s.student_id as sid, a.status " +
                "FROM attendance a JOIN student s ON s.id = a.student_id " +
                "WHERE a.section_id = :sectionId AND a.date = :date AND a.academic_year_id = :yearId " +
                "ORDER BY s.name")
                .setParameter("sectionId", sectionId)
                .setParameter("date", date)
                .setParameter("yearId", activeYear.getId())
                .getResultList();

        var attendance = rows.stream().map(r -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("studentId", r[0]);
            m.put("studentName", r[1]);
            m.put("studentCode", r[2]);
            m.put("status", r[3]);
            return m;
        }).toList();

        return Response.ok(ApiResponse.success(attendance)).build();
    }

    @GET
    @Path("/summary")
    public Response getSummary(@QueryParam("sectionId") String sectionIdStr,
                              @QueryParam("from") String fromStr,
                              @QueryParam("to") String toStr,
                              @QueryParam("academicYearId") UUID academicYearId) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isTeacher()) {
            throw new ForbiddenException();
        }

        UUID sectionId = UUID.fromString(sectionIdStr);
        UUID yearId = academicYearId;
        if (yearId == null) {
            AcademicYear active = academicYearRepository.findActiveBySchoolId(requestContext.getSchoolId());
            if (active == null) return Response.ok(ApiResponse.success(List.of())).build();
            yearId = active.getId();
        }

        if (requestContext.isTeacher()) verifyTeacherSectionAccess(sectionId, yearId);
        LocalDate from = fromStr != null ? LocalDate.parse(fromStr) : LocalDate.now().minusDays(30);
        LocalDate to = toStr != null ? LocalDate.parse(toStr) : LocalDate.now();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT s.id, s.name, s.student_id, " +
                "COUNT(a.id) as total_days, " +
                "COUNT(CASE WHEN a.status = 'PRESENT' THEN 1 END) as present_days, " +
                "COUNT(CASE WHEN a.status = 'ABSENT' THEN 1 END) as absent_days, " +
                "COUNT(CASE WHEN a.status = 'LATE' THEN 1 END) as late_days " +
                "FROM student_section_enrollment sse " +
                "JOIN student s ON s.id = sse.student_id " +
                "LEFT JOIN attendance a ON a.student_id = s.id AND a.date BETWEEN :from AND :to AND a.academic_year_id = :yearId " +
                "WHERE sse.section_id = :sectionId AND sse.academic_year_id = :yearId AND sse.status = 'ACTIVE' " +
                "GROUP BY s.id, s.name, s.student_id ORDER BY s.name")
                .setParameter("sectionId", sectionId)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("yearId", yearId)
                .getResultList();

        var summary = rows.stream().map(r -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("studentId", r[0]);
            m.put("studentName", r[1]);
            m.put("studentCode", r[2]);
            long totalDays = ((Number) r[3]).longValue();
            long presentDays = ((Number) r[4]).longValue();
            long absentDays = ((Number) r[5]).longValue();
            long lateDays = ((Number) r[6]).longValue();
            m.put("totalDays", totalDays);
            m.put("presentDays", presentDays);
            m.put("absentDays", absentDays);
            m.put("lateDays", lateDays);
            m.put("percentage", totalDays > 0 ? Math.round((presentDays + lateDays) * 100.0 / totalDays) : 0);
            return m;
        }).toList();

        return Response.ok(ApiResponse.success(summary)).build();
    }

    private void verifyTeacherSectionAccess(UUID sectionId, UUID yearId) {
        Long assigned = (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM teacher_section_assignment WHERE teacher_id = :tid AND section_id = :sid " +
                "AND academic_year_id = :yearId AND status = 'ACTIVE'")
                .setParameter("tid", requestContext.getUserId())
                .setParameter("sid", sectionId)
                .setParameter("yearId", yearId)
                .getSingleResult();
        if (assigned == 0) throw new ForbiddenException();
    }
}
