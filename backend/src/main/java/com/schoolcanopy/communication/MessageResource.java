package com.schoolcanopy.communication;

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
import com.schoolcanopy.common.PaginationMeta;
import com.schoolcanopy.common.PaginationParams;
import com.schoolcanopy.common.exceptions.ForbiddenException;
import com.schoolcanopy.common.exceptions.ResourceNotFoundException;
import com.schoolcanopy.common.exceptions.ValidationException;
import com.schoolcanopy.config.ConfigService;
import com.schoolcanopy.rbac.RequestContext;

@Path("/api/school/messages")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MessageResource {

    @Inject RequestContext requestContext;
    @Inject ConfigService configService;
    @Inject EntityManager em;

    @POST
    @Transactional
    public Response createThread(Map<String, String> body) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isTeacher()) throw new ForbiddenException();

        String parentId = body.get("parentId");
        String studentId = body.get("studentId");
        String subject = body.get("subject");
        String messageBody = body.get("body");

        if (parentId == null) throw new ValidationException("parentId", "REQUIRED", "Parent is required");
        if (studentId == null) throw new ValidationException("studentId", "REQUIRED", "Student is required");
        if (subject == null || subject.isBlank() || subject.length() > 200)
            throw new ValidationException("subject", "INVALID", "Subject is required (max 200 characters)");

        int maxLength = configService.getMessageMaxLength();
        if (messageBody == null || messageBody.isBlank())
            throw new ValidationException("body", "REQUIRED", "Message body is required");
        if (messageBody.length() > maxLength)
            throw new ValidationException("body", "TOO_LONG", "Message exceeds maximum length of " + maxLength);

        UUID schoolId = requestContext.getSchoolId();
        UUID staffId = requestContext.getUserId();

        // Create thread
        UUID threadId = UUID.randomUUID();
        em.createNativeQuery(
                "INSERT INTO message_thread (id, school_id, staff_id, parent_id, student_id, subject, created_at, last_message_at) " +
                "VALUES (:id, :schoolId, :staffId, :parentId, :studentId, :subject, NOW(), NOW())")
                .setParameter("id", threadId)
                .setParameter("schoolId", schoolId)
                .setParameter("staffId", staffId)
                .setParameter("parentId", UUID.fromString(parentId))
                .setParameter("studentId", UUID.fromString(studentId))
                .setParameter("subject", subject.trim())
                .executeUpdate();

        // Create first message
        UUID messageId = UUID.randomUUID();
        em.createNativeQuery(
                "INSERT INTO message (id, thread_id, sender_id, body, read_by_recipient, created_at) " +
                "VALUES (:id, :threadId, :senderId, :body, false, NOW())")
                .setParameter("id", messageId)
                .setParameter("threadId", threadId)
                .setParameter("senderId", staffId)
                .setParameter("body", messageBody.trim())
                .executeUpdate();

        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.success(Map.of("threadId", threadId, "subject", subject)))
                .build();
    }

    @POST
    @Path("/{threadId}/replies")
    @Transactional
    public Response reply(@PathParam("threadId") UUID threadId, Map<String, String> body) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isTeacher()) throw new ForbiddenException();

        String messageBody = body.get("body");
        int maxLength = configService.getMessageMaxLength();

        if (messageBody == null || messageBody.isBlank())
            throw new ValidationException("body", "REQUIRED", "Message body is required");
        if (messageBody.length() > maxLength)
            throw new ValidationException("body", "TOO_LONG", "Message exceeds maximum length of " + maxLength);

        UUID messageId = UUID.randomUUID();
        em.createNativeQuery(
                "INSERT INTO message (id, thread_id, sender_id, body, read_by_recipient, created_at) " +
                "VALUES (:id, :threadId, :senderId, :body, false, NOW())")
                .setParameter("id", messageId)
                .setParameter("threadId", threadId)
                .setParameter("senderId", requestContext.getUserId())
                .setParameter("body", messageBody.trim())
                .executeUpdate();

        // Update last_message_at
        em.createNativeQuery("UPDATE message_thread SET last_message_at = NOW() WHERE id = :id")
                .setParameter("id", threadId)
                .executeUpdate();

        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.success(Map.of("messageId", messageId, "threadId", threadId)))
                .build();
    }

    @GET
    public Response listThreads(PaginationParams pagination) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isTeacher()) throw new ForbiddenException();

        UUID schoolId = requestContext.getSchoolId();
        UUID userId = requestContext.getUserId();

        String query = requestContext.isSchoolAdministrator()
                ? "SELECT mt.id, mt.subject, u.name as parent_name, s.name as student_name, mt.last_message_at FROM message_thread mt JOIN user_account u ON u.id = mt.parent_id JOIN student s ON s.id = mt.student_id WHERE mt.school_id = :schoolId ORDER BY mt.last_message_at DESC"
                : "SELECT mt.id, mt.subject, u.name as parent_name, s.name as student_name, mt.last_message_at FROM message_thread mt JOIN user_account u ON u.id = mt.parent_id JOIN student s ON s.id = mt.student_id WHERE mt.staff_id = :userId ORDER BY mt.last_message_at DESC";

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(query)
                .setParameter(requestContext.isSchoolAdministrator() ? "schoolId" : "userId",
                        requestContext.isSchoolAdministrator() ? schoolId : userId)
                .getResultList();

        var threads = rows.stream().map(r -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("id", r[0]);
            m.put("subject", r[1]);
            m.put("parentName", r[2]);
            m.put("studentName", r[3]);
            m.put("lastMessageAt", r[4] != null ? r[4].toString() : null);
            return m;
        }).toList();

        return Response.ok(ApiResponse.success(threads)).build();
    }

    @GET
    @Path("/{threadId}")
    public Response getThread(@PathParam("threadId") UUID threadId) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isTeacher()) throw new ForbiddenException();

        @SuppressWarnings("unchecked")
        List<Object[]> messages = em.createNativeQuery(
                "SELECT m.id, m.body, m.created_at, u.name as sender_name, m.read_by_recipient, m.sender_id, u.role " +
                "FROM message m JOIN user_account u ON u.id = m.sender_id " +
                "WHERE m.thread_id = :tid ORDER BY m.created_at ASC")
                .setParameter("tid", threadId)
                .getResultList();

        UUID currentUserId = requestContext.getUserId();
        var msgs = messages.stream().map(r -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("id", r[0]); m.put("body", r[1]); m.put("createdAt", r[2].toString());
            m.put("senderName", r[3]); m.put("read", r[4]);
            m.put("isMe", r[5] != null && r[5].equals(currentUserId));
            m.put("senderRole", r[6]);
            return m;
        }).toList();

        return Response.ok(ApiResponse.success(msgs)).build();
    }
}
