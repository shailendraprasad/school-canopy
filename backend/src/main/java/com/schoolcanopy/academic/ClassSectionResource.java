package com.schoolcanopy.academic;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.schoolcanopy.common.ApiResponse;
import com.schoolcanopy.common.exceptions.ForbiddenException;
import com.schoolcanopy.common.exceptions.ValidationException;
import com.schoolcanopy.rbac.RequestContext;

@Path("/api/school")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ClassSectionResource {

    @Inject ClassRepository classRepository;
    @Inject SectionRepository sectionRepository;
    @Inject RequestContext requestContext;

    @POST
    @Path("/classes")
    @Transactional
    public Response createClass(Map<String, String> body) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff()) {
            throw new ForbiddenException();
        }

        String name = body.get("name");
        String gradeLevel = body.get("gradeLevel");

        if (name == null || name.isBlank() || name.length() > 50) {
            throw new ValidationException("name", "INVALID", "Class name is required (max 50 characters)");
        }
        if (gradeLevel == null || gradeLevel.isBlank()) {
            throw new ValidationException("gradeLevel", "REQUIRED", "Grade level is required");
        }

        SchoolClass clazz = new SchoolClass();
        clazz.setSchoolId(requestContext.getSchoolId());
        clazz.setName(name.trim());
        clazz.setGradeLevel(gradeLevel.trim());
        clazz.setStatus("ACTIVE");
        clazz.setCreatedAt(LocalDateTime.now());
        classRepository.persist(clazz);

        return Response.status(Response.Status.CREATED).entity(ApiResponse.success(classToDto(clazz))).build();
    }

    @GET
    @Path("/classes")
    public Response listClasses() {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff() && !requestContext.isTeacher()) {
            throw new ForbiddenException();
        }

        UUID schoolId = requestContext.getSchoolId();

        // Teachers: only see classes that contain their assigned sections
        if (requestContext.isTeacher()) {
            @SuppressWarnings("unchecked")
            List<SchoolClass> classes = classRepository.getEntityManager().createNativeQuery(
                    "SELECT DISTINCT c.* FROM class c " +
                    "JOIN section s ON s.class_id = c.id " +
                    "JOIN teacher_section_assignment tsa ON tsa.section_id = s.id " +
                    "WHERE tsa.teacher_id = :teacherId AND c.school_id = :schoolId", SchoolClass.class)
                    .setParameter("teacherId", requestContext.getUserId())
                    .setParameter("schoolId", schoolId)
                    .getResultList();
            return Response.ok(ApiResponse.success(classes.stream().map(this::classToDto).toList())).build();
        }

        List<SchoolClass> classes = classRepository.find("schoolId", schoolId).list();
        return Response.ok(ApiResponse.success(classes.stream().map(this::classToDto).toList())).build();
    }

    @POST
    @Path("/sections")
    @Transactional
    public Response createSection(Map<String, String> body) {
        if (!requestContext.isSchoolAdministrator() && !requestContext.isOfficeStaff()) {
            throw new ForbiddenException();
        }

        String name = body.get("name");
        String classId = body.get("classId");

        if (name == null || name.isBlank() || name.length() > 50) {
            throw new ValidationException("name", "INVALID", "Section name is required (max 50 characters)");
        }
        if (classId == null || classId.isBlank()) {
            throw new ValidationException("classId", "REQUIRED", "Class is required");
        }

        Section section = new Section();
        section.setSchoolId(requestContext.getSchoolId());
        section.setClassId(UUID.fromString(classId));
        section.setName(name.trim());
        section.setStatus("ACTIVE");
        section.setCreatedAt(LocalDateTime.now());
        sectionRepository.persist(section);

        return Response.status(Response.Status.CREATED).entity(ApiResponse.success(sectionToDto(section))).build();
    }

    @GET
    @Path("/sections")
    public Response listSections(@QueryParam("classId") String classId) {
        UUID schoolId = requestContext.getSchoolId();

        // Teachers: only see their assigned sections
        if (requestContext.isTeacher()) {
            String sql = "SELECT DISTINCT s.* FROM section s " +
                    "JOIN teacher_section_assignment tsa ON tsa.section_id = s.id " +
                    "WHERE tsa.teacher_id = :teacherId AND s.school_id = :schoolId";
            if (classId != null && !classId.isBlank()) {
                sql += " AND s.class_id = :classId";
            }
            var q = sectionRepository.getEntityManager().createNativeQuery(sql, Section.class)
                    .setParameter("teacherId", requestContext.getUserId())
                    .setParameter("schoolId", schoolId);
            if (classId != null && !classId.isBlank()) {
                q.setParameter("classId", UUID.fromString(classId));
            }
            @SuppressWarnings("unchecked")
            List<Section> sections = q.getResultList();
            return Response.ok(ApiResponse.success(sections.stream().map(this::sectionToDto).toList())).build();
        }

        List<Section> sections;
        if (classId != null && !classId.isBlank()) {
            sections = sectionRepository.find("schoolId = ?1 AND classId = ?2", schoolId, UUID.fromString(classId)).list();
        } else {
            sections = sectionRepository.find("schoolId", schoolId).list();
        }
        return Response.ok(ApiResponse.success(sections.stream().map(this::sectionToDto).toList())).build();
    }

    private Map<String, Object> classToDto(SchoolClass c) {
        return Map.of("id", c.getId(), "name", c.getName(), "gradeLevel", c.getGradeLevel(),
                "status", c.getStatus(), "createdAt", c.getCreatedAt().toString());
    }

    private Map<String, Object> sectionToDto(Section s) {
        return Map.of("id", s.getId(), "name", s.getName(), "classId", s.getClassId(),
                "status", s.getStatus(), "createdAt", s.getCreatedAt().toString());
    }
}
