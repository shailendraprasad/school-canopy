package com.schoolcanopy.school;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.schoolcanopy.common.ApiResponse;
import com.schoolcanopy.common.exceptions.ForbiddenException;
import com.schoolcanopy.common.exceptions.ResourceNotFoundException;
import com.schoolcanopy.rbac.RequestContext;

@Path("/api/school/branding")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SchoolBrandingResource {

    @Inject RequestContext requestContext;
    @Inject SchoolRepository schoolRepository;

    @GET
    public Response getBranding() {
        UUID schoolId = requestContext.getSchoolId();
        if (schoolId == null) throw new ForbiddenException();

        School school = schoolRepository.findById(schoolId);
        if (school == null) throw new ResourceNotFoundException("School not found");

        return Response.ok(ApiResponse.success(Map.of(
                "brandColor", school.getBrandColor() != null ? school.getBrandColor() : "#4a6b8a",
                "logoUrl", school.getLogoUrl() != null ? school.getLogoUrl() : "",
                "schoolName", school.getName()
        ))).build();
    }

    @PUT
    @Transactional
    public Response updateBranding(Map<String, String> body) {
        if (!requestContext.isSchoolAdministrator()) throw new ForbiddenException();

        UUID schoolId = requestContext.getSchoolId();
        School school = schoolRepository.findById(schoolId);
        if (school == null) throw new ResourceNotFoundException("School not found");

        if (body.containsKey("brandColor")) {
            String color = body.get("brandColor");
            // Basic hex color validation
            if (color != null && color.matches("^#[0-9a-fA-F]{6}$")) {
                school.setBrandColor(color);
            }
        }
        if (body.containsKey("logoUrl")) {
            school.setLogoUrl(body.get("logoUrl"));
        }

        school.setUpdatedAt(LocalDateTime.now());
        schoolRepository.persist(school);

        return Response.ok(ApiResponse.success(Map.of(
                "brandColor", school.getBrandColor() != null ? school.getBrandColor() : "#4a6b8a",
                "logoUrl", school.getLogoUrl() != null ? school.getLogoUrl() : "",
                "schoolName", school.getName()
        ))).build();
    }
}
