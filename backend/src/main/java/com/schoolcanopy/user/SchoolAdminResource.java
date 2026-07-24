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
import com.schoolcanopy.common.exceptions.ValidationException;
import com.schoolcanopy.rbac.RequestContext;

@Path("/api/platform/school-admins")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SchoolAdminResource {

    private static final int MAX_ADMINS_PER_SCHOOL = 10;

    @Inject UserAccountRepository userAccountRepository;
    @Inject SessionService sessionService;
    @Inject AuditService auditService;
    @Inject RequestContext requestContext;
    @Inject com.schoolcanopy.auth.InvitationResource invitationResource;

    @POST
    @Transactional
    public Response create(CreateUserRequest request) {
        if (!requestContext.isSuperAdmin() && !requestContext.isPlatformTeamMember()) {
            throw new ForbiddenException();
        }

        if (request.getSchoolId() == null || request.getSchoolId().isBlank()) {
            throw new ValidationException("schoolId", "REQUIRED", "School is required");
        }

        UUID schoolId = UUID.fromString(request.getSchoolId());

        if (userAccountRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("email", "Email is already in use");
        }

        long adminCount = userAccountRepository.countByRoleAndSchoolId("SCHOOL_ADMINISTRATOR", schoolId);
        if (adminCount >= MAX_ADMINS_PER_SCHOOL) {
            throw new ValidationException("school", "LIMIT_REACHED",
                    "Maximum of " + MAX_ADMINS_PER_SCHOOL + " administrators per school");
        }

        UserAccount user = new UserAccount();
        user.setName(request.getName());
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setSchoolId(schoolId);
        user.setRole("SCHOOL_ADMINISTRATOR");
        user.setStatus("PENDING");
        user.setCreatedAt(LocalDateTime.now());
        userAccountRepository.persist(user);

        // Create invitation and return token
        String token = invitationResource.createInvitation(user.getId(), user.getEmail());

        auditService.log("SCHOOL_ADMINISTRATOR_CREATED", schoolId, user.getId().toString(),
                requestContext.getUserId(), String.format("{\"name\":\"%s\",\"email\":\"%s\"}", user.getName(), user.getEmail()));

        var dto = new java.util.HashMap<>(toDto(user));
        dto.put("invitationToken", token);
        dto.put("invitationLink", "/api/invitations/" + token + "/setup");
        return Response.status(Response.Status.CREATED).entity(ApiResponse.success(dto)).build();
    }

    @GET
    public Response list(PaginationParams pagination) {
        if (!requestContext.isSuperAdmin() && !requestContext.isPlatformTeamMember()) {
            throw new ForbiddenException();
        }

        long total = userAccountRepository.count("role", "SCHOOL_ADMINISTRATOR");
        List<UserAccount> admins = userAccountRepository.find("role", "SCHOOL_ADMINISTRATOR")
                .page(pagination.getOffset() / pagination.getLimit(), pagination.getLimit()).list();

        return Response.ok(ApiResponse.success(admins.stream().map(this::toDto).toList(),
                new PaginationMeta(total, pagination.getOffset(), pagination.getLimit()))).build();
    }

    @PATCH
    @Path("/{id}/status")
    @Transactional
    public Response updateStatus(@PathParam("id") UUID id, Map<String, String> body) {
        if (!requestContext.isSuperAdmin() && !requestContext.isPlatformTeamMember()) {
            throw new ForbiddenException();
        }

        UserAccount user = userAccountRepository.findById(id);
        if (user == null || !"SCHOOL_ADMINISTRATOR".equals(user.getRole())) throw new ResourceNotFoundException();

        String newStatus = body.get("status");
        if ("DEACTIVATED".equalsIgnoreCase(newStatus)) {
            user.setStatus("DEACTIVATED");
            sessionService.revokeSessionsForUser(user.getId());
            auditService.log("SCHOOL_ADMINISTRATOR_DEACTIVATED", user.getSchoolId(), id.toString(), requestContext.getUserId(), null);
        } else if ("ACTIVE".equalsIgnoreCase(newStatus)) {
            user.setStatus("ACTIVE");
        }
        user.setUpdatedAt(LocalDateTime.now());
        userAccountRepository.persist(user);

        return Response.ok(ApiResponse.success(toDto(user))).build();
    }

    private Map<String, Object> toDto(UserAccount u) {
        return Map.of("id", u.getId(), "name", u.getName(), "email", u.getEmail(),
                "role", u.getRole(), "status", u.getStatus(), "createdAt", u.getCreatedAt().toString());
    }
}
