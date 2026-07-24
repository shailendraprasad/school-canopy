package com.schoolcanopy.school;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.schoolcanopy.common.ApiResponse;
import com.schoolcanopy.common.PaginationMeta;
import com.schoolcanopy.common.PaginationParams;
import com.schoolcanopy.common.exceptions.ValidationException;
import com.schoolcanopy.rbac.RequestContext;

@Path("/api/platform/schools")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SchoolResource {

    @Inject
    SchoolService schoolService;

    @Inject
    SchoolRepository schoolRepository;

    @Inject
    RequestContext requestContext;

    @POST
    public Response create(SchoolCreateRequest request) {
        // Only Super_Admin can onboard schools
        if (!requestContext.isSuperAdmin()) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("Access denied"))
                    .build();
        }
        School school = schoolService.onboard(request, requestContext.getUserId());
        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.success(school))
                .build();
    }

    @GET
    public Response list(@QueryParam("status") String status, PaginationParams pagination) {
        if (!pagination.isValid()) {
            throw new ValidationException("limit", "INVALID", pagination.getValidationError());
        }

        // Platform_Team_Members see only assigned schools
        if (requestContext.isPlatformTeamMember()) {
            @SuppressWarnings("unchecked")
            var assigned = schoolRepository.getEntityManager().createNativeQuery(
                    "SELECT s.* FROM school s JOIN platform_team_school_assignment ptsa ON ptsa.school_id = s.id " +
                    "WHERE ptsa.user_id = :uid ORDER BY s.name",
                    com.schoolcanopy.school.School.class)
                    .setParameter("uid", requestContext.getUserId())
                    .getResultList();
            return Response.ok(ApiResponse.success(assigned, new PaginationMeta(assigned.size(), 0, assigned.size())))
                    .build();
        }

        var query = schoolRepository.findAll();
        long total = schoolRepository.count();

        var schools = query.page(pagination.getOffset() / pagination.getLimit(), pagination.getLimit()).list();
        return Response.ok(ApiResponse.success(schools, new PaginationMeta(total, pagination.getOffset(), pagination.getLimit())))
                .build();
    }

    @PATCH
    @Path("/{id}/status")
    public Response updateStatus(@PathParam("id") UUID id, StatusUpdateRequest request) {
        School school;
        if ("DEACTIVATED".equalsIgnoreCase(request.getStatus())) {
            school = schoolService.deactivate(id, requestContext.getUserId());
        } else if ("ACTIVE".equalsIgnoreCase(request.getStatus())) {
            school = schoolService.reactivate(id, requestContext.getUserId());
        } else {
            throw new ValidationException("status", "INVALID", "Status must be ACTIVE or DEACTIVATED");
        }
        return Response.ok(ApiResponse.success(school)).build();
    }

    public static class StatusUpdateRequest {
        private String status;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
