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
                "INSERT INTO student_section_enrollment (id, student_id, section_id, school_id, enrolled_at) VALUES (gen_random_uuid(), :studentId, :sid, :schoolId, NOW())")
                .setParameter("studentId", student.getId())
                .setParameter("sid", sectionId)
                .setParameter("schoolId", schoolId)
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
            PaginationParams pagination) {

        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff() && !requestContext.isTeacher()) {
            throw new ForbiddenException();
        }

        UUID schoolId = requestContext.getSchoolId();

        // Teachers: only see students in their assigned sections
        if (requestContext.isTeacher()) {
            return listStudentsForTeacher(search, pagination);
        }

        String query = "schoolId = ?1";
        Object[] params;

        if (search != null && !search.isBlank()) {
            query += " AND (LOWER(name) LIKE ?2 OR LOWER(studentId) LIKE ?2 OR LOWER(firstName) LIKE ?2 OR LOWER(lastName) LIKE ?2)";
            params = new Object[]{schoolId, "%" + search.toLowerCase() + "%"};
        } else {
            params = new Object[]{schoolId};
        }

        long total = studentRepository.count(query, params);
        List<Student> students = studentRepository.find(query, params)
                .page(pagination.getOffset() / pagination.getLimit(), pagination.getLimit()).list();

        return Response.ok(ApiResponse.success(students.stream().map(this::toDto).toList(),
                new PaginationMeta(total, pagination.getOffset(), pagination.getLimit()))).build();
    }

    private Response listStudentsForTeacher(String search, PaginationParams pagination) {
        UUID teacherId = requestContext.getUserId();
        String sql = "SELECT DISTINCT s.* FROM student s " +
                "JOIN student_section_enrollment sse ON sse.student_id = s.id " +
                "JOIN teacher_section_assignment tsa ON tsa.section_id = sse.section_id " +
                "WHERE tsa.teacher_id = :teacherId AND s.status = 'ACTIVE'";
        if (search != null && !search.isBlank()) {
            sql += " AND (LOWER(s.name) LIKE :search OR LOWER(s.student_id) LIKE :search OR LOWER(s.first_name) LIKE :search OR LOWER(s.last_name) LIKE :search)";
        }
        sql += " ORDER BY s.name";

        var q = em.createNativeQuery(sql, Student.class)
                .setParameter("teacherId", teacherId);
        if (search != null && !search.isBlank()) {
            q.setParameter("search", "%" + search.toLowerCase() + "%");
        }

        @SuppressWarnings("unchecked")
        List<Student> students = q.getResultList();
        long total = students.size();

        // Manual pagination
        int offset = pagination.getOffset();
        int limit = pagination.getLimit();
        List<Student> page = students.subList(
                Math.min(offset, students.size()),
                Math.min(offset + limit, students.size()));

        return Response.ok(ApiResponse.success(page.stream().map(this::toDto).toList(),
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
            Long inTeacherSection = (Long) em.createNativeQuery(
                    "SELECT COUNT(*) FROM student_section_enrollment sse " +
                    "JOIN teacher_section_assignment tsa ON tsa.section_id = sse.section_id " +
                    "WHERE sse.student_id = :studentId AND tsa.teacher_id = :teacherId")
                    .setParameter("studentId", id)
                    .setParameter("teacherId", requestContext.getUserId())
                    .getSingleResult();
            if (inTeacherSection == 0) throw new ForbiddenException();
        }

        return Response.ok(ApiResponse.success(toDto(student))).build();
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
            em.createNativeQuery("DELETE FROM student_section_enrollment WHERE student_id = :studentId")
                    .setParameter("studentId", id)
                    .executeUpdate();
            if (!sectionIdStr.isBlank()) {
                UUID sectionId = UUID.fromString(sectionIdStr);
                em.createNativeQuery(
                        "INSERT INTO student_section_enrollment (id, student_id, section_id, school_id, enrolled_at) VALUES (gen_random_uuid(), :studentId, :sid, :schoolId, NOW())")
                        .setParameter("studentId", id)
                        .setParameter("sid", sectionId)
                        .setParameter("schoolId", schoolId)
                        .executeUpdate();
            }
        }

        student.setUpdatedAt(LocalDateTime.now());
        studentRepository.persist(student);

        return Response.ok(ApiResponse.success(toDto(student))).build();
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
        }
        student.setUpdatedAt(LocalDateTime.now());
        studentRepository.persist(student);

        return Response.ok(ApiResponse.success(toDto(student))).build();
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

        // Fetch enrollment info (class & section)
        @SuppressWarnings("unchecked")
        List<Object[]> enrollment = em.createNativeQuery(
                "SELECT c.id, c.name, sec.id, sec.name FROM student_section_enrollment sse " +
                "JOIN section sec ON sec.id = sse.section_id " +
                "JOIN \"class\" c ON c.id = sec.class_id " +
                "WHERE sse.student_id = :studentId")
                .setParameter("studentId", s.getId())
                .getResultList();

        if (!enrollment.isEmpty()) {
            Object[] row = enrollment.get(0);
            dto.put("classId", row[0] != null ? row[0].toString() : null);
            dto.put("className", row[1]);
            dto.put("sectionId", row[2] != null ? row[2].toString() : null);
            dto.put("sectionName", row[3]);
        } else {
            dto.put("classId", null);
            dto.put("className", null);
            dto.put("sectionId", null);
            dto.put("sectionName", null);
        }

        return dto;
    }
}
