package com.schoolcanopy.academic;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import com.schoolcanopy.common.exceptions.ResourceNotFoundException;
import com.schoolcanopy.common.exceptions.ValidationException;

@ApplicationScoped
public class AcademicYearService {

    @Inject AcademicYearRepository academicYearRepository;
    @Inject EntityManager em;

    public AcademicYear requireActiveYear(UUID schoolId) {
        AcademicYear year = academicYearRepository.findActiveBySchoolId(schoolId);
        if (year == null) {
            throw new ValidationException("academicYear", "NO_ACTIVE_YEAR", "No active academic year configured for this school");
        }
        return year;
    }

    public AcademicYear requireWritableYear(UUID academicYearId, UUID schoolId) {
        AcademicYear year = academicYearRepository.findById(academicYearId);
        if (year == null || !year.getSchoolId().equals(schoolId)) {
            throw new ResourceNotFoundException("Academic year not found");
        }
        if (!"ACTIVE".equals(year.getStatus())) {
            throw new ValidationException("academicYear", "CLOSED", "This academic year is closed and read-only");
        }
        return year;
    }

    public void requireActiveWritable(UUID schoolId) {
        requireWritableYear(requireActiveYear(schoolId).getId(), schoolId);
    }

    public void closeActiveEnrollment(UUID studentId, UUID academicYearId) {
        em.createNativeQuery(
                "UPDATE student_section_enrollment SET status = 'CLOSED', ended_at = NOW() " +
                "WHERE student_id = :studentId AND academic_year_id = :yearId AND status = 'ACTIVE'")
                .setParameter("studentId", studentId)
                .setParameter("yearId", academicYearId)
                .executeUpdate();
    }

    public void createActiveEnrollment(UUID studentId, UUID sectionId, UUID schoolId, UUID academicYearId) {
        em.createNativeQuery(
                "INSERT INTO student_section_enrollment " +
                "(id, student_id, section_id, school_id, academic_year_id, status, enrolled_at) " +
                "VALUES (gen_random_uuid(), :studentId, :sectionId, :schoolId, :yearId, 'ACTIVE', NOW())")
                .setParameter("studentId", studentId)
                .setParameter("sectionId", sectionId)
                .setParameter("schoolId", schoolId)
                .setParameter("yearId", academicYearId)
                .executeUpdate();
    }

    public long countUnhandledActiveEnrollments(UUID schoolId, UUID academicYearId) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM student_section_enrollment sse " +
                "JOIN student s ON s.id = sse.student_id " +
                "WHERE sse.school_id = :schoolId AND sse.academic_year_id = :yearId " +
                "AND sse.status = 'ACTIVE' AND s.status = 'ACTIVE'")
                .setParameter("schoolId", schoolId)
                .setParameter("yearId", academicYearId)
                .getSingleResult()).longValue();
    }
}
