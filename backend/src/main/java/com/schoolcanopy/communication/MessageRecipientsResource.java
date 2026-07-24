package com.schoolcanopy.communication;

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
import com.schoolcanopy.rbac.RequestContext;

/**
 * Returns available message recipients based on the current user's role.
 * School Admin: all parents linked to students in their school.
 * Teacher: parents of students in their assigned sections.
 */
@Path("/api/school/message-recipients")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MessageRecipientsResource {

    @Inject RequestContext requestContext;
    @Inject EntityManager em;

    @GET
    public Response getRecipients() {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isTeacher()) {
            throw new ForbiddenException();
        }

        UUID schoolId = requestContext.getSchoolId();
        UUID userId = requestContext.getUserId();
        String query;

        if (requestContext.isSchoolAdministrator()) {
            // School admin can message any parent linked to students in their school
            query = "SELECT DISTINCT u.id, u.name, u.email, s.name as student_name, s.id as student_id " +
                    "FROM user_account u " +
                    "JOIN parent_student_link psl ON psl.parent_id = u.id " +
                    "JOIN student s ON s.id = psl.student_id " +
                    "WHERE psl.school_id = :schoolId AND s.status = 'ACTIVE' " +
                    "ORDER BY u.name";
        } else {
            // Teacher can only message parents of students in their assigned sections
            query = "SELECT DISTINCT u.id, u.name, u.email, s.name as student_name, s.id as student_id " +
                    "FROM user_account u " +
                    "JOIN parent_student_link psl ON psl.parent_id = u.id " +
                    "JOIN student s ON s.id = psl.student_id " +
                    "JOIN student_section_enrollment sse ON sse.student_id = s.id " +
                    "JOIN teacher_section_assignment tsa ON tsa.section_id = sse.section_id " +
                    "WHERE tsa.teacher_id = :userId AND s.status = 'ACTIVE' " +
                    "ORDER BY u.name";
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows;
        if (requestContext.isSchoolAdministrator()) {
            rows = em.createNativeQuery(query)
                    .setParameter("schoolId", schoolId)
                    .getResultList();
        } else {
            rows = em.createNativeQuery(query)
                    .setParameter("userId", userId)
                    .getResultList();
        }

        var recipients = rows.stream().map(r -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("parentId", r[0]);
            m.put("parentName", r[1] != null ? r[1] : "Parent");
            m.put("parentEmail", r[2]);
            m.put("studentName", r[3]);
            m.put("studentId", r[4]);
            return m;
        }).toList();

        return Response.ok(ApiResponse.success(recipients)).build();
    }
}
