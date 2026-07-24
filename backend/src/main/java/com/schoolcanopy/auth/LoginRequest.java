package com.schoolcanopy.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    /**
     * Portal identifier: "platform" or "school"
     */
    @NotBlank(message = "Portal is required")
    private String portal;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getPortal() { return portal; }
    public void setPortal(String portal) { this.portal = portal; }
}
