package com.schoolcanopy.audit;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.schoolcanopy.common.ApiResponse;
import com.schoolcanopy.common.PaginationMeta;
import com.schoolcanopy.common.PaginationParams;
import com.schoolcanopy.common.exceptions.ForbiddenException;
import com.schoolcanopy.rbac.RequestContext;

import io.quarkus.panache.common.Sort;

@Path("/api/platform/audit-logs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuditResource {

    @Inject
    AuditLogRepository auditLogRepository;

    @Inject
    RequestContext requestContext;

    @GET
    public Response list(
            @QueryParam("actionType") String actionType,
            @QueryParam("performedBy") String performedBy,
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate,
            PaginationParams pagination) {

        if (!requestContext.isSuperAdmin() && !requestContext.isPlatformTeamMember()) {
            throw new ForbiddenException();
        }

        StringBuilder query = new StringBuilder("1=1");
        Map<String, Object> params = new HashMap<>();

        if (actionType != null && !actionType.isBlank()) {
            query.append(" AND actionType = :actionType");
            params.put("actionType", actionType);
        }

        if (performedBy != null && !performedBy.isBlank()) {
            query.append(" AND performedBy = :performedBy");
            params.put("performedBy", java.util.UUID.fromString(performedBy));
        }

        if (startDate != null && !startDate.isBlank()) {
            query.append(" AND createdAt >= :startDate");
            params.put("startDate", LocalDate.parse(startDate).atStartOfDay());
        }

        if (endDate != null && !endDate.isBlank()) {
            query.append(" AND createdAt <= :endDate");
            params.put("endDate", LocalDate.parse(endDate).atTime(23, 59, 59));
        }

        long total = auditLogRepository.count(query.toString(), params);
        List<AuditLog> logs = auditLogRepository.find(query.toString(), Sort.descending("createdAt"), params)
                .page(pagination.getOffset() / pagination.getLimit(), pagination.getLimit())
                .list();

        return Response.ok(ApiResponse.success(logs, new PaginationMeta(total, pagination.getOffset(), pagination.getLimit())))
                .build();
    }
}
