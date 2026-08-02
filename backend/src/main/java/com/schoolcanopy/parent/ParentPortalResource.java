package com.schoolcanopy.parent;

import java.time.LocalDate;
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
import com.schoolcanopy.common.exceptions.ForbiddenException;
import com.schoolcanopy.common.exceptions.ValidationException;
import com.schoolcanopy.academic.AcademicYear;
import com.schoolcanopy.academic.AcademicYearRepository;
import com.schoolcanopy.rbac.RequestContext;

@Path("/api/parent")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ParentPortalResource {

    @Inject RequestContext requestContext;
    @Inject EntityManager em;
    @Inject AcademicYearRepository academicYearRepository;

    private void requireParent() {
        if (!"PARENT".equals(requestContext.getCurrentUserRole())) {
            throw new ForbiddenException();
        }
    }

    @GET
    @Path("/children")
    public Response getChildren() {
        requireParent();
        UUID parentId = requestContext.getUserId();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT s.id, s.student_id, s.name, s.status, " +
                "c.name AS class_name, sec.name AS section_name " +
                "FROM parent_student_link psl " +
                "JOIN student s ON s.id = psl.student_id " +
                "LEFT JOIN student_section_enrollment sse ON sse.student_id = s.id AND sse.status = 'ACTIVE' " +
                "AND sse.academic_year_id IN (SELECT ay.id FROM academic_year ay WHERE ay.school_id = s.school_id AND ay.status = 'ACTIVE') " +
                "LEFT JOIN section sec ON sec.id = sse.section_id " +
                "LEFT JOIN \"class\" c ON c.id = sec.class_id " +
                "WHERE psl.parent_id = :parentId AND s.status = 'ACTIVE'")
                .setParameter("parentId", parentId)
                .getResultList();

        var children = rows.stream().map(r -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("id", r[0]);
            m.put("studentId", r[1]);
            m.put("name", r[2]);
            m.put("status", r[3]);
            m.put("className", r[4]);
            m.put("sectionName", r[5]);
            return m;
        }).toList();

        return Response.ok(ApiResponse.success(children)).build();
    }

    @GET
    @Path("/academic-years")
    public Response getAcademicYears() {
        requireParent();
        UUID parentId = requestContext.getUserId();

        @SuppressWarnings("unchecked")
        List<Object[]> schoolRows = em.createNativeQuery(
                "SELECT DISTINCT s.school_id FROM student s " +
                "JOIN parent_student_link psl ON psl.student_id = s.id " +
                "WHERE psl.parent_id = :parentId")
                .setParameter("parentId", parentId)
                .getResultList();

        if (schoolRows.isEmpty()) {
            return Response.ok(ApiResponse.success(List.of())).build();
        }

        UUID schoolId = (UUID) schoolRows.get(0)[0];
        List<AcademicYear> years = academicYearRepository.find("schoolId = ?1 ORDER BY startsOn DESC", schoolId).list();
        var dtos = years.stream().map(y -> Map.of(
                "id", y.getId(),
                "name", y.getName(),
                "status", y.getStatus(),
                "startsOn", y.getStartsOn().toString(),
                "endsOn", y.getEndsOn().toString()
        )).toList();
        return Response.ok(ApiResponse.success(dtos)).build();
    }

    @GET
    @Path("/announcements")
    public Response getAnnouncements() {
        requireParent();
        UUID parentId = requestContext.getUserId();

        // Get announcements visible to this parent's children
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT DISTINCT a.id, a.title, a.body, a.category, a.scope_type, a.publish_at, a.created_at " +
                "FROM announcement a " +
                "WHERE a.status = 'PUBLISHED' AND (" +
                "  (a.scope_type = 'SCHOOL' AND a.school_id IN (" +
                "    SELECT DISTINCT s.school_id FROM student s " +
                "    JOIN parent_student_link psl ON psl.student_id = s.id WHERE psl.parent_id = :pid)) " +
                "  OR (a.scope_type = 'SECTION' AND a.scope_id IN (" +
                "    SELECT DISTINCT sse.section_id FROM student_section_enrollment sse " +
                "    JOIN parent_student_link psl ON psl.student_id = sse.student_id WHERE psl.parent_id = :pid)) " +
                "  OR (a.scope_type = 'CLASS' AND a.scope_id IN (" +
                "    SELECT DISTINCT sec.class_id FROM student_section_enrollment sse " +
                "    JOIN section sec ON sec.id = sse.section_id " +
                "    JOIN parent_student_link psl ON psl.student_id = sse.student_id WHERE psl.parent_id = :pid)) " +
                ") ORDER BY a.publish_at DESC NULLS LAST, a.created_at DESC")
                .setParameter("pid", parentId)
                .getResultList();

        var announcements = rows.stream().map(r -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("id", r[0]);
            m.put("title", r[1]);
            m.put("body", r[2]);
            m.put("category", r[3]);
            m.put("scopeType", r[4]);
            m.put("publishAt", r[5] != null ? r[5].toString() : null);
            m.put("createdAt", r[6] != null ? r[6].toString() : null);
            return m;
        }).toList();

        return Response.ok(ApiResponse.success(announcements)).build();
    }

    @GET
    @Path("/events")
    public Response getEvents() {
        requireParent();
        UUID parentId = requestContext.getUserId();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT DISTINCT e.id, e.title, e.event_date, e.start_time, e.end_time, e.location, e.scope_type " +
                "FROM calendar_event e " +
                "WHERE e.event_date >= :today AND e.academic_year_id IN (" +
                "  SELECT ay.id FROM academic_year ay WHERE ay.school_id IN (" +
                "    SELECT DISTINCT s.school_id FROM student s " +
                "    JOIN parent_student_link psl ON psl.student_id = s.id WHERE psl.parent_id = :pid) AND ay.status = 'ACTIVE') " +
                "AND (" +
                "  (e.scope_type = 'SCHOOL' AND e.school_id IN (" +
                "    SELECT DISTINCT s.school_id FROM student s " +
                "    JOIN parent_student_link psl ON psl.student_id = s.id WHERE psl.parent_id = :pid)) " +
                "  OR (e.scope_type = 'SECTION' AND e.scope_id IN (" +
                "    SELECT DISTINCT sse.section_id FROM student_section_enrollment sse " +
                "    JOIN parent_student_link psl ON psl.student_id = sse.student_id " +
                "    WHERE psl.parent_id = :pid AND sse.status = 'ACTIVE' " +
                "    AND sse.academic_year_id IN (SELECT ay.id FROM academic_year ay WHERE ay.school_id = sse.school_id AND ay.status = 'ACTIVE'))) " +
                "  OR (e.scope_type = 'CLASS' AND e.scope_id IN (" +
                "    SELECT DISTINCT sec.class_id FROM student_section_enrollment sse " +
                "    JOIN section sec ON sec.id = sse.section_id " +
                "    JOIN parent_student_link psl ON psl.student_id = sse.student_id " +
                "    WHERE psl.parent_id = :pid AND sse.status = 'ACTIVE' " +
                "    AND sse.academic_year_id IN (SELECT ay.id FROM academic_year ay WHERE ay.school_id = sse.school_id AND ay.status = 'ACTIVE'))) " +
                ") ORDER BY e.event_date ASC")
                .setParameter("pid", parentId)
                .setParameter("today", LocalDate.now())
                .getResultList();

        var events = rows.stream().map(r -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("id", r[0]);
            m.put("title", r[1]);
            m.put("eventDate", r[2] != null ? r[2].toString() : null);
            m.put("startTime", r[3] != null ? r[3].toString() : null);
            m.put("endTime", r[4] != null ? r[4].toString() : null);
            m.put("location", r[5]);
            m.put("scopeType", r[6]);
            return m;
        }).toList();

        return Response.ok(ApiResponse.success(events)).build();
    }

    @GET
    @Path("/messages")
    public Response getMessageThreads() {
        requireParent();
        UUID parentId = requestContext.getUserId();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT mt.id, mt.subject, u.name as staff_name, s.name as student_name, mt.last_message_at " +
                "FROM message_thread mt " +
                "JOIN user_account u ON u.id = mt.staff_id " +
                "JOIN student s ON s.id = mt.student_id " +
                "WHERE mt.parent_id = :pid " +
                "ORDER BY mt.last_message_at DESC NULLS LAST")
                .setParameter("pid", parentId)
                .getResultList();

        var threads = rows.stream().map(r -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("id", r[0]);
            m.put("subject", r[1]);
            m.put("staffName", r[2] != null ? r[2] : "School Staff");
            m.put("studentName", r[3] != null ? r[3] : "");
            m.put("lastMessageAt", r[4] != null ? r[4].toString() : null);
            return m;
        }).toList();

        return Response.ok(ApiResponse.success(threads)).build();
    }

    @GET
    @Path("/messages/{threadId}")
    public Response getThreadMessages(@PathParam("threadId") UUID threadId) {
        requireParent();
        UUID parentId = requestContext.getUserId();

        // Verify parent owns this thread
        Long count = (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM message_thread WHERE id = :tid AND parent_id = :pid")
                .setParameter("tid", threadId)
                .setParameter("pid", parentId)
                .getSingleResult();
        if (count == 0) throw new ForbiddenException();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT m.id, m.body, m.sender_id, u.name, m.created_at, m.read_by_recipient " +
                "FROM message m JOIN user_account u ON u.id = m.sender_id " +
                "WHERE m.thread_id = :tid ORDER BY m.created_at ASC")
                .setParameter("tid", threadId)
                .getResultList();

        var messages = rows.stream().map(r -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("id", r[0]);
            m.put("body", r[1]);
            m.put("senderId", r[2]);
            m.put("senderName", r[3]);
            m.put("createdAt", r[4] != null ? r[4].toString() : null);
            m.put("isMe", r[2].equals(parentId));
            return m;
        }).toList();

        return Response.ok(ApiResponse.success(messages)).build();
    }

    @GET
    @Path("/children/{childId}/attendance")
    public Response getChildAttendance(@PathParam("childId") UUID childId,
                                       @QueryParam("academicYearId") UUID academicYearId) {
        requireParent();
        UUID parentId = requestContext.getUserId();

        Long linked = (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM parent_student_link WHERE parent_id = :pid AND student_id = :sid")
                .setParameter("pid", parentId)
                .setParameter("sid", childId)
                .getSingleResult();
        if (linked == 0) throw new ForbiddenException();

        UUID yearId = academicYearId;
        if (yearId == null) {
            Object schoolIdObj = em.createNativeQuery("SELECT school_id FROM student WHERE id = :sid")
                    .setParameter("sid", childId).getSingleResult();
            AcademicYear active = academicYearRepository.findActiveBySchoolId((UUID) schoolIdObj);
            if (active == null) return Response.ok(ApiResponse.success(Map.of())).build();
            yearId = active.getId();
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT COUNT(a.id) as total, " +
                "COUNT(CASE WHEN a.status = 'PRESENT' THEN 1 END) as present, " +
                "COUNT(CASE WHEN a.status = 'ABSENT' THEN 1 END) as absent, " +
                "COUNT(CASE WHEN a.status = 'LATE' THEN 1 END) as late " +
                "FROM attendance a WHERE a.student_id = :sid AND a.academic_year_id = :yearId AND a.date >= :fromDate")
                .setParameter("sid", childId)
                .setParameter("yearId", yearId)
                .setParameter("fromDate", LocalDate.now().minusDays(30))
                .getResultList();

        var m = new java.util.HashMap<String, Object>();
        if (!rows.isEmpty()) {
            Object[] r = rows.get(0);
            long total = ((Number) r[0]).longValue();
            long present = ((Number) r[1]).longValue();
            long absent = ((Number) r[2]).longValue();
            long late = ((Number) r[3]).longValue();
            m.put("totalDays", total);
            m.put("presentDays", present);
            m.put("absentDays", absent);
            m.put("lateDays", late);
            m.put("percentage", total > 0 ? Math.round((present + late) * 100.0 / total) : 0);
        } else {
            m.put("totalDays", 0); m.put("presentDays", 0); m.put("absentDays", 0); m.put("lateDays", 0); m.put("percentage", 0);
        }
        return Response.ok(ApiResponse.success(m)).build();
    }

    @GET
    @Path("/teachers")
    public Response getTeachers() {
        requireParent();
        UUID parentId = requestContext.getUserId();

        // Get teachers assigned to sections where the parent's children are enrolled
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT DISTINCT u.id, u.name, u.email, s.name as student_name, s.id as student_id " +
                "FROM user_account u " +
                "JOIN teacher_section_assignment tsa ON tsa.teacher_id = u.id AND tsa.status = 'ACTIVE' " +
                "JOIN student_section_enrollment sse ON sse.section_id = tsa.section_id AND sse.status = 'ACTIVE' " +
                "JOIN student s ON s.id = sse.student_id " +
                "JOIN parent_student_link psl ON psl.student_id = s.id " +
                "WHERE psl.parent_id = :pid AND u.status = 'ACTIVE' " +
                "AND tsa.academic_year_id IN (SELECT ay.id FROM academic_year ay WHERE ay.school_id = s.school_id AND ay.status = 'ACTIVE') " +
                "AND sse.academic_year_id IN (SELECT ay.id FROM academic_year ay WHERE ay.school_id = s.school_id AND ay.status = 'ACTIVE') " +
                "ORDER BY u.name")
                .setParameter("pid", parentId)
                .getResultList();

        var teachers = rows.stream().map(r -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("teacherId", r[0]);
            m.put("teacherName", r[1] != null ? r[1] : "Teacher");
            m.put("teacherEmail", r[2]);
            m.put("studentName", r[3]);
            m.put("studentId", r[4]);
            return m;
        }).toList();

        return Response.ok(ApiResponse.success(teachers)).build();
    }

    @POST
    @Path("/messages")
    @Transactional
    public Response createThread(Map<String, String> body) {
        requireParent();
        UUID parentId = requestContext.getUserId();

        String staffIdStr = body.get("staffId");
        String studentIdStr = body.get("studentId");
        String subject = body.get("subject");
        String msgBody = body.get("body");

        if (staffIdStr == null || studentIdStr == null || subject == null || msgBody == null) {
            throw new ValidationException("fields", "REQUIRED", "All fields are required");
        }

        UUID staffId = UUID.fromString(staffIdStr);
        UUID studentId = UUID.fromString(studentIdStr);

        // Get student's school_id
        Object schoolIdObj = em.createNativeQuery("SELECT school_id FROM student WHERE id = :sid")
                .setParameter("sid", studentId).getSingleResult();
        UUID schoolId = (UUID) schoolIdObj;

        UUID threadId = UUID.randomUUID();
        em.createNativeQuery(
                "INSERT INTO message_thread (id, school_id, staff_id, parent_id, student_id, subject, created_at, last_message_at) " +
                "VALUES (:id, :schoolId, :staffId, :parentId, :studentId, :subject, NOW(), NOW())")
                .setParameter("id", threadId)
                .setParameter("schoolId", schoolId)
                .setParameter("staffId", staffId)
                .setParameter("parentId", parentId)
                .setParameter("studentId", studentId)
                .setParameter("subject", subject.trim())
                .executeUpdate();

        UUID messageId = UUID.randomUUID();
        em.createNativeQuery(
                "INSERT INTO message (id, thread_id, sender_id, body, read_by_recipient, created_at) " +
                "VALUES (:id, :tid, :pid, :body, false, NOW())")
                .setParameter("id", messageId)
                .setParameter("tid", threadId)
                .setParameter("pid", parentId)
                .setParameter("body", msgBody.trim())
                .executeUpdate();

        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.success(Map.of("threadId", threadId, "subject", subject)))
                .build();
    }

    @POST
    @Path("/messages/{threadId}/replies")
    @Transactional
    public Response reply(@PathParam("threadId") UUID threadId, Map<String, String> body) {
        requireParent();
        UUID parentId = requestContext.getUserId();

        // Verify parent owns this thread
        Long count = (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM message_thread WHERE id = :tid AND parent_id = :pid")
                .setParameter("tid", threadId)
                .setParameter("pid", parentId)
                .getSingleResult();
        if (count == 0) throw new ForbiddenException();

        String msgBody = body.get("body");
        if (msgBody == null || msgBody.isBlank()) {
            throw new ValidationException("body", "REQUIRED", "Message body is required");
        }

        UUID messageId = UUID.randomUUID();
        em.createNativeQuery(
                "INSERT INTO message (id, thread_id, sender_id, body, read_by_recipient, created_at) " +
                "VALUES (:id, :tid, :pid, :body, false, NOW())")
                .setParameter("id", messageId)
                .setParameter("tid", threadId)
                .setParameter("pid", parentId)
                .setParameter("body", msgBody.trim())
                .executeUpdate();

        em.createNativeQuery("UPDATE message_thread SET last_message_at = NOW() WHERE id = :tid")
                .setParameter("tid", threadId)
                .executeUpdate();

        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.success(Map.of("messageId", messageId, "threadId", threadId)))
                .build();
    }
}
