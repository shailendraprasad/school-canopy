package com.schoolcanopy.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ConfigService {

    @Inject
    PlatformConfigRepository configRepository;

    // Default values (used if DB not yet available)
    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("session.timeout_minutes", "60"),
            Map.entry("security.max_failed_attempts", "5"),
            Map.entry("security.lockout_duration_minutes", "15"),
            Map.entry("security.password_reset_token_expiry_minutes", "60"),
            Map.entry("password.min_length", "8"),
            Map.entry("password.max_length", "128"),
            Map.entry("password.require_uppercase", "true"),
            Map.entry("password.require_lowercase", "true"),
            Map.entry("password.require_number", "true"),
            Map.entry("password.require_special", "false"),
            Map.entry("data.max_parents_per_student", "4"),
            Map.entry("data.max_attachments_per_message", "5"),
            Map.entry("data.max_attachment_size_mb", "10"),
            Map.entry("data.message_max_length", "5000"),
            Map.entry("data.invitation_expiry_hours", "48")
    );

    public String getValue(String key) {
        PlatformConfig config = configRepository.findByKey(key);
        if (config != null) {
            return config.getValue();
        }
        return DEFAULTS.get(key);
    }

    public int getIntValue(String key) {
        return Integer.parseInt(getValue(key));
    }

    public boolean getBooleanValue(String key) {
        return Boolean.parseBoolean(getValue(key));
    }

    public Map<String, String> getAllConfig() {
        Map<String, String> result = new HashMap<>(DEFAULTS);
        List<PlatformConfig> configs = configRepository.listAll();
        for (PlatformConfig config : configs) {
            result.put(config.getKey(), config.getValue());
        }
        return result;
    }

    // Convenience methods
    public long getSessionTimeoutMinutes() { return getIntValue("session.timeout_minutes"); }
    public int getMaxFailedAttempts() { return getIntValue("security.max_failed_attempts"); }
    public long getLockoutDurationMinutes() { return getIntValue("security.lockout_duration_minutes"); }
    public int getPasswordMinLength() { return getIntValue("password.min_length"); }
    public int getPasswordMaxLength() { return getIntValue("password.max_length"); }
    public boolean isPasswordRequireUppercase() { return getBooleanValue("password.require_uppercase"); }
    public boolean isPasswordRequireLowercase() { return getBooleanValue("password.require_lowercase"); }
    public boolean isPasswordRequireNumber() { return getBooleanValue("password.require_number"); }
    public boolean isPasswordRequireSpecial() { return getBooleanValue("password.require_special"); }
    public int getMaxParentsPerStudent() { return getIntValue("data.max_parents_per_student"); }
    public int getMaxAttachmentsPerMessage() { return getIntValue("data.max_attachments_per_message"); }
    public int getMaxAttachmentSizeMb() { return getIntValue("data.max_attachment_size_mb"); }
    public int getMessageMaxLength() { return getIntValue("data.message_max_length"); }
    public int getInvitationExpiryHours() { return getIntValue("data.invitation_expiry_hours"); }
}
