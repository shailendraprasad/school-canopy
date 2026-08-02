package com.schoolcanopy.auth;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.schoolcanopy.common.ApiResponse;
import com.schoolcanopy.common.exceptions.ValidationException;
import com.schoolcanopy.config.ConfigService;
import com.schoolcanopy.notification.EmailService;
import com.schoolcanopy.user.UserAccount;
import com.schoolcanopy.user.UserAccountRepository;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/api/invitations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InvitationResource {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Inject EntityManager em;
    @Inject PasswordService passwordService;
    @Inject UserAccountRepository userAccountRepository;
    @Inject ConfigService configService;
    @Inject EmailService emailService;

    @ConfigProperty(name = "schoolcanopy.parent-portal.url", defaultValue = "http://localhost:3002")
    String parentPortalUrl;

    @ConfigProperty(name = "schoolcanopy.school-portal.url", defaultValue = "http://localhost:3001")
    String schoolPortalUrl;

    @ConfigProperty(name = "schoolcanopy.platform-admin.url", defaultValue = "http://localhost:3000")
    String platformAdminUrl;

    /**
     * Account setup endpoint — public, called when user clicks invitation link.
     */
    @POST
    @Path("/{token}/setup")
    @Transactional
    public Response setup(@PathParam("token") String token, Map<String, String> body) {
        String password = body.get("password");

        // Find invitation
        @SuppressWarnings("unchecked")
        var results = em.createNativeQuery(
                "SELECT id, target_user_id, email, status, expires_at FROM invitation WHERE token = :token")
                .setParameter("token", token)
                .getResultList();

        if (results.isEmpty()) {
            throw new ValidationException("token", "INVALID", "Invalid invitation link");
        }

        Object[] row = (Object[]) results.get(0);
        String status = (String) row[3];
        LocalDateTime expiresAt = ((java.sql.Timestamp) row[4]).toLocalDateTime();

        if ("USED".equals(status)) {
            throw new ValidationException("token", "USED", "This invitation has already been used");
        }

        if (expiresAt.isBefore(LocalDateTime.now())) {
            throw new ValidationException("token", "EXPIRED", "This invitation has expired");
        }

        // Validate password
        passwordService.validatePassword(password);

        // Activate account
        UUID targetUserId = (UUID) row[1];
        String passwordHash = passwordService.hash(password);

        em.createNativeQuery("UPDATE user_account SET password_hash = :hash, status = 'ACTIVE', updated_at = NOW() WHERE id = :id")
                .setParameter("hash", passwordHash)
                .setParameter("id", targetUserId)
                .executeUpdate();

        // Mark invitation as used
        em.createNativeQuery("UPDATE invitation SET status = 'USED', used_at = NOW() WHERE token = :token")
                .setParameter("token", token)
                .executeUpdate();

        return Response.ok(ApiResponse.success(Map.of("message", "Account activated successfully"))).build();
    }

    /**
     * Utility: create an invitation for a user (called internally by other services).
     */
    public String createInvitation(UUID targetUserId, String email) {
        String token = generateToken();
        int expiryHours = configService.getInvitationExpiryHours();

        em.createNativeQuery(
                "INSERT INTO invitation (id, token, target_user_id, email, status, expires_at, created_at) " +
                "VALUES (gen_random_uuid(), :token, :userId, :email, 'PENDING', :expires, NOW())")
                .setParameter("token", token)
                .setParameter("userId", targetUserId)
                .setParameter("email", email)
                .setParameter("expires", LocalDateTime.now().plusHours(expiryHours))
                .executeUpdate();

        UserAccount user = userAccountRepository.findById(targetUserId);
        if (user != null) {
            String setupUrl = EmailService.resolveSetupUrl(
                    user.getRole(), token, parentPortalUrl, schoolPortalUrl, platformAdminUrl);
            emailService.sendInvitation(email, user.getName(), setupUrl);
        }

        return token;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
