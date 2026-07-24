package com.schoolcanopy.config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.schoolcanopy.audit.AuditService;
import com.schoolcanopy.common.ApiResponse;
import com.schoolcanopy.common.ErrorDetail;
import com.schoolcanopy.common.exceptions.ForbiddenException;
import com.schoolcanopy.common.exceptions.ValidationException;
import com.schoolcanopy.rbac.RequestContext;

@Path("/api/platform/config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConfigResource {

    private static final Map<String, int[]> INT_RANGES = Map.ofEntries(
            Map.entry("session.timeout_minutes", new int[]{1, 1440}),
            Map.entry("security.max_failed_attempts", new int[]{1, 20}),
            Map.entry("security.lockout_duration_minutes", new int[]{1, 1440}),
            Map.entry("security.password_reset_token_expiry_minutes", new int[]{1, 4320}),
            Map.entry("password.min_length", new int[]{6, 30}),
            Map.entry("password.max_length", new int[]{12, 128}),
            Map.entry("data.max_parents_per_student", new int[]{1, 10}),
            Map.entry("data.max_attachments_per_message", new int[]{1, 20}),
            Map.entry("data.max_attachment_size_mb", new int[]{1, 50}),
            Map.entry("data.message_max_length", new int[]{100, 10000}),
            Map.entry("data.invitation_expiry_hours", new int[]{1, 168})
    );

    private static final List<String> BOOLEAN_KEYS = List.of(
            "password.require_uppercase",
            "password.require_lowercase",
            "password.require_number",
            "password.require_special"
    );

    @Inject
    ConfigService configService;

    @Inject
    PlatformConfigRepository configRepository;

    @Inject
    RequestContext requestContext;

    @Inject
    AuditService auditService;

    @GET
    public Response getAll() {
        if (!requestContext.isSuperAdmin()) {
            throw new ForbiddenException();
        }
        return Response.ok(ApiResponse.success(configService.getAllConfig())).build();
    }

    @PUT
    @Transactional
    public Response update(Map<String, String> updates) {
        if (!requestContext.isSuperAdmin()) {
            throw new ForbiddenException();
        }

        List<ErrorDetail> errors = new ArrayList<>();

        // Validate all values first
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (INT_RANGES.containsKey(key)) {
                try {
                    int intVal = Integer.parseInt(value);
                    int[] range = INT_RANGES.get(key);
                    if (intVal < range[0] || intVal > range[1]) {
                        errors.add(new ErrorDetail(key, "OUT_OF_RANGE",
                                key + " must be between " + range[0] + " and " + range[1]));
                    }
                } catch (NumberFormatException e) {
                    errors.add(new ErrorDetail(key, "INVALID_TYPE", key + " must be an integer"));
                }
            } else if (BOOLEAN_KEYS.contains(key)) {
                if (!"true".equals(value) && !"false".equals(value)) {
                    errors.add(new ErrorDetail(key, "INVALID_TYPE", key + " must be true or false"));
                }
            }
        }

        // Validate password min <= max
        String minStr = updates.getOrDefault("password.min_length", configService.getValue("password.min_length"));
        String maxStr = updates.getOrDefault("password.max_length", configService.getValue("password.max_length"));
        try {
            int min = Integer.parseInt(minStr);
            int max = Integer.parseInt(maxStr);
            if (min > max) {
                errors.add(new ErrorDetail("password.min_length", "INVALID",
                        "Minimum password length cannot exceed maximum password length"));
            }
        } catch (NumberFormatException ignored) {}

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        // Apply updates
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String key = entry.getKey();
            String newValue = entry.getValue();
            String oldValue = configService.getValue(key);

            PlatformConfig config = configRepository.findByKey(key);
            if (config == null) {
                config = new PlatformConfig();
                config.setKey(key);
            }
            config.setValue(newValue);
            config.setUpdatedAt(LocalDateTime.now());
            config.setUpdatedBy(requestContext.getUserId());
            configRepository.persist(config);

            auditService.log("CONFIG_CHANGED", null, key, requestContext.getUserId(),
                    String.format("{\"key\":\"%s\",\"oldValue\":\"%s\",\"newValue\":\"%s\"}", key, oldValue, newValue));
        }

        return Response.ok(ApiResponse.success(configService.getAllConfig())).build();
    }
}
