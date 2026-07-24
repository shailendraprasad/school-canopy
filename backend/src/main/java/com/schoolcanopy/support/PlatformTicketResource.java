package com.schoolcanopy.support;

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

@Path("/api/platform/support-tickets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PlatformTicketResource {

    @Inject RequestContext requestContext;
    @Inject EntityManager em;

    @GET
    public Response listAllTickets() {
        if (!requestContext.isSuperAdmin() && !requestContext.isPlatformTeamMember()) {
            throw new ForbiddenException();
        }

        String sql = "SELECT t.id, t.subject, t.priority, t.status, t.created_at, t.updated_at, " +
                "s.name as school_name, u.name as created_by_name, " +
                "(SELECT COUNT(*) FROM ticket_comment tc WHERE tc.ticket_id = t.id) as comment_count " +
                "FROM support_ticket t " +
                "JOIN school s ON s.id = t.school_id " +
                "JOIN user_account u ON u.id = t.created_by ";

        if (requestContext.isPlatformTeamMember()) {
            sql += "JOIN platform_team_school_assignment ptsa ON ptsa.school_id = t.school_id AND ptsa.user_id = :uid ";
        }
        sql += "ORDER BY CASE t.status WHEN 'OPEN' THEN 0 WHEN 'IN_PROGRESS' THEN 1 ELSE 2 END, t.created_at DESC";

        var query = em.createNativeQuery(sql);
        if (requestContext.isPlatformTeamMember()) {
            query.setParameter("uid", requestContext.getUserId());
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        var tickets = rows.stream().map(r -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("id", r[0]); m.put("subject", r[1]); m.put("priority", r[2]); m.put("status", r[3]);
            m.put("createdAt", r[4] != null ? r[4].toString() : null);
            m.put("updatedAt", r[5] != null ? r[5].toString() : null);
            m.put("schoolName", r[6]); m.put("createdByName", r[7]);
            m.put("commentCount", ((Number) r[8]).intValue());
            return m;
        }).toList();

        return Response.ok(ApiResponse.success(tickets)).build();
    }

    @GET
    @Path("/{id}")
    public Response getTicketDetail(@PathParam("id") UUID ticketId) {
        if (!requestContext.isSuperAdmin() && !requestContext.isPlatformTeamMember()) {
            throw new ForbiddenException();
        }

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

    @POST
    @Path("/{id}/comments")
    @Transactional
    public Response addComment(@PathParam("id") UUID ticketId, Map<String, String> body) {
        if (!requestContext.isSuperAdmin() && !requestContext.isPlatformTeamMember()) {
            throw new ForbiddenException();
        }

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

    @PATCH
    @Path("/{id}/status")
    @Transactional
    public Response updateStatus(@PathParam("id") UUID ticketId, Map<String, String> body) {
        if (!requestContext.isSuperAdmin() && !requestContext.isPlatformTeamMember()) {
            throw new ForbiddenException();
        }

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
