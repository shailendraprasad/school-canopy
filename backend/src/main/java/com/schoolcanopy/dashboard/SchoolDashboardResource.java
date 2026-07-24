package com.schoolcanopy.dashboard;

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

@Path("/api/school/dashboard")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SchoolDashboardResource {

    @Inject RequestContext requestContext;
    @Inject EntityManager em;

    @GET
    public Response getDashboard() {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isTeacher()) throw new ForbiddenException();

        UUID schoolId = requestContext.getSchoolId();

        Long totalStudents = (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM student WHERE school_id = :sid")
                .setParameter("sid", schoolId).getSingleResult();

        Long totalTeachers = (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM user_account WHERE school_id = :sid AND role = 'TEACHER'")
                .setParameter("sid", schoolId).getSingleResult();

        Long totalParents = (Long) em.createNativeQuery(
                "SELECT COUNT(DISTINCT parent_id) FROM parent_student_link WHERE school_id = :sid")
                .setParameter("sid", schoolId).getSingleResult();

        Long unreadMessages = (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM message m JOIN message_thread mt ON mt.id = m.thread_id " +
                "WHERE mt.school_id = :sid AND m.read_by_recipient = false")
                .setParameter("sid", schoolId).getSingleResult();

        return Response.ok(ApiResponse.success(Map.of(
                "totalStudents", totalStudents,
                "totalTeachers", totalTeachers,
                "totalParents", totalParents,
                "unreadMessages", unreadMessages
        ))).build();
    }
}
