package com.schoolcanopy.rbac;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;

import com.schoolcanopy.auth.Session;
import com.schoolcanopy.user.UserAccount;
import com.schoolcanopy.user.UserAccountRepository;

@Provider
@Priority(Priorities.AUTHORIZATION)
public class TenantContextFilter implements ContainerRequestFilter {

    @Inject
    EntityManager entityManager;

    @Inject
    UserAccountRepository userAccountRepository;

    @Inject
    RequestContext requestContext;

    @Override
    public void filter(ContainerRequestContext ctx) {
        Session session = (Session) ctx.getProperty("session");
        if (session == null) {
            return;
        }

        UUID userId = session.getUserId();

        // Bypass RLS for user lookup by temporarily setting SUPER_ADMIN role
        entityManager.createNativeQuery("SELECT set_config('app.current_role', 'SUPER_ADMIN', true)")
                .getSingleResult();

        UserAccount user = userAccountRepository.findById(userId);
        if (user == null) {
            return;
        }

        // Populate request context
        requestContext.setUserId(user.getId());
        requestContext.setRole(user.getRole());
        requestContext.setSchoolId(user.getSchoolId());
        requestContext.setEmail(user.getEmail());
        requestContext.setName(user.getName());

        // Now set the real PostgreSQL session variables for RLS
        String role = user.getRole();
        entityManager.createNativeQuery("SELECT set_config('app.current_role', :role, true)")
                .setParameter("role", role)
                .getSingleResult();

        if (user.getSchoolId() != null) {
            entityManager.createNativeQuery("SELECT set_config('app.current_school_id', :schoolId, true)")
                    .setParameter("schoolId", user.getSchoolId().toString())
                    .getSingleResult();
        }
    }
}
