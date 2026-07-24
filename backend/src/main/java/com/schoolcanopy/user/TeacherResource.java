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

@Path("/api/school/teachers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TeacherResource {

    @Inject UserAccountRepository userAccountRepository;
    @Inject SessionService sessionService;
    @Inject AuditService auditService;
    @Inject RequestContext requestContext;
    @Inject com.schoolcanopy.auth.InvitationResource invitationResource;

    @POST
    @Path("/invite")
    @Transactional
    public Response invite(CreateUserRequest request) {
        if (!requestContext.isSchoolAdministrator()) throw new ForbiddenException();

        UUID schoolId = requestContext.getSchoolId();
        if (userAccountRepository.existsByEmailAndSchoolId(request.getEmail(), schoolId)) {
            throw new ConflictException("email", "Email is already registered in this school");
        }

        UserAccount user = new UserAccount();
        user.setName(request.getName());
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setSchoolId(schoolId);
        user.setRole("TEACHER");
        user.setStatus("PENDING");
        user.setCreatedAt(LocalDateTime.now());
        userAccountRepository.persist(user);

        // Create invitation and return token
        String token = invitationResource.createInvitation(user.getId(), user.getEmail());

        auditService.log("TEACHER_INVITED", schoolId, user.getId().toString(), requestContext.getUserId(), null);

        var dto = new java.util.HashMap<>(toDto(user));
        dto.put("invitationToken", token);
        dto.put("invitationLink", "/api/invitations/" + token + "/setup");
        return Response.status(Response.Status.CREATED).entity(ApiResponse.success(dto)).build();
    }

    @GET
    public Response list(PaginationParams pagination) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff()) {
            throw new ForbiddenException();
        }

        UUID schoolId = requestContext.getSchoolId();
        long total = userAccountRepository.count("role = ?1 AND schoolId = ?2", "TEACHER", schoolId);
        List<UserAccount> teachers = userAccountRepository.find("role = ?1 AND schoolId = ?2", "TEACHER", schoolId)
                .page(pagination.getOffset() / pagination.getLimit(), pagination.getLimit()).list();

        return Response.ok(ApiResponse.success(teachers.stream().map(this::toDto).toList(),
                new PaginationMeta(total, pagination.getOffset(), pagination.getLimit()))).build();
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") UUID id, Map<String, String> body) {
        if (!requestContext.isSchoolAdministrator()) throw new ForbiddenException();

        UserAccount user = userAccountRepository.findById(id);
        if (user == null || !"TEACHER".equals(user.getRole())) throw new ResourceNotFoundException();

        if (body.containsKey("name")) user.setName(body.get("name"));
        if (body.containsKey("phone")) user.setPhone(body.get("phone"));
        if (body.containsKey("gender")) user.setGender(body.get("gender"));
        if (body.containsKey("qualification")) user.setQualification(body.get("qualification"));
        if (body.containsKey("specialization")) user.setSpecialization(body.get("specialization"));
        if (body.containsKey("employeeId")) user.setEmployeeId(body.get("employeeId"));
        if (body.containsKey("experienceYears") && body.get("experienceYears") != null) {
            try { user.setExperienceYears(Integer.parseInt(body.get("experienceYears"))); } catch (Exception e) {}
        }
        if (body.containsKey("dateOfJoining") && body.get("dateOfJoining") != null && !body.get("dateOfJoining").isBlank()) {
            user.setDateOfJoining(java.time.LocalDate.parse(body.get("dateOfJoining")));
        }
        if (body.containsKey("dateOfBirth") && body.get("dateOfBirth") != null && !body.get("dateOfBirth").isBlank()) {
            user.setDateOfBirth(java.time.LocalDate.parse(body.get("dateOfBirth")));
        }
        if (body.containsKey("aadhaarLast4")) user.setAadhaarLast4(body.get("aadhaarLast4"));
        if (body.containsKey("emergencyContact")) user.setEmergencyContact(body.get("emergencyContact"));
        if (body.containsKey("address")) user.setAddress(body.get("address"));

        user.setUpdatedAt(LocalDateTime.now());
        userAccountRepository.persist(user);

        return Response.ok(ApiResponse.success(toDto(user))).build();
    }

    @PATCH
    @Path("/{id}/status")
    @Transactional
    public Response updateStatus(@PathParam("id") UUID id, Map<String, String> body) {
        if (!requestContext.isSchoolAdministrator()) throw new ForbiddenException();

        UserAccount user = userAccountRepository.findById(id);
        if (user == null || !"TEACHER".equals(user.getRole())) throw new ResourceNotFoundException();

        String newStatus = body.get("status");
        if ("DEACTIVATED".equalsIgnoreCase(newStatus)) {
            user.setStatus("DEACTIVATED");
            sessionService.revokeSessionsForUser(user.getId());
            auditService.log("TEACHER_DEACTIVATED", user.getSchoolId(), id.toString(), requestContext.getUserId(), null);
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
        dto.put("role", u.getRole()); dto.put("status", u.getStatus()); dto.put("createdAt", u.getCreatedAt().toString());
        dto.put("phone", u.getPhone()); dto.put("gender", u.getGender());
        dto.put("qualification", u.getQualification()); dto.put("specialization", u.getSpecialization());
        dto.put("employeeId", u.getEmployeeId());
        dto.put("dateOfJoining", u.getDateOfJoining() != null ? u.getDateOfJoining().toString() : null);
        dto.put("dateOfBirth", u.getDateOfBirth() != null ? u.getDateOfBirth().toString() : null);
        dto.put("experienceYears", u.getExperienceYears());
        dto.put("aadhaarLast4", u.getAadhaarLast4());
        dto.put("emergencyContact", u.getEmergencyContact()); dto.put("address", u.getAddress());
        return dto;
    }
}
