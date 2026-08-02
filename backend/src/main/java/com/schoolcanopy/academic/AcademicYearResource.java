package com.schoolcanopy.academic;

import java.time.LocalDate;
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
import com.schoolcanopy.common.ApiResponse;
import com.schoolcanopy.common.exceptions.ConflictException;
import com.schoolcanopy.common.exceptions.ForbiddenException;
import com.schoolcanopy.common.exceptions.ResourceNotFoundException;
import com.schoolcanopy.common.exceptions.ValidationException;
import com.schoolcanopy.rbac.RequestContext;

@Path("/api/school/academic-years")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AcademicYearResource {

    @Inject AcademicYearRepository academicYearRepository;
    @Inject AcademicYearService academicYearService;
    @Inject RequestContext requestContext;
    @Inject AuditService auditService;

    @GET
    public Response list() {
        if (!requestContext.isSchoolAdministrator()) throw new ForbiddenException();
        UUID schoolId = requestContext.getSchoolId();
        List<AcademicYear> years = academicYearRepository.find("schoolId = ?1 ORDER BY startsOn DESC", schoolId).list();
        return Response.ok(ApiResponse.success(years.stream().map(this::toDto).toList())).build();
    }

    @GET
    @Path("/active")
    public Response getActive() {
        UUID schoolId = requestContext.getSchoolId();
        AcademicYear year = academicYearRepository.findActiveBySchoolId(schoolId);
        if (year == null) return Response.ok(ApiResponse.success(null)).build();
        return Response.ok(ApiResponse.success(toDto(year))).build();
    }

    @POST
    @Transactional
    public Response create(Map<String, String> body) {
        if (!requestContext.isSchoolAdministrator()) throw new ForbiddenException();

        String name = body.get("name");
        String startsOnStr = body.get("startsOn");
        String endsOnStr = body.get("endsOn");
        if (name == null || name.isBlank()) {
            throw new ValidationException("name", "REQUIRED", "Academic year name is required");
        }
        if (startsOnStr == null || endsOnStr == null) {
            throw new ValidationException("dates", "REQUIRED", "Start and end dates are required");
        }

        UUID schoolId = requestContext.getSchoolId();
        AcademicYear existing = academicYearRepository.find("schoolId = ?1 AND name = ?2", schoolId, name.trim()).firstResult();
        if (existing != null) {
            throw new ConflictException("name", "DUPLICATE", "An academic year with this name already exists");
        }

        AcademicYear year = new AcademicYear();
        year.setSchoolId(schoolId);
        year.setName(name.trim());
        year.setStartsOn(LocalDate.parse(startsOnStr));
        year.setEndsOn(LocalDate.parse(endsOnStr));
        year.setStatus("PLANNED");
        year.setCreatedAt(LocalDateTime.now());
        academicYearRepository.persist(year);

        auditService.log("ACADEMIC_YEAR_CREATED", schoolId, year.getId().toString(), requestContext.getUserId(), name);
        return Response.status(Response.Status.CREATED).entity(ApiResponse.success(toDto(year))).build();
    }

    @POST
    @Path("/{id}/activate")
    @Transactional
    public Response activate(@PathParam("id") UUID id) {
        if (!requestContext.isSchoolAdministrator()) throw new ForbiddenException();
        UUID schoolId = requestContext.getSchoolId();

        AcademicYear year = academicYearRepository.findById(id);
        if (year == null || !year.getSchoolId().equals(schoolId)) {
            throw new ResourceNotFoundException("Academic year not found");
        }

        AcademicYear current = academicYearRepository.findActiveBySchoolId(schoolId);
        if (current != null && !current.getId().equals(id)) {
            throw new ValidationException("academicYear", "ACTIVE_EXISTS",
                    "Close the current active year before activating another");
        }

        year.setStatus("ACTIVE");
        academicYearRepository.persist(year);
        auditService.log("ACADEMIC_YEAR_ACTIVATED", schoolId, id.toString(), requestContext.getUserId(), year.getName());
        return Response.ok(ApiResponse.success(toDto(year))).build();
    }

    @POST
    @Path("/{id}/close")
    @Transactional
    public Response close(@PathParam("id") UUID id) {
        if (!requestContext.isSchoolAdministrator()) throw new ForbiddenException();
        UUID schoolId = requestContext.getSchoolId();

        AcademicYear year = academicYearRepository.findById(id);
        if (year == null || !year.getSchoolId().equals(schoolId)) {
            throw new ResourceNotFoundException("Academic year not found");
        }
        if (!"ACTIVE".equals(year.getStatus())) {
            throw new ValidationException("academicYear", "NOT_ACTIVE", "Only the active academic year can be closed");
        }

        long unhandled = academicYearService.countUnhandledActiveEnrollments(schoolId, id);
        if (unhandled > 0) {
            throw new ValidationException("academicYear", "INCOMPLETE",
                    unhandled + " active student(s) still need promotion before closing this year");
        }

        year.setStatus("CLOSED");
        academicYearRepository.persist(year);
        auditService.log("ACADEMIC_YEAR_CLOSED", schoolId, id.toString(), requestContext.getUserId(), year.getName());
        return Response.ok(ApiResponse.success(toDto(year))).build();
    }

    private Map<String, Object> toDto(AcademicYear y) {
        var dto = new java.util.HashMap<String, Object>();
        dto.put("id", y.getId());
        dto.put("name", y.getName());
        dto.put("startsOn", y.getStartsOn().toString());
        dto.put("endsOn", y.getEndsOn().toString());
        dto.put("status", y.getStatus());
        dto.put("createdAt", y.getCreatedAt().toString());
        return dto;
    }
}
