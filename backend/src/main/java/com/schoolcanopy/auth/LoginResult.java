package com.schoolcanopy.auth;

import java.util.Map;

public class LoginResult {

    private String token;
    private long timeoutSeconds;
    private String role;
    private String name;
    private String email;
    private String brandColor;
    private String logoUrl;
    private String schoolName;

    public LoginResult(String token, long timeoutSeconds, String role, String name, String email) {
        this.token = token;
        this.timeoutSeconds = timeoutSeconds;
        this.role = role;
        this.name = name;
        this.email = email;
    }

    public String getToken() { return token; }
    public long getTimeoutSeconds() { return timeoutSeconds; }
    public String getRole() { return role; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getBrandColor() { return brandColor; }
    public void setBrandColor(String brandColor) { this.brandColor = brandColor; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }

    public Map<String, Object> toDto() {
        var dto = new java.util.HashMap<String, Object>();
        dto.put("token", token);
        dto.put("role", role);
        dto.put("name", name);
        dto.put("email", email);
        if (brandColor != null) dto.put("brandColor", brandColor);
        if (logoUrl != null) dto.put("logoUrl", logoUrl);
        if (schoolName != null) dto.put("schoolName", schoolName);
        return dto;
    }
}
