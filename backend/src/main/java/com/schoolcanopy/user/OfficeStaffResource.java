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

@Path("/api/school/office-staff")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OfficeStaffResource {

    @Inject UserAccountRepository userAccountRepository;
    @Inject SessionService sessionService;
    @Inject AuditService auditService;
    @Inject RequestContext requestContext;

    @POST
    @Transactional
    public Response create(CreateUserRequest request) {
        if (!requestContext.isSchoolAdministrator()) throw new ForbiddenException();

        UUID schoolId = requestContext.getSchoolId();
        if (userAccountRepository.existsByEmailAndSchoolId(request.getEmail(), schoolId)) {
            throw new ConflictException("email", "Email is already in use within this school");
        }

        UserAccount user = new UserAccount();
        user.setName(request.getName());
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setSchoolId(schoolId);
        user.setRole("OFFICE_STAFF");
        user.setStatus("PENDING");
        user.setPhone(request.getPhone());
        user.setDesignation(request.getDesignation());
        user.setCreatedAt(LocalDateTime.now());
        userAccountRepository.persist(user);

        auditService.log("OFFICE_STAFF_CREATED", schoolId, user.getId().toString(), requestContext.getUserId(), null);

        return Response.status(Response.Status.CREATED).entity(ApiResponse.success(toDto(user))).build();
    }

    @GET
    public Response list(PaginationParams pagination) {
        if (!requestContext.isSchoolAdministrator()) throw new ForbiddenException();

        UUID schoolId = requestContext.getSchoolId();
        long total = userAccountRepository.count("role = ?1 AND schoolId = ?2", "OFFICE_STAFF", schoolId);
        List<UserAccount> staff = userAccountRepository.find("role = ?1 AND schoolId = ?2", "OFFICE_STAFF", schoolId)
                .page(pagination.getOffset() / pagination.getLimit(), pagination.getLimit()).list();

        return Response.ok(ApiResponse.success(staff.stream().map(this::toDto).toList(),
                new PaginationMeta(total, pagination.getOffset(), pagination.getLimit()))).build();
    }

    @PATCH
    @Path("/{id}/status")
    @Transactional
    public Response updateStatus(@PathParam("id") UUID id, Map<String, String> body) {
        if (!requestContext.isSchoolAdministrator()) throw new ForbiddenException();

        UserAccount user = userAccountRepository.findById(id);
        if (user == null || !"OFFICE_STAFF".equals(user.getRole())) throw new ResourceNotFoundException();

        String newStatus = body.get("status");
        if ("DEACTIVATED".equalsIgnoreCase(newStatus)) {
            user.setStatus("DEACTIVATED");
            sessionService.revokeSessionsForUser(user.getId());
            auditService.log("OFFICE_STAFF_DEACTIVATED", user.getSchoolId(), id.toString(), requestContext.getUserId(), null);
        } else if ("ACTIVE".equalsIgnoreCase(newStatus)) {
            user.setStatus("ACTIVE");
        }
        user.setUpdatedAt(LocalDateTime.now());
        userAccountRepository.persist(user);

        return Response.ok(ApiResponse.success(toDto(user))).build();
    }

    private Map<String, Object> toDto(UserAccount u) {
        var dto = new java.util.HashMap<String, Object>();
        dto.put("id", u.getId()); dto.put("name", u.getName()); dto.put("email", u.getEmail());
        dto.put("status", u.getStatus()); dto.put("createdAt", u.getCreatedAt().toString());
        dto.put("phone", u.getPhone()); dto.put("designation", u.getDesignation());
        dto.put("gender", u.getGender()); dto.put("employeeId", u.getEmployeeId());
        dto.put("dateOfJoining", u.getDateOfJoining() != null ? u.getDateOfJoining().toString() : null);
        dto.put("emergencyContact", u.getEmergencyContact());
        return dto;
    }
}
