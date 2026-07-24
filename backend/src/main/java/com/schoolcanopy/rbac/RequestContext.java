package com.schoolcanopy.rbac;

import java.util.UUID;

import jakarta.enterprise.context.RequestScoped;

/**
 * Holds the current request's authenticated user context.
 * Populated by SessionFilter + TenantContextFilter.
 */
@RequestScoped
public class RequestContext {

    private UUID userId;
    private UUID schoolId;
    private String role;
    private String email;
    private String name;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getSchoolId() { return schoolId; }
    public void setSchoolId(UUID schoolId) { this.schoolId = schoolId; }
    public String getCurrentUserRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(role);
    }

    public boolean isPlatformTeamMember() {
        return "PLATFORM_TEAM_MEMBER".equals(role);
    }

    public boolean isSchoolAdministrator() {
        return "SCHOOL_ADMINISTRATOR".equals(role);
    }

    public boolean isTeacher() {
        return "TEACHER".equals(role);
    }

    public boolean isOfficeStaff() {
        return "OFFICE_STAFF".equals(role);
    }

    public boolean isParent() {
        return "PARENT".equals(role);
    }
}
