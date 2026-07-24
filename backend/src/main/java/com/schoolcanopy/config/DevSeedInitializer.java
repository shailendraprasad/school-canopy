package com.schoolcanopy.config;

import at.favre.lib.crypto.bcrypt.BCrypt;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

/**
 * Seeds the Super Admin account on startup.
 * Only creates the platform admin — all other accounts are created via the UI.
 */
@ApplicationScoped
public class DevSeedInitializer {

    private static final Logger LOG = Logger.getLogger(DevSeedInitializer.class);
    private static final String SUPER_ADMIN_EMAIL = "schoolcanopyadmin@gmail.com";
    private static final String SUPER_ADMIN_PASSWORD = "Admin@123456";

    @Inject
    EntityManager em;

    @Transactional
    void onStart(@Observes StartupEvent event) {
        String correctHash = BCrypt.withDefaults().hashToString(12, SUPER_ADMIN_PASSWORD.toCharArray());

        // Check if Super Admin exists
        Long count = (Long) em.createNativeQuery("SELECT COUNT(*) FROM user_account WHERE email = :email")
                .setParameter("email", SUPER_ADMIN_EMAIL)
                .getSingleResult();

        if (count == 0) {
            // Create Super Admin
            em.createNativeQuery(
                    "INSERT INTO user_account (id, email, password_hash, name, role, status, failed_login_attempts, created_at) " +
                    "VALUES (gen_random_uuid(), :email, :hash, 'Super Admin', 'SUPER_ADMIN', 'ACTIVE', 0, NOW())")
                    .setParameter("email", SUPER_ADMIN_EMAIL)
                    .setParameter("hash", correctHash)
                    .executeUpdate();
            LOG.infof("Seed: Created Super Admin (%s)", SUPER_ADMIN_EMAIL);
        } else {
            // Update password hash to ensure it's always correct
            em.createNativeQuery("UPDATE user_account SET password_hash = :hash, failed_login_attempts = 0, locked_until = NULL WHERE email = :email")
                    .setParameter("hash", correctHash)
                    .setParameter("email", SUPER_ADMIN_EMAIL)
                    .executeUpdate();
            LOG.infof("Seed: Updated Super Admin password hash for %s", SUPER_ADMIN_EMAIL);
        }
    }
}
