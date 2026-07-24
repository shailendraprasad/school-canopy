package com.schoolcanopy.support;

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
import com.schoolcanopy.common.exceptions.ResourceNotFoundException;
import com.schoolcanopy.common.exceptions.ValidationException;
import com.schoolcanopy.rbac.RequestContext;

@Path("/api/school/support-tickets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SupportTicketResource {

    @Inject RequestContext requestContext;
    @Inject EntityManager em;

    // === SCHOOL PORTAL: Create & View Tickets ===

    @POST
    @Transactional
    public Response createTicket(Map<String, String> body) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff()) {
            throw new ForbiddenException();
        }

        String subject = body.get("subject");
        String description = body.get("description");
        String priority = body.getOrDefault("priority", "MEDIUM").toUpperCase();

        if (subject == null || subject.isBlank()) throw new ValidationException("subject", "REQUIRED", "Subject is required");
        if (description == null || description.isBlank()) throw new ValidationException("description", "REQUIRED", "Description is required");
        if (!List.of("LOW", "MEDIUM", "HIGH", "URGENT").contains(priority)) priority = "MEDIUM";

        UUID ticketId = UUID.randomUUID();
        em.createNativeQuery(
                "INSERT INTO support_ticket (id, school_id, created_by, subject, description, priority, status, created_at) " +
                "VALUES (:id, :schoolId, :userId, :subject, :desc, :priority, 'OPEN', NOW())")
                .setParameter("id", ticketId)
                .setParameter("schoolId", requestContext.getSchoolId())
                .setParameter("userId", requestContext.getUserId())
                .setParameter("subject", subject.trim())
                .setParameter("desc", description.trim())
                .setParameter("priority", priority)
                .executeUpdate();

        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.success(Map.of("id", ticketId, "subject", subject, "status", "OPEN")))
                .build();
    }

    @GET
    public Response listSchoolTickets() {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff()) {
            throw new ForbiddenException();
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT t.id, t.subject, t.priority, t.status, t.created_at, t.updated_at, t.resolved_at, u.name as created_by_name, " +
                "(SELECT COUNT(*) FROM ticket_comment tc WHERE tc.ticket_id = t.id) as comment_count " +
                "FROM support_ticket t JOIN user_account u ON u.id = t.created_by " +
                "WHERE t.school_id = :schoolId ORDER BY t.created_at DESC")
                .setParameter("schoolId", requestContext.getSchoolId())
                .getResultList();

        var tickets = rows.stream().map(r -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("id", r[0]); m.put("subject", r[1]); m.put("priority", r[2]); m.put("status", r[3]);
            m.put("createdAt", r[4] != null ? r[4].toString() : null);
            m.put("updatedAt", r[5] != null ? r[5].toString() : null);
            m.put("resolvedAt", r[6] != null ? r[6].toString() : null);
            m.put("createdByName", r[7]); m.put("commentCount", ((Number) r[8]).intValue());
            return m;
        }).toList();

        return Response.ok(ApiResponse.success(tickets)).build();
    }

    @GET
    @Path("/{id}")
    public Response getSchoolTicketDetail(@PathParam("id") UUID id) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff()) {
            throw new ForbiddenException();
        }
        return getTicketDetail(id);
    }

    @POST
    @Path("/{id}/comments")
    @Transactional
    public Response addSchoolComment(@PathParam("id") UUID ticketId, Map<String, String> body) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff()) {
            throw new ForbiddenException();
        }
        return addComment(ticketId, body);
    }

    @PATCH
    @Path("/{id}/status")
    @Transactional
    public Response updateSchoolTicketStatus(@PathParam("id") UUID ticketId, Map<String, String> body) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff()) {
            throw new ForbiddenException();
        }
        return updateTicketStatus(ticketId, body);
    }

    // === Shared methods ===

    private Response getTicketDetail(UUID ticketId) {
        @SuppressWarnings("unchecked")
        List<Object[]> ticketRows = em.createNativeQuery(
                "SELECT t.id, t.subject, t.description, t.priority, t.status, t.created_at, t.updated_at, t.resolved_at, " +
                "u.name as created_by_name, u.email as created_by_email, s.name as school_name " +
                "FROM support_ticket t JOIN user_account u ON u.id = t.created_by JOIN school s ON s.id = t.school_id " +
                "WHERE t.id = :id")
                .setParameter("id", ticketId)
                .getResultList();

        if (ticketRows.isEmpty()) throw new ResourceNotFoundException("Ticket not found");
        Object[] t = ticketRows.get(0);

        var ticket = new java.util.HashMap<String, Object>();
        ticket.put("id", t[0]); ticket.put("subject", t[1]); ticket.put("description", t[2]);
        ticket.put("priority", t[3]); ticket.put("status", t[4]);
        ticket.put("createdAt", t[5] != null ? t[5].toString() : null);
        ticket.put("updatedAt", t[6] != null ? t[6].toString() : null);
        ticket.put("resolvedAt", t[7] != null ? t[7].toString() : null);
        ticket.put("createdByName", t[8]); ticket.put("createdByEmail", t[9]); ticket.put("schoolName", t[10]);

        // Load comments
        @SuppressWarnings("unchecked")
        List<Object[]> commentRows = em.createNativeQuery(
                "SELECT tc.id, tc.body, tc.created_at, u.name, u.role FROM ticket_comment tc " +
                "JOIN user_account u ON u.id = tc.author_id WHERE tc.ticket_id = :tid ORDER BY tc.created_at ASC")
                .setParameter("tid", ticketId)
                .getResultList();

        var comments = commentRows.stream().map(c -> {
            var cm = new java.util.HashMap<String, Object>();
            cm.put("id", c[0]); cm.put("body", c[1]);
            cm.put("createdAt", c[2] != null ? c[2].toString() : null);
            cm.put("authorName", c[3]); cm.put("authorRole", c[4]);
            return cm;
        }).toList();
        ticket.put("comments", comments);

        return Response.ok(ApiResponse.success(ticket)).build();
    }

    private Response addComment(UUID ticketId, Map<String, String> body) {
        String commentBody = body.get("body");
        if (commentBody == null || commentBody.isBlank()) {
            throw new ValidationException("body", "REQUIRED", "Comment text is required");
        }

        UUID commentId = UUID.randomUUID();
        em.createNativeQuery(
                "INSERT INTO ticket_comment (id, ticket_id, author_id, body, created_at) VALUES (:id, :tid, :uid, :body, NOW())")
                .setParameter("id", commentId)
                .setParameter("tid", ticketId)
                .setParameter("uid", requestContext.getUserId())
                .setParameter("body", commentBody.trim())
                .executeUpdate();

        em.createNativeQuery("UPDATE support_ticket SET updated_at = NOW() WHERE id = :id")
                .setParameter("id", ticketId).executeUpdate();

        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.success(Map.of("id", commentId, "ticketId", ticketId)))
                .build();
    }

    private Response updateTicketStatus(UUID ticketId, Map<String, String> body) {
        String newStatus = body.get("status");
        if (newStatus == null || !List.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED").contains(newStatus.toUpperCase())) {
            throw new ValidationException("status", "INVALID", "Status must be OPEN, IN_PROGRESS, RESOLVED, or CLOSED");
        }
        newStatus = newStatus.toUpperCase();

        String updateSql = "UPDATE support_ticket SET status = :status, updated_at = NOW()";
        if ("RESOLVED".equals(newStatus) || "CLOSED".equals(newStatus)) {
            updateSql += ", resolved_at = NOW()";
        }
        updateSql += " WHERE id = :id";

        em.createNativeQuery(updateSql)
                .setParameter("status", newStatus)
                .setParameter("id", ticketId)
                .executeUpdate();

        return Response.ok(ApiResponse.success(Map.of("id", ticketId, "status", newStatus))).build();
    }
}
