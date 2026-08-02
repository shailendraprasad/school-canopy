package com.schoolcanopy.academic;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.schoolcanopy.audit.AuditService;
import com.schoolcanopy.common.ApiResponse;
import com.schoolcanopy.common.exceptions.ForbiddenException;
import com.schoolcanopy.common.exceptions.ValidationException;
import com.schoolcanopy.rbac.RequestContext;
import com.schoolcanopy.school.SchoolRepository;

@Path("/api/school/admissions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdmissionsResource {

    @Inject RequestContext requestContext;
    @Inject EntityManager em;
    @Inject AcademicYearService academicYearService;
    @Inject StudentRepository studentRepository;
    @Inject SchoolRepository schoolRepository;
    @Inject AuditService auditService;

    /**
     * Bulk admit new students into the ACTIVE academic year.
     * Body: { sectionId, rows: [{ firstName, lastName, parentEmail, relationship, address?, parentContact?, bloodGroup? }] }
     */
    @POST
    @Path("/bulk")
    @Transactional
    public Response bulkAdmit(Map<String, Object> body) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff()) {
            throw new ForbiddenException();
        }

        String sectionIdStr = (String) body.get("sectionId");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> rows = (List<Map<String, String>>) body.get("rows");

        if (sectionIdStr == null || sectionIdStr.isBlank()) {
            throw new ValidationException("sectionId", "REQUIRED", "Target section is required");
        }
        if (rows == null || rows.isEmpty()) {
            throw new ValidationException("rows", "REQUIRED", "At least one student row is required");
        }
        if (rows.size() > 200) {
            throw new ValidationException("rows", "TOO_MANY", "Maximum 200 students per batch");
        }

        UUID schoolId = requestContext.getSchoolId();
        AcademicYear activeYear = academicYearService.requireActiveYear(schoolId);
        UUID sectionId = UUID.fromString(sectionIdStr);
        var school = schoolRepository.findById(schoolId);
        if (school == null) throw new ValidationException("school", "NOT_FOUND", "School not found");

        String prefix = school.getPrefix();
        int year = Year.now().getValue();
        int admitted = 0;
        List<Map<String, Object>> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            try {
                String firstName = requireField(row, "firstName", i);
                String lastName = requireField(row, "lastName", i);
                String parentEmail = requireField(row, "parentEmail", i).toLowerCase().trim();
                String relationship = requireField(row, "relationship", i).toUpperCase();
                if (!List.of("MOTHER", "FATHER", "GUARDIAN").contains(relationship)) {
                    throw new ValidationException("relationship", "INVALID", "Row " + (i + 1) + ": invalid relationship");
                }

                int nextNumber = getNextStudentNumber(schoolId, year);
                String studentCode = String.format("%s-%d-%04d", prefix, year, nextNumber);
                String fullName = firstName.trim() + " " + lastName.trim();

                UUID studentUuid = UUID.randomUUID();
                em.createNativeQuery(
                        "INSERT INTO student (id, school_id, student_id, name, first_name, last_name, address, parent_contact, parent_email, blood_group, status, created_at) " +
                        "VALUES (:id, :schoolId, :code, :name, :firstName, :lastName, :address, :contact, :email, :blood, 'ACTIVE', NOW())")
                        .setParameter("id", studentUuid)
                        .setParameter("schoolId", schoolId)
                        .setParameter("code", studentCode)
                        .setParameter("name", fullName)
                        .setParameter("firstName", firstName.trim())
                        .setParameter("lastName", lastName.trim())
                        .setParameter("address", row.getOrDefault("address", "").trim())
                        .setParameter("contact", row.getOrDefault("parentContact", "").trim())
                        .setParameter("email", parentEmail)
                        .setParameter("blood", row.getOrDefault("bloodGroup", "").trim().toUpperCase())
                        .executeUpdate();

                em.createNativeQuery(
                        "INSERT INTO student_section_enrollment (id, student_id, section_id, school_id, academic_year_id, status, enrolled_at) " +
                        "VALUES (gen_random_uuid(), :studentId, :sectionId, :schoolId, :yearId, 'ACTIVE', NOW())")
                        .setParameter("studentId", studentUuid)
                        .setParameter("sectionId", sectionId)
                        .setParameter("schoolId", schoolId)
                        .setParameter("yearId", activeYear.getId())
                        .executeUpdate();

                admitted++;
            } catch (ValidationException ve) {
                errors.add(Map.of("row", i + 1, "message", ve.getFieldErrors().get(0).getMessage()));
            }
        }

        auditService.log("BULK_ADMISSION", schoolId, activeYear.getId().toString(),
                requestContext.getUserId(), admitted + " students admitted");

        return Response.ok(ApiResponse.success(Map.of(
                "admitted", admitted,
                "failed", errors.size(),
                "errors", errors
        ))).build();
    }

    private String requireField(Map<String, String> row, String field, int index) {
        String val = row.get(field);
        if (val == null || val.isBlank()) {
            throw new ValidationException(field, "REQUIRED", "Row " + (index + 1) + ": " + field + " is required");
        }
        return val;
    }

    private int getNextStudentNumber(UUID schoolId, int year) {
        em.createNativeQuery(
                "INSERT INTO student_id_sequence (school_id, year, last_number) VALUES (:schoolId, :year, 1) " +
                "ON CONFLICT (school_id, year) DO UPDATE SET last_number = student_id_sequence.last_number + 1")
                .setParameter("schoolId", schoolId)
                .setParameter("year", year)
                .executeUpdate();
        return ((Number) em.createNativeQuery(
                "SELECT last_number FROM student_id_sequence WHERE school_id = :schoolId AND year = :year")
                .setParameter("schoolId", schoolId)
                .setParameter("year", year)
                .getSingleResult()).intValue();
    }
}
