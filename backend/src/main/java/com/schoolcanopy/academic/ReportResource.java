package com.schoolcanopy.academic;

import java.time.LocalDate;
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
import com.schoolcanopy.common.exceptions.ValidationException;
import com.schoolcanopy.rbac.RequestContext;

@Path("/api/school/reports")
@Produces(MediaType.APPLICATION_JSON)
public class ReportResource {

    @Inject RequestContext requestContext;
    @Inject EntityManager em;
    @Inject AcademicYearRepository academicYearRepository;

    @GET
    @Path("/attendance")
    public Response attendanceReport(@QueryParam("academicYearId") UUID academicYearId,
                                     @QueryParam("classId") UUID classId,
                                     @QueryParam("sectionId") UUID sectionId,
                                     @QueryParam("from") String fromStr,
                                     @QueryParam("to") String toStr) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff()) {
            throw new ForbiddenException();
        }

        UUID schoolId = requestContext.getSchoolId();
        UUID yearId = resolveYearId(academicYearId, schoolId);
        LocalDate from = fromStr != null ? LocalDate.parse(fromStr) : LocalDate.now().minusDays(90);
        LocalDate to = toStr != null ? LocalDate.parse(toStr) : LocalDate.now();

        StringBuilder sql = new StringBuilder(
                "SELECT s.id, s.student_id, s.name, c.name, sec.name, " +
                "COUNT(a.id) as total_days, " +
                "COUNT(CASE WHEN a.status = 'PRESENT' THEN 1 END) as present_days, " +
                "COUNT(CASE WHEN a.status = 'ABSENT' THEN 1 END) as absent_days, " +
                "COUNT(CASE WHEN a.status = 'LATE' THEN 1 END) as late_days " +
                "FROM student_section_enrollment sse " +
                "JOIN student s ON s.id = sse.student_id " +
                "JOIN section sec ON sec.id = sse.section_id " +
                "JOIN \"class\" c ON c.id = sec.class_id " +
                "LEFT JOIN attendance a ON a.student_id = s.id AND a.date BETWEEN :from AND :to AND a.academic_year_id = :yearId " +
                "WHERE sse.school_id = :schoolId AND sse.academic_year_id = :yearId AND sse.status = 'ACTIVE' ");

        if (classId != null) sql.append("AND c.id = :classId ");
        if (sectionId != null) sql.append("AND sec.id = :sectionId ");

        sql.append("GROUP BY s.id, s.student_id, s.name, c.name, sec.name ORDER BY c.name, sec.name, s.name");

        var q = em.createNativeQuery(sql.toString())
                .setParameter("schoolId", schoolId)
                .setParameter("yearId", yearId)
                .setParameter("from", from)
                .setParameter("to", to);
        if (classId != null) q.setParameter("classId", classId);
        if (sectionId != null) q.setParameter("sectionId", sectionId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        var report = rows.stream().map(this::toAttendanceRow).toList();
        return Response.ok(ApiResponse.success(Map.of(
                "rows", report, "from", from.toString(), "to", to.toString()
        ))).build();
    }

    @GET
    @Path("/enrollment")
    public Response enrollmentSnapshot(@QueryParam("academicYearId") UUID academicYearId,
                                       @QueryParam("classId") UUID classId) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff()) {
            throw new ForbiddenException();
        }

        UUID schoolId = requestContext.getSchoolId();
        UUID yearId = resolveYearId(academicYearId, schoolId);

        StringBuilder sql = new StringBuilder(
                "SELECT c.id, c.name, sec.id, sec.name, COUNT(sse.id) as student_count " +
                "FROM section sec " +
                "JOIN \"class\" c ON c.id = sec.class_id " +
                "LEFT JOIN student_section_enrollment sse ON sse.section_id = sec.id " +
                "AND sse.academic_year_id = :yearId AND sse.status = 'ACTIVE' " +
                "WHERE sec.school_id = :schoolId ");
        if (classId != null) sql.append("AND c.id = :classId ");
        sql.append("GROUP BY c.id, c.name, sec.id, sec.name ORDER BY c.name, sec.name");

        var q = em.createNativeQuery(sql.toString())
                .setParameter("schoolId", schoolId)
                .setParameter("yearId", yearId);
        if (classId != null) q.setParameter("classId", classId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        var snapshot = rows.stream().map(r -> Map.of(
                "classId", r[0], "className", r[1],
                "sectionId", r[2], "sectionName", r[3],
                "studentCount", ((Number) r[4]).longValue()
        )).toList();
        return Response.ok(ApiResponse.success(Map.of("rows", snapshot))).build();
    }

    @GET
    @Path("/attendance/export")
    @Produces("text/csv")
    public Response exportAttendanceCsv(@QueryParam("academicYearId") UUID academicYearId,
                                        @QueryParam("classId") UUID classId,
                                        @QueryParam("sectionId") UUID sectionId,
                                        @QueryParam("from") String fromStr,
                                        @QueryParam("to") String toStr) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff()) {
            throw new ForbiddenException();
        }

        UUID schoolId = requestContext.getSchoolId();
        UUID yearId = resolveYearId(academicYearId, schoolId);
        LocalDate from = fromStr != null ? LocalDate.parse(fromStr) : LocalDate.now().minusDays(90);
        LocalDate to = toStr != null ? LocalDate.parse(toStr) : LocalDate.now();

        StringBuilder sql = new StringBuilder(
                "SELECT s.student_id, s.name, c.name, sec.name, " +
                "COUNT(a.id), COUNT(CASE WHEN a.status = 'PRESENT' THEN 1 END), " +
                "COUNT(CASE WHEN a.status = 'ABSENT' THEN 1 END), " +
                "COUNT(CASE WHEN a.status = 'LATE' THEN 1 END) " +
                "FROM student_section_enrollment sse " +
                "JOIN student s ON s.id = sse.student_id " +
                "JOIN section sec ON sec.id = sse.section_id " +
                "JOIN \"class\" c ON c.id = sec.class_id " +
                "LEFT JOIN attendance a ON a.student_id = s.id AND a.date BETWEEN :from AND :to AND a.academic_year_id = :yearId " +
                "WHERE sse.school_id = :schoolId AND sse.academic_year_id = :yearId AND sse.status = 'ACTIVE' ");
        if (classId != null) sql.append("AND c.id = :classId ");
        if (sectionId != null) sql.append("AND sec.id = :sectionId ");
        sql.append("GROUP BY s.student_id, s.name, c.name, sec.name ORDER BY c.name, sec.name, s.name");

        var q = em.createNativeQuery(sql.toString())
                .setParameter("schoolId", schoolId)
                .setParameter("yearId", yearId)
                .setParameter("from", from)
                .setParameter("to", to);
        if (classId != null) q.setParameter("classId", classId);
        if (sectionId != null) q.setParameter("sectionId", sectionId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();

        StringBuilder csv = new StringBuilder("Student ID,Name,Class,Section,Total Days,Present,Absent,Late,Attendance %\n");
        for (Object[] r : rows) {
            long total = ((Number) r[4]).longValue();
            long present = ((Number) r[5]).longValue();
            long absent = ((Number) r[6]).longValue();
            long late = ((Number) r[7]).longValue();
            long pct = total > 0 ? Math.round((present + late) * 100.0 / total) : 0;
            csv.append(csvEscape(r[0])).append(',')
               .append(csvEscape(r[1])).append(',')
               .append(csvEscape(r[2])).append(',')
               .append(csvEscape(r[3])).append(',')
               .append(total).append(',')
               .append(present).append(',')
               .append(absent).append(',')
               .append(late).append(',')
               .append(pct).append('\n');
        }

        return Response.ok(csv.toString())
                .header("Content-Disposition", "attachment; filename=\"attendance-report.csv\"")
                .build();
    }

    private UUID resolveYearId(UUID academicYearId, UUID schoolId) {
        if (academicYearId != null) return academicYearId;
        AcademicYear active = academicYearRepository.findActiveBySchoolId(schoolId);
        if (active == null) {
            throw new ValidationException("academicYear", "NO_ACTIVE_YEAR", "No academic year configured");
        }
        return active.getId();
    }

    private Map<String, Object> toAttendanceRow(Object[] r) {
        long total = ((Number) r[5]).longValue();
        long present = ((Number) r[6]).longValue();
        long absent = ((Number) r[7]).longValue();
        long late = ((Number) r[8]).longValue();
        var m = new java.util.HashMap<String, Object>();
        m.put("studentId", r[0]);
        m.put("studentCode", r[1]);
        m.put("studentName", r[2]);
        m.put("className", r[3]);
        m.put("sectionName", r[4]);
        m.put("totalDays", total);
        m.put("presentDays", present);
        m.put("absentDays", absent);
        m.put("lateDays", late);
        m.put("percentage", total > 0 ? Math.round((present + late) * 100.0 / total) : 0);
        return m;
    }

    private String csvEscape(Object val) {
        if (val == null) return "";
        String s = val.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
