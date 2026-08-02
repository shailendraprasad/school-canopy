package com.schoolcanopy.notification;

import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;

@ApplicationScoped
public class EmailService {

    private static final Logger LOG = Logger.getLogger(EmailService.class);

    @ConfigProperty(name = "schoolcanopy.email.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "schoolcanopy.email.resend.api-key")
    Optional<String> apiKey;

    @ConfigProperty(name = "schoolcanopy.email.from", defaultValue = "School Canopy <onboarding@resend.dev>")
    String fromAddress;

    public void sendInvitation(String toEmail, String recipientName, String setupUrl) {
        if (!isConfigured()) {
            LOG.infof("Email not configured; invitation link for %s: %s", toEmail, setupUrl);
            return;
        }

        String name = recipientName != null && !recipientName.isBlank() ? recipientName : toEmail;
        String html = """
                <div style="font-family: Arial, sans-serif; max-width: 560px; margin: 0 auto; color: #1a1a2e;">
                  <h2 style="color: #4a6b8a;">Welcome to School Canopy</h2>
                  <p>Hi %s,</p>
                  <p>You have been invited to join School Canopy. Click the button below to set your password and activate your account.</p>
                  <p style="margin: 28px 0;">
                    <a href="%s" style="background: #4a6b8a; color: #fff; padding: 12px 24px; border-radius: 8px; text-decoration: none; font-weight: 600;">Activate my account</a>
                  </p>
                  <p style="font-size: 13px; color: #666;">Or copy this link into your browser:<br><a href="%s">%s</a></p>
                  <p style="font-size: 12px; color: #999; margin-top: 32px;">If you did not expect this email, you can ignore it.</p>
                </div>
                """.formatted(name, setupUrl, setupUrl, setupUrl);

        try {
            Resend resend = new Resend(apiKey.orElseThrow());
            CreateEmailOptions options = CreateEmailOptions.builder()
                    .from(fromAddress)
                    .to(toEmail)
                    .subject("You're invited to School Canopy")
                    .html(html)
                    .build();
            CreateEmailResponse response = resend.emails().send(options);
            LOG.infof("Invitation email sent to %s (id=%s)", toEmail, response.getId());
        } catch (ResendException e) {
            LOG.warnf(e, "Failed to send invitation email to %s; manual link: %s", toEmail, setupUrl);
        }
    }

    public boolean isConfigured() {
        return enabled && apiKey.filter(k -> !k.isBlank()).isPresent();
    }

    public static String resolveSetupUrl(String role, String token,
                                         String parentPortalUrl,
                                         String schoolPortalUrl,
                                         String platformAdminUrl) {
        String base = schoolPortalUrl;
        if ("PARENT".equals(role)) {
            base = parentPortalUrl;
        } else if (Set.of("SUPER_ADMIN", "PLATFORM_TEAM_MEMBER").contains(role)) {
            base = platformAdminUrl;
        }
        return trimTrailingSlash(base) + "/setup/" + token;
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
