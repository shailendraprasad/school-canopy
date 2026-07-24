package com.schoolcanopy.academic;

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
import com.schoolcanopy.config.ConfigService;
import com.schoolcanopy.rbac.RequestContext;
import com.schoolcanopy.user.UserAccount;
import com.schoolcanopy.user.UserAccountRepository;

@Path("/api/school/students/{studentId}/parents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ParentResource {

    @Inject RequestContext requestContext;
    @Inject UserAccountRepository userAccountRepository;
    @Inject StudentRepository studentRepository;
    @Inject ConfigService configService;
    @Inject EntityManager em;

    @POST
    @Transactional
    public Response linkParent(@PathParam("studentId") UUID studentId, Map<String, String> body) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff()) throw new ForbiddenException();

        String parentEmail = body.get("email");
        if (parentEmail == null || parentEmail.isBlank()) {
            throw new ValidationException("email", "REQUIRED", "Parent email is required");
        }

        String relationship = body.getOrDefault("relationship", "GUARDIAN").toUpperCase();
        if (!List.of("MOTHER", "FATHER", "GUARDIAN").contains(relationship)) {
            relationship = "GUARDIAN";
        }

        Student student = studentRepository.findById(studentId);
        if (student == null) throw new ResourceNotFoundException("Student not found");
        if ("DEACTIVATED".equals(student.getStatus())) {
            throw new ValidationException("student", "DEACTIVATED", "Cannot link parent to a deactivated student");
        }

        UUID schoolId = requestContext.getSchoolId();

        // Check max parents per student
        int maxParents = configService.getMaxParentsPerStudent();
        Long currentCount = (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM parent_student_link WHERE student_id = :sid")
                .setParameter("sid", studentId)
                .getSingleResult();
        if (currentCount >= maxParents) {
            throw new ValidationException("student", "LIMIT_REACHED",
                    "Maximum of " + maxParents + " parents per student");
        }

        // Enforce uniqueness: Mother and Father can only appear once per student
        if ("MOTHER".equals(relationship) || "FATHER".equals(relationship)) {
            Long existingRelCount = (Long) em.createNativeQuery(
                    "SELECT COUNT(*) FROM parent_student_link WHERE student_id = :sid AND relationship = :rel")
                    .setParameter("sid", studentId)
                    .setParameter("rel", relationship)
                    .getSingleResult();
            if (existingRelCount > 0) {
                throw new ValidationException("relationship", "ALREADY_EXISTS",
                        relationship.charAt(0) + relationship.substring(1).toLowerCase() + " is already linked to this student");
            }
        }

        // Find or create parent account
        UserAccount parent = userAccountRepository.findByEmail(parentEmail.toLowerCase().trim());
        if (parent == null) {
            parent = new UserAccount();
            parent.setEmail(parentEmail.toLowerCase().trim());
            parent.setName(parentEmail.split("@")[0]);
            parent.setRole("PARENT");
            parent.setStatus("PENDING");
            parent.setCreatedAt(LocalDateTime.now());
            userAccountRepository.persist(parent);
        }

        // Check max students per parent (20)
        Long parentStudentCount = (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM parent_student_link WHERE parent_id = :pid")
                .setParameter("pid", parent.getId())
                .getSingleResult();
        if (parentStudentCount >= 20) {
            throw new ValidationException("parent", "LIMIT_REACHED", "Parent can be linked to maximum 20 students");
        }

        // Check if already linked (same parent account + student)
        Long alreadyLinked = (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM parent_student_link WHERE parent_id = :pid AND student_id = :sid")
                .setParameter("pid", parent.getId())
                .setParameter("sid", studentId)
                .getSingleResult();
        if (alreadyLinked > 0) {
            return Response.ok(ApiResponse.success(Map.of("parentId", parent.getId(), "studentId", studentId, "status", "already_linked"))).build();
        }

        // Create the link with relationship
        em.createNativeQuery(
                "INSERT INTO parent_student_link (id, parent_id, student_id, school_id, relationship, linked_at) VALUES (gen_random_uuid(), :pid, :sid, :schoolId, :rel, NOW())")
                .setParameter("pid", parent.getId())
                .setParameter("sid", studentId)
                .setParameter("schoolId", schoolId)
                .setParameter("rel", relationship)
                .executeUpdate();

        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.success(Map.of("parentId", parent.getId(), "studentId", studentId, "email", parent.getEmail(), "relationship", relationship)))
                .build();
    }

    @DELETE
    @Path("/{parentId}")
    @Transactional
    public Response unlinkParent(@PathParam("studentId") UUID studentId, @PathParam("parentId") UUID parentId) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff()) throw new ForbiddenException();

        int deleted = em.createNativeQuery(
                "DELETE FROM parent_student_link WHERE parent_id = :pid AND student_id = :sid")
                .setParameter("pid", parentId)
                .setParameter("sid", studentId)
                .executeUpdate();

        if (deleted == 0) throw new ResourceNotFoundException("Link not found");

        return Response.ok(ApiResponse.success(null)).build();
    }

    @GET
    public Response listParents(@PathParam("studentId") UUID studentId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT u.id, u.email, u.name, u.status, psl.relationship FROM user_account u " +
                "JOIN parent_student_link psl ON psl.parent_id = u.id " +
                "WHERE psl.student_id = :sid")
                .setParameter("sid", studentId)
                .getResultList();

        var parents = rows.stream().map(r -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("id", r[0]);
            m.put("email", r[1]);
            m.put("name", r[2]);
            m.put("status", r[3]);
            m.put("relationship", r[4]);
            return m;
        }).toList();
        return Response.ok(ApiResponse.success(parents)).build();
    }
}
