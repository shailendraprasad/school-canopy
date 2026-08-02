package com.schoolcanopy.academic;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.schoolcanopy.auth.InvitationResource;
import com.schoolcanopy.common.ApiResponse;
import com.schoolcanopy.common.PaginationMeta;
import com.schoolcanopy.common.PaginationParams;
import com.schoolcanopy.common.exceptions.ForbiddenException;
import com.schoolcanopy.common.exceptions.ResourceNotFoundException;
import com.schoolcanopy.common.exceptions.ValidationException;
import com.schoolcanopy.rbac.RequestContext;
import com.schoolcanopy.school.SchoolRepository;
import com.schoolcanopy.user.UserAccount;
import com.schoolcanopy.user.UserAccountRepository;

@Path("/api/school/students")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StudentResource {

    @Inject StudentRepository studentRepository;
    @Inject SchoolRepository schoolRepository;
    @Inject RequestContext requestContext;
    @Inject EntityManager em;
    @Inject UserAccountRepository userAccountRepository;
    @Inject InvitationResource invitationResource;
    @Inject AcademicYearService academicYearService;
    @Inject AcademicYearRepository academicYearRepository;

    @POST
    @Transactional
    public Response create(Map<String, String> body) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff()) {
            throw new ForbiddenException();
        }

        // Validate required fields
        String firstName = body.get("firstName");
        String lastName = body.get("lastName");
        if (firstName == null || firstName.isBlank()) {
            throw new ValidationException("firstName", "REQUIRED", "First name is required");
        }
        if (lastName == null || lastName.isBlank()) {
            throw new ValidationException("lastName", "REQUIRED", "Last name is required");
        }
        if (firstName.length() > 50) {
            throw new ValidationException("firstName", "INVALID", "First name must be at most 50 characters");
        }
        if (lastName.length() > 50) {
            throw new ValidationException("lastName", "INVALID", "Last name must be at most 50 characters");
        }

        // Class and Section are mandatory
        String sectionIdStr = body.get("sectionId");
        if (sectionIdStr == null || sectionIdStr.isBlank()) {
            throw new ValidationException("sectionId", "REQUIRED", "Class and Section must be assigned");
        }

        // Validate parent email is provided
        String parentEmail = body.get("parentEmail");
        if (parentEmail == null || parentEmail.isBlank()) {
            throw new ValidationException("parentEmail", "REQUIRED", "Parent email is required");
        }
        parentEmail = parentEmail.toLowerCase().trim();

        // Validate relationship type
        String relationship = body.get("relationship");
        if (relationship == null || relationship.isBlank()) {
            throw new ValidationException("relationship", "REQUIRED", "Parent/Guardian relationship is required");
        }
        if (!List.of("MOTHER", "FATHER", "GUARDIAN").contains(relationship.toUpperCase())) {
            throw new ValidationException("relationship", "INVALID", "Relationship must be Mother, Father, or Guardian");
        }
        relationship = relationship.toUpperCase();

        // Optional fields
        String address = body.get("address");
        String parentContact = body.get("parentContact");
        String bloodGroup = body.get("bloodGroup");

        UUID schoolId = requestContext.getSchoolId();
        AcademicYear activeYear = academicYearService.requireActiveYear(schoolId);
        var school = schoolRepository.findById(schoolId);
        if (school == null) throw new ResourceNotFoundException("School not found");

        // Generate Student ID
        String prefix = school.getPrefix();
        int year = Year.now().getValue();
        int nextNumber = getNextStudentNumber(schoolId, year);
        String studentIdStr = String.format("%s-%d-%04d", prefix, year, nextNumber);

        // Compose full name for legacy column
        String fullName = (firstName.trim() + " " + lastName.trim()).trim();

        Student student = new Student();
        student.setSchoolId(schoolId);
        student.setStudentId(studentIdStr);
        student.setName(fullName);
        student.setFirstName(firstName.trim());
        student.setLastName(lastName.trim());
        student.setAddress(address != null ? address.trim() : null);
        student.setParentContact(parentContact != null ? parentContact.trim() : null);
        student.setParentEmail(parentEmail);
        student.setBloodGroup(bloodGroup != null ? bloodGroup.trim().toUpperCase() : null);
        student.setStatus("ACTIVE");
        student.setCreatedAt(LocalDateTime.now());
        studentRepository.persist(student);

        // Enroll student in section
        UUID sectionId = UUID.fromString(sectionIdStr);
        em.createNativeQuery(
                "INSERT INTO student_section_enrollment (id, student_id, section_id, school_id, academic_year_id, status, enrolled_at) " +
                "VALUES (gen_random_uuid(), :studentId, :sid, :schoolId, :yearId, 'ACTIVE', NOW())")
                .setParameter("studentId", student.getId())
                .setParameter("sid", sectionId)
                .setParameter("schoolId", schoolId)
                .setParameter("yearId", activeYear.getId())
                .executeUpdate();

        // Auto-link parent: find or create parent account and link to student
        String invitationToken = linkParentToStudent(student, parentEmail, schoolId, relationship);

        Map<String, Object> dto = toDto(student);
        if (invitationToken != null) {
            dto.put("invitationToken", invitationToken);
        }
        return Response.status(Response.Status.CREATED).entity(ApiResponse.success(dto)).build();
    }

    /**
     * Find or create parent account, link to student with relationship type, and send invitation if needed.
     * @return invitation token when a new invite was created; otherwise null
     */
    private String linkParentToStudent(Student student, String parentEmail, UUID schoolId, String relationship) {
        UserAccount parent = userAccountRepository.findByEmail(parentEmail);
        boolean isNewParent = false;

        if (parent == null) {
            // Create new parent account
            parent = new UserAccount();
            parent.setEmail(parentEmail);
            parent.setName(parentEmail.split("@")[0]);
            parent.setRole("PARENT");
            parent.setStatus("PENDING");
            parent.setCreatedAt(LocalDateTime.now());
            userAccountRepository.persist(parent);
            isNewParent = true;
        }

        // Check if already linked
        Long alreadyLinked = (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM parent_student_link WHERE parent_id = :pid AND student_id = :sid")
                .setParameter("pid", parent.getId())
                .setParameter("sid", student.getId())
                .getSingleResult();

        if (alreadyLinked == 0) {
            em.createNativeQuery(
                    "INSERT INTO parent_student_link (id, parent_id, student_id, school_id, relationship, linked_at) VALUES (gen_random_uuid(), :pid, :sid, :schoolId, :rel, NOW())")
                    .setParameter("pid", parent.getId())
                    .setParameter("sid", student.getId())
                    .setParameter("schoolId", schoolId)
                    .setParameter("rel", relationship)
                    .executeUpdate();
        }

        // Invite new or still-pending parent accounts so the UI can show a shareable link
        if (isNewParent || "PENDING".equals(parent.getStatus())) {
            return invitationResource.createInvitation(parent.getId(), parentEmail);
        }
        return null;
    }

    @GET
    public Response list(
            @QueryParam("search") String search,
            @QueryParam("includeArchived") @DefaultValue("false") boolean includeArchived,
            PaginationParams pagination) {

        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff() && !requestContext.isTeacher()) {
            throw new ForbiddenException();
        }

        UUID schoolId = requestContext.getSchoolId();
        AcademicYear activeYear = academicYearRepository.findActiveBySchoolId(schoolId);

        // Teachers: only see students in their assigned sections
        if (requestContext.isTeacher()) {
            return listStudentsForTeacher(search, pagination, includeArchived, activeYear);
        }

        String query = "schoolId = ?1";
        Object[] params;

        if (!includeArchived) {
            query += " AND status = 'ACTIVE'";
        }

        if (search != null && !search.isBlank()) {
            query += " AND (LOWER(name) LIKE ?2 OR LOWER(studentId) LIKE ?2 OR LOWER(firstName) LIKE ?2 OR LOWER(lastName) LIKE ?2)";
            params = new Object[]{schoolId, "%" + search.toLowerCase() + "%"};
        } else {
            params = new Object[]{schoolId};
        }

        long total = studentRepository.count(query, params);
        List<Student> students = studentRepository.find(query, params)
                .page(pagination.getOffset() / pagination.getLimit(), pagination.getLimit()).list();

        if (activeYear != null && !includeArchived) {
            students = students.stream().filter(s -> hasActiveEnrollmentInYear(s.getId(), activeYear.getId())).toList();
            total = students.size();
        }

        return Response.ok(ApiResponse.success(students.stream().map(s -> toDto(s, activeYear)).toList(),
                new PaginationMeta(total, pagination.getOffset(), pagination.getLimit()))).build();
    }

    private boolean hasActiveEnrollmentInYear(UUID studentId, UUID yearId) {
        Long count = (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM student_section_enrollment WHERE student_id = :sid AND academic_year_id = :yearId AND status = 'ACTIVE'")
                .setParameter("sid", studentId)
                .setParameter("yearId", yearId)
                .getSingleResult();
        return count > 0;
    }

    private Response listStudentsForTeacher(String search, PaginationParams pagination, boolean includeArchived, AcademicYear activeYear) {
        UUID teacherId = requestContext.getUserId();
        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT s.* FROM student s " +
                "JOIN student_section_enrollment sse ON sse.student_id = s.id " +
                "JOIN teacher_section_assignment tsa ON tsa.section_id = sse.section_id " +
                "WHERE tsa.teacher_id = :teacherId AND tsa.status = 'ACTIVE' AND sse.status = 'ACTIVE'");
        if (!includeArchived) sql.append(" AND s.status = 'ACTIVE'");
        if (activeYear != null) sql.append(" AND sse.academic_year_id = :yearId AND tsa.academic_year_id = :yearId");
        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(s.name) LIKE :search OR LOWER(s.student_id) LIKE :search OR LOWER(s.first_name) LIKE :search OR LOWER(s.last_name) LIKE :search)");
        }
        sql.append(" ORDER BY s.name");

        var q = em.createNativeQuery(sql.toString(), Student.class).setParameter("teacherId", teacherId);
        if (activeYear != null) q.setParameter("yearId", activeYear.getId());
        if (search != null && !search.isBlank()) q.setParameter("search", "%" + search.toLowerCase() + "%");

        @SuppressWarnings("unchecked")
        List<Student> students = q.getResultList();
        long total = students.size();
        int offset = pagination.getOffset();
        int limit = pagination.getLimit();
        List<Student> page = students.subList(Math.min(offset, students.size()), Math.min(offset + limit, students.size()));

        return Response.ok(ApiResponse.success(page.stream().map(s -> toDto(s, activeYear)).toList(),
                new PaginationMeta(total, offset, limit))).build();
    }

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") UUID id) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff() && !requestContext.isTeacher()) {
            throw new ForbiddenException();
        }

        Student student = studentRepository.findById(id);
        if (student == null) throw new ResourceNotFoundException();

        // Teachers: verify student is in one of their assigned sections
        if (requestContext.isTeacher()) {
            AcademicYear activeYear = academicYearRepository.findActiveBySchoolId(requestContext.getSchoolId());
            String yearClause = activeYear != null ? " AND sse.academic_year_id = :yearId AND tsa.academic_year_id = :yearId" : "";
            var q = em.createNativeQuery(
                    "SELECT COUNT(*) FROM student_section_enrollment sse " +
                    "JOIN teacher_section_assignment tsa ON tsa.section_id = sse.section_id " +
                    "WHERE sse.student_id = :studentId AND tsa.teacher_id = :teacherId AND sse.status = 'ACTIVE' AND tsa.status = 'ACTIVE'" + yearClause)
                    .setParameter("studentId", id)
                    .setParameter("teacherId", requestContext.getUserId());
            if (activeYear != null) q.setParameter("yearId", activeYear.getId());
            Long inTeacherSection = (Long) q.getSingleResult();
            if (inTeacherSection == 0) throw new ForbiddenException();
        }

        AcademicYear activeYear = academicYearRepository.findActiveBySchoolId(requestContext.getSchoolId());
        return Response.ok(ApiResponse.success(toDto(student, activeYear))).build();
    }

    @GET
    @Path("/{id}/history")
    public Response getHistory(@PathParam("id") UUID id) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff() && !requestContext.isTeacher()) {
            throw new ForbiddenException();
        }
        Student student = studentRepository.findById(id);
        if (student == null) throw new ResourceNotFoundException();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT ay.id, ay.name, ay.status, c.name, sec.name, sse.status, sse.enrolled_at, sse.ended_at " +
                "FROM student_section_enrollment sse " +
                "JOIN academic_year ay ON ay.id = sse.academic_year_id " +
                "JOIN section sec ON sec.id = sse.section_id " +
                "JOIN \"class\" c ON c.id = sec.class_id " +
                "WHERE sse.student_id = :studentId ORDER BY ay.starts_on DESC")
                .setParameter("studentId", id)
                .getResultList();

        var history = rows.stream().map(r -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("academicYearId", r[0]);
            m.put("academicYearName", r[1]);
            m.put("academicYearStatus", r[2]);
            m.put("className", r[3]);
            m.put("sectionName", r[4]);
            m.put("enrollmentStatus", r[5]);
            m.put("enrolledAt", r[6] != null ? r[6].toString() : null);
            m.put("endedAt", r[7] != null ? r[7].toString() : null);
            return m;
        }).toList();
        return Response.ok(ApiResponse.success(history)).build();
    }

    @GET
    @Path("/{id}/attendance-history")
    public Response getAttendanceHistory(@PathParam("id") UUID id, @QueryParam("academicYearId") UUID academicYearId) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff() && !requestContext.isTeacher()) {
            throw new ForbiddenException();
        }
        Student student = studentRepository.findById(id);
        if (student == null) throw new ResourceNotFoundException();

        UUID yearId = academicYearId;
        if (yearId == null) {
            AcademicYear active = academicYearRepository.findActiveBySchoolId(requestContext.getSchoolId());
            if (active == null) return Response.ok(ApiResponse.success(Map.of())).build();
            yearId = active.getId();
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT COUNT(a.id), " +
                "COUNT(CASE WHEN a.status = 'PRESENT' THEN 1 END), " +
                "COUNT(CASE WHEN a.status = 'ABSENT' THEN 1 END), " +
                "COUNT(CASE WHEN a.status = 'LATE' THEN 1 END) " +
                "FROM attendance a WHERE a.student_id = :sid AND a.academic_year_id = :yearId")
                .setParameter("sid", id)
                .setParameter("yearId", yearId)
                .getResultList();

        var m = new java.util.HashMap<String, Object>();
        if (!rows.isEmpty()) {
            Object[] r = rows.get(0);
            long total = ((Number) r[0]).longValue();
            long present = ((Number) r[1]).longValue();
            long absent = ((Number) r[2]).longValue();
            long late = ((Number) r[3]).longValue();
            m.put("totalDays", total);
            m.put("presentDays", present);
            m.put("absentDays", absent);
            m.put("lateDays", late);
            m.put("percentage", total > 0 ? Math.round((present + late) * 100.0 / total) : 0);
        }
        return Response.ok(ApiResponse.success(m)).build();
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") UUID id, Map<String, String> body) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff()) {
            throw new ForbiddenException();
        }

        Student student = studentRepository.findById(id);
        if (student == null) throw new ResourceNotFoundException();

        if (body.containsKey("firstName")) {
            student.setFirstName(body.get("firstName"));
        }
        if (body.containsKey("lastName")) {
            student.setLastName(body.get("lastName"));
        }
        // Update legacy name column
        if (body.containsKey("firstName") || body.containsKey("lastName")) {
            String fn = student.getFirstName() != null ? student.getFirstName() : "";
            String ln = student.getLastName() != null ? student.getLastName() : "";
            student.setName((fn + " " + ln).trim());
        }
        // Backward compat: if only "name" is sent (old API)
        if (body.containsKey("name") && !body.containsKey("firstName")) {
            student.setName(body.get("name"));
        }

        if (body.containsKey("address")) {
            student.setAddress(body.get("address"));
        }
        if (body.containsKey("parentContact")) {
            student.setParentContact(body.get("parentContact"));
        }
        if (body.containsKey("bloodGroup")) {
            String bg = body.get("bloodGroup");
            student.setBloodGroup(bg != null ? bg.trim().toUpperCase() : null);
        }
        if (body.containsKey("parentEmail")) {
            String newParentEmail = body.get("parentEmail");
            if (newParentEmail != null && !newParentEmail.isBlank()) {
                newParentEmail = newParentEmail.toLowerCase().trim();
                // If parent email changed, link new parent
                if (!newParentEmail.equals(student.getParentEmail())) {
                    student.setParentEmail(newParentEmail);
                    UUID schoolId = requestContext.getSchoolId();
                    String rel = body.getOrDefault("relationship", "GUARDIAN").toUpperCase();
                    if (!List.of("MOTHER", "FATHER", "GUARDIAN").contains(rel)) rel = "GUARDIAN";
                    linkParentToStudent(student, newParentEmail, schoolId, rel);
                }
            }
        }

        // Handle section enrollment update
        String sectionIdStr = body.get("sectionId");
        if (sectionIdStr != null) {
            UUID schoolId = requestContext.getSchoolId();
            AcademicYear activeYear = academicYearService.requireActiveYear(schoolId);
            academicYearService.closeActiveEnrollment(id, activeYear.getId());
            if (!sectionIdStr.isBlank()) {
                UUID sectionId = UUID.fromString(sectionIdStr);
                academicYearService.createActiveEnrollment(id, sectionId, schoolId, activeYear.getId());
            }
        }

        student.setUpdatedAt(LocalDateTime.now());
        studentRepository.persist(student);

        AcademicYear activeYear = academicYearRepository.findActiveBySchoolId(requestContext.getSchoolId());
        return Response.ok(ApiResponse.success(toDto(student, activeYear))).build();
    }

    @PATCH
    @Path("/{id}/status")
    @Transactional
    public Response updateStatus(@PathParam("id") UUID id, Map<String, String> body) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff()) {
            throw new ForbiddenException();
        }

        Student student = studentRepository.findById(id);
        if (student == null) throw new ResourceNotFoundException();

        String newStatus = body.get("status");
        if ("DEACTIVATED".equalsIgnoreCase(newStatus)) {
            student.setStatus("DEACTIVATED");
        } else if ("ACTIVE".equalsIgnoreCase(newStatus)) {
            student.setStatus("ACTIVE");
        } else if ("GRADUATED".equalsIgnoreCase(newStatus)) {
            student.setStatus("GRADUATED");
        } else if ("WITHDRAWN".equalsIgnoreCase(newStatus)) {
            student.setStatus("WITHDRAWN");
        }
        student.setUpdatedAt(LocalDateTime.now());
        studentRepository.persist(student);

        AcademicYear activeYear = academicYearRepository.findActiveBySchoolId(requestContext.getSchoolId());
        return Response.ok(ApiResponse.success(toDto(student, activeYear))).build();
    }

    private int getNextStudentNumber(UUID schoolId, int year) {
        Object result = em.createNativeQuery(
                "INSERT INTO student_id_sequence (id, school_id, year, last_number) " +
                "VALUES (gen_random_uuid(), :schoolId, :year, 1) " +
                "ON CONFLICT (school_id, year) " +
                "DO UPDATE SET last_number = student_id_sequence.last_number + 1 " +
                "RETURNING last_number")
                .setParameter("schoolId", schoolId)
                .setParameter("year", year)
                .getSingleResult();
        return ((Number) result).intValue();
    }

    private Map<String, Object> toDto(Student s) {
        AcademicYear activeYear = academicYearRepository.findActiveBySchoolId(s.getSchoolId());
        return toDto(s, activeYear);
    }

    private Map<String, Object> toDto(Student s, AcademicYear activeYear) {
        Map<String, Object> dto = new java.util.HashMap<>();
        dto.put("id", s.getId());
        dto.put("studentId", s.getStudentId());
        dto.put("name", s.getName());
        dto.put("firstName", s.getFirstName());
        dto.put("lastName", s.getLastName());
        dto.put("address", s.getAddress());
        dto.put("parentContact", s.getParentContact());
        dto.put("parentEmail", s.getParentEmail());
        dto.put("bloodGroup", s.getBloodGroup());
        dto.put("status", s.getStatus());
        dto.put("createdAt", s.getCreatedAt().toString());

        String enrollmentSql =
                "SELECT c.id, c.name, sec.id, sec.name, ay.id, ay.name FROM student_section_enrollment sse " +
                "JOIN section sec ON sec.id = sse.section_id " +
                "JOIN \"class\" c ON c.id = sec.class_id " +
                "JOIN academic_year ay ON ay.id = sse.academic_year_id " +
                "WHERE sse.student_id = :studentId AND sse.status = 'ACTIVE'";
        if (activeYear != null) enrollmentSql += " AND sse.academic_year_id = :yearId";
        enrollmentSql += " ORDER BY sse.enrolled_at DESC LIMIT 1";

        var q = em.createNativeQuery(enrollmentSql).setParameter("studentId", s.getId());
        if (activeYear != null) q.setParameter("yearId", activeYear.getId());

        @SuppressWarnings("unchecked")
        List<Object[]> enrollment = q.getResultList();

        if (!enrollment.isEmpty()) {
            Object[] row = enrollment.get(0);
            dto.put("classId", row[0] != null ? row[0].toString() : null);
            dto.put("className", row[1]);
            dto.put("sectionId", row[2] != null ? row[2].toString() : null);
            dto.put("sectionName", row[3]);
            dto.put("academicYearId", row[4] != null ? row[4].toString() : null);
            dto.put("academicYearName", row[5]);
        } else {
            dto.put("classId", null);
            dto.put("className", null);
            dto.put("sectionId", null);
            dto.put("sectionName", null);
            dto.put("academicYearId", null);
            dto.put("academicYearName", null);
        }

        return dto;
    }
}
