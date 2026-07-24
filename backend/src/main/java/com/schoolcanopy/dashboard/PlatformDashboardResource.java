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

@Path("/api/platform/dashboard")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PlatformDashboardResource {

    @Inject RequestContext requestContext;
    @Inject EntityManager em;

    @GET
    public Response getDashboard() {
        if (!requestContext.isSuperAdmin() && !requestContext.isPlatformTeamMember()) throw new ForbiddenException();

        Long totalSchools, totalStudents, totalUsers, activeParents;

        if (requestContext.isPlatformTeamMember()) {
            // Only count stats for assigned schools
            UUID uid = requestContext.getUserId();
            totalSchools = (Long) em.createNativeQuery(
                    "SELECT COUNT(*) FROM platform_team_school_assignment WHERE user_id = :uid")
                    .setParameter("uid", uid).getSingleResult();
            totalStudents = (Long) em.createNativeQuery(
                    "SELECT COUNT(*) FROM student s JOIN platform_team_school_assignment ptsa ON ptsa.school_id = s.school_id WHERE ptsa.user_id = :uid")
                    .setParameter("uid", uid).getSingleResult();
            totalUsers = (Long) em.createNativeQuery(
                    "SELECT COUNT(*) FROM user_account u JOIN platform_team_school_assignment ptsa ON ptsa.school_id = u.school_id WHERE ptsa.user_id = :uid")
                    .setParameter("uid", uid).getSingleResult();
            activeParents = (Long) em.createNativeQuery(
                    "SELECT COUNT(DISTINCT psl.parent_id) FROM parent_student_link psl JOIN platform_team_school_assignment ptsa ON ptsa.school_id = psl.school_id WHERE ptsa.user_id = :uid")
                    .setParameter("uid", uid).getSingleResult();
        } else {
            totalSchools = (Long) em.createNativeQuery("SELECT COUNT(*) FROM school").getSingleResult();
            totalStudents = (Long) em.createNativeQuery("SELECT COUNT(*) FROM student").getSingleResult();
            totalUsers = (Long) em.createNativeQuery("SELECT COUNT(*) FROM user_account").getSingleResult();
            activeParents = (Long) em.createNativeQuery(
                    "SELECT COUNT(*) FROM user_account WHERE role = 'PARENT' AND last_login_at > NOW() - INTERVAL '30 days'")
                    .getSingleResult();
        }

        return Response.ok(ApiResponse.success(Map.of(
                "totalSchools", totalSchools,
                "totalStudents", totalStudents,
                "totalUsers", totalUsers,
                "activeParents", activeParents
        ))).build();
    }

    @GET
    @Path("/school-health")
    public Response getSchoolHealth() {
        if (!requestContext.isSuperAdmin() && !requestContext.isPlatformTeamMember()) throw new ForbiddenException();

        String sql = "SELECT s.id, s.name, s.prefix, s.status, s.contact_email, s.created_at, " +
                "  s.address, s.phone, " +
                "  (SELECT COUNT(*) FROM student st WHERE st.school_id = s.id AND st.status = 'ACTIVE') as student_count, " +
                "  (SELECT COUNT(*) FROM user_account u WHERE u.school_id = s.id AND u.role = 'TEACHER' AND u.status = 'ACTIVE') as teacher_count, " +
                "  (SELECT COUNT(DISTINCT psl.parent_id) FROM parent_student_link psl WHERE psl.school_id = s.id) as parent_count, " +
                "  (SELECT COUNT(*) FROM user_account u WHERE u.school_id = s.id AND u.role = 'SCHOOL_ADMINISTRATOR' AND u.status = 'ACTIVE') as admin_count, " +
                "  (SELECT MAX(u.last_login_at) FROM user_account u WHERE u.school_id = s.id) as last_activity, " +
                "  s.board_affiliation, s.udise_code, s.school_type, s.medium_of_instruction, " +
                "  s.city, s.state, s.pin_code, s.principal_name, s.founded_year " +
                "FROM school s ";

        // Platform team members: only see assigned schools
        if (requestContext.isPlatformTeamMember()) {
            sql += "JOIN platform_team_school_assignment ptsa ON ptsa.school_id = s.id AND ptsa.user_id = :uid ";
        }
        sql += "ORDER BY s.name";

        var query = em.createNativeQuery(sql);
        if (requestContext.isPlatformTeamMember()) {
            query.setParameter("uid", requestContext.getUserId());
        }

        @SuppressWarnings("unchecked")
        java.util.List<Object[]> rows = query.getResultList();

        var schoolHealth = rows.stream().map(r -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("id", r[0]);
            m.put("name", r[1]);
            m.put("prefix", r[2]);
            m.put("status", r[3]);
            m.put("contactEmail", r[4]);
            m.put("createdAt", r[5] != null ? r[5].toString() : null);
            m.put("address", r[6]);
            m.put("phone", r[7]);
            m.put("studentCount", ((Number) r[8]).intValue());
            m.put("teacherCount", ((Number) r[9]).intValue());
            m.put("parentCount", ((Number) r[10]).intValue());
            m.put("adminCount", ((Number) r[11]).intValue());
            m.put("lastActivity", r[12] != null ? r[12].toString() : null);
            m.put("boardAffiliation", r[13]);
            m.put("udiseCode", r[14]);
            m.put("schoolType", r[15]);
            m.put("mediumOfInstruction", r[16]);
            m.put("city", r[17]);
            m.put("state", r[18]);
            m.put("pinCode", r[19]);
            m.put("principalName", r[20]);
            m.put("foundedYear", r[21]);
            return m;
        }).toList();

        return Response.ok(ApiResponse.success(schoolHealth)).build();
    }
}
