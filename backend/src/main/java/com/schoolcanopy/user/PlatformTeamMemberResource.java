package com.schoolcanopy.user;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.schoolcanopy.audit.AuditService;
import com.schoolcanopy.auth.SessionService;
import com.schoolcanopy.common.ApiResponse;
import com.schoolcanopy.common.PaginationMeta;
import com.schoolcanopy.common.PaginationParams;
import com.schoolcanopy.common.exceptions.ConflictException;
import com.schoolcanopy.common.exceptions.ForbiddenException;
import com.schoolcanopy.common.exceptions.ResourceNotFoundException;
import com.schoolcanopy.rbac.RequestContext;

@Path("/api/platform/team-members")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PlatformTeamMemberResource {

    @Inject UserAccountRepository userAccountRepository;
    @Inject SessionService sessionService;
    @Inject AuditService auditService;
    @Inject RequestContext requestContext;
    @Inject com.schoolcanopy.auth.InvitationResource invitationResource;
    @Inject jakarta.persistence.EntityManager em;

    @POST
    @Transactional
    public Response create(CreateUserRequest request) {
        if (!requestContext.isSuperAdmin()) throw new ForbiddenException();

        if (userAccountRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("email", "Email is already in use");
        }

        UserAccount user = new UserAccount();
        user.setName(request.getName());
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setRole("PLATFORM_TEAM_MEMBER");
        user.setStatus("PENDING");
        user.setCreatedAt(LocalDateTime.now());
        userAccountRepository.persist(user);

        // Create invitation and return token
        String token = invitationResource.createInvitation(user.getId(), user.getEmail());

        auditService.log("PLATFORM_TEAM_MEMBER_CREATED", null, user.getId().toString(),
                requestContext.getUserId(), String.format("{\"name\":\"%s\",\"email\":\"%s\"}", user.getName(), user.getEmail()));

        var dto = new java.util.HashMap<>(toDto(user));
        dto.put("invitationToken", token);
        dto.put("invitationLink", "/api/invitations/" + token + "/setup");
        return Response.status(Response.Status.CREATED).entity(ApiResponse.success(dto)).build();
    }

    @GET
    public Response list(PaginationParams pagination) {
        if (!requestContext.isSuperAdmin()) throw new ForbiddenException();

        long total = userAccountRepository.count("role", "PLATFORM_TEAM_MEMBER");
        List<UserAccount> members = userAccountRepository.find("role", "PLATFORM_TEAM_MEMBER")
                .page(pagination.getOffset() / pagination.getLimit(), pagination.getLimit()).list();

        return Response.ok(ApiResponse.success(members.stream().map(this::toDto).toList(),
                new PaginationMeta(total, pagination.getOffset(), pagination.getLimit()))).build();
    }

    @PATCH
    @Path("/{id}/status")
    @Transactional
    public Response updateStatus(@PathParam("id") UUID id, Map<String, String> body) {
        if (!requestContext.isSuperAdmin()) throw new ForbiddenException();

        UserAccount user = userAccountRepository.findById(id);
        if (user == null || !"PLATFORM_TEAM_MEMBER".equals(user.getRole())) throw new ResourceNotFoundException();

        String newStatus = body.get("status");
        if ("DEACTIVATED".equalsIgnoreCase(newStatus)) {
            user.setStatus("DEACTIVATED");
            sessionService.revokeSessionsForUser(user.getId());
            auditService.log("PLATFORM_TEAM_MEMBER_DEACTIVATED", null, id.toString(), requestContext.getUserId(), null);
        } else if ("ACTIVE".equalsIgnoreCase(newStatus)) {
            user.setStatus("ACTIVE");
        }
        user.setUpdatedAt(LocalDateTime.now());
        userAccountRepository.persist(user);

        return Response.ok(ApiResponse.success(toDto(user))).build();
    }

    private Map<String, Object> toDto(UserAccount u) {
        var dto = new java.util.HashMap<String, Object>();
        dto.put("id", u.getId());
        dto.put("name", u.getName());
        dto.put("email", u.getEmail());
        dto.put("status", u.getStatus());
        dto.put("createdAt", u.getCreatedAt().toString());

        // Fetch assigned schools
        @SuppressWarnings("unchecked")
        List<Object[]> schools = em.createNativeQuery(
                "SELECT s.id, s.name, s.prefix FROM platform_team_school_assignment ptsa " +
                "JOIN school s ON s.id = ptsa.school_id WHERE ptsa.user_id = :mid")
                .setParameter("mid", u.getId())
                .getResultList();
        dto.put("assignedSchools", schools.stream().map(r -> Map.of("id", r[0], "name", r[1], "prefix", r[2])).toList());

        return dto;
    }

    @PUT
    @Path("/{id}/schools")
    @Transactional
    public Response assignSchools(@PathParam("id") UUID id, Map<String, Object> body) {
        if (!requestContext.isSuperAdmin()) throw new ForbiddenException();

        UserAccount user = userAccountRepository.findById(id);
        if (user == null || !"PLATFORM_TEAM_MEMBER".equals(user.getRole())) throw new ResourceNotFoundException();

        @SuppressWarnings("unchecked")
        List<String> schoolIds = (List<String>) body.get("schoolIds");
        if (schoolIds == null) schoolIds = List.of();

        // Remove all existing assignments
        em.createNativeQuery("DELETE FROM platform_team_school_assignment WHERE user_id = :mid")
                .setParameter("mid", id)
                .executeUpdate();

        // Add new assignments
        for (String schoolIdStr : schoolIds) {
            UUID schoolId = UUID.fromString(schoolIdStr);
            em.createNativeQuery(
                    "INSERT INTO platform_team_school_assignment (id, user_id, school_id, assigned_at) VALUES (gen_random_uuid(), :mid, :sid, NOW())")
                    .setParameter("mid", id)
                    .setParameter("sid", schoolId)
                    .executeUpdate();
        }

        return Response.ok(ApiResponse.success(Map.of("memberId", id, "schoolCount", schoolIds.size()))).build();
    }

    @GET
    @Path("/{id}/schools")
    public Response getAssignedSchools(@PathParam("id") UUID id) {
        if (!requestContext.isSuperAdmin()) throw new ForbiddenException();

        @SuppressWarnings("unchecked")
        List<Object[]> schools = em.createNativeQuery(
                "SELECT s.id, s.name, s.prefix FROM platform_team_school_assignment ptsa " +
                "JOIN school s ON s.id = ptsa.school_id WHERE ptsa.user_id = :mid")
                .setParameter("mid", id)
                .getResultList();

        var result = schools.stream().map(r -> Map.of("id", r[0], "name", r[1], "prefix", r[2])).toList();
        return Response.ok(ApiResponse.success(result)).build();
    }
}
