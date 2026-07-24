package com.schoolcanopy.school;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.schoolcanopy.audit.AuditService;
import com.schoolcanopy.auth.SessionService;
import com.schoolcanopy.common.ErrorDetail;
import com.schoolcanopy.common.exceptions.ConflictException;
import com.schoolcanopy.common.exceptions.ResourceNotFoundException;
import com.schoolcanopy.common.exceptions.ValidationException;

@ApplicationScoped
public class SchoolService {

    private static final String PREFIX_PATTERN = "^[A-Z]{2,5}$";

    @Inject
    SchoolRepository schoolRepository;

    @Inject
    AuditService auditService;

    @Inject
    SessionService sessionService;

    @Transactional
    public School onboard(SchoolCreateRequest request, UUID performedBy) {
        validate(request);

        if (schoolRepository.existsByPrefix(request.getPrefix())) {
            throw new ConflictException("prefix", "School prefix is already in use");
        }

        School school = new School();
        school.setName(request.getName().trim());
        school.setPrefix(request.getPrefix().trim().toUpperCase());
        school.setContactEmail(request.getContactEmail().trim());
        school.setAddress(request.getAddress() != null ? request.getAddress().trim() : null);
        school.setPhone(request.getPhone() != null ? request.getPhone().trim() : null);
        school.setStatus("ACTIVE");
        school.setCreatedAt(LocalDateTime.now());

        // Extended fields
        school.setBoardAffiliation(request.getBoardAffiliation());
        school.setUdiseCode(request.getUdiseCode());
        school.setSchoolType(request.getSchoolType());
        school.setMediumOfInstruction(request.getMediumOfInstruction());
        school.setFoundedYear(request.getFoundedYear());
        school.setCity(request.getCity());
        school.setState(request.getState());
        school.setPinCode(request.getPinCode());
        school.setPrincipalName(request.getPrincipalName());
        school.setPrincipalPhone(request.getPrincipalPhone());
        school.setWebsite(request.getWebsite());

        schoolRepository.persist(school);

        auditService.log("SCHOOL_ONBOARDED", school.getId(),
                school.getId().toString(), performedBy,
                String.format("{\"schoolName\":\"%s\",\"prefix\":\"%s\"}", school.getName(), school.getPrefix()));

        return school;
    }

    @Transactional
    public School deactivate(UUID schoolId, UUID performedBy) {
        School school = schoolRepository.findById(schoolId);
        if (school == null) {
            throw new ResourceNotFoundException("School not found");
        }
        school.setStatus("DEACTIVATED");
        school.setUpdatedAt(LocalDateTime.now());
        schoolRepository.persist(school);

        // Revoke all sessions for this school
        sessionService.revokeSessionsForSchool(schoolId);

        auditService.log("SCHOOL_DEACTIVATED", schoolId, schoolId.toString(), performedBy, null);
        return school;
    }

    @Transactional
    public School reactivate(UUID schoolId, UUID performedBy) {
        School school = schoolRepository.findById(schoolId);
        if (school == null) {
            throw new ResourceNotFoundException("School not found");
        }
        school.setStatus("ACTIVE");
        school.setUpdatedAt(LocalDateTime.now());
        schoolRepository.persist(school);

        auditService.log("SCHOOL_REACTIVATED", schoolId, schoolId.toString(), performedBy, null);
        return school;
    }

    private void validate(SchoolCreateRequest request) {
        List<ErrorDetail> errors = new ArrayList<>();

        if (request.getName() == null || request.getName().isBlank()) {
            errors.add(new ErrorDetail("name", "REQUIRED", "School name is required"));
        } else if (request.getName().trim().length() > 100) {
            errors.add(new ErrorDetail("name", "TOO_LONG", "School name must not exceed 100 characters"));
        }

        if (request.getPrefix() == null || request.getPrefix().isBlank()) {
            errors.add(new ErrorDetail("prefix", "REQUIRED", "School prefix is required"));
        } else if (!request.getPrefix().trim().matches(PREFIX_PATTERN)) {
            errors.add(new ErrorDetail("prefix", "INVALID_FORMAT", "Prefix must be 2-5 uppercase letters (A-Z)"));
        }

        if (request.getContactEmail() == null || request.getContactEmail().isBlank()) {
            errors.add(new ErrorDetail("contactEmail", "REQUIRED", "Contact email is required"));
        }

        if (request.getAddress() != null && request.getAddress().trim().length() > 200) {
            errors.add(new ErrorDetail("address", "TOO_LONG", "Address must not exceed 200 characters"));
        }

        if (request.getPhone() != null && request.getPhone().trim().length() > 20) {
            errors.add(new ErrorDetail("phone", "TOO_LONG", "Phone must not exceed 20 characters"));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }
}
