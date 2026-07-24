package com.schoolcanopy.auth;

import java.util.Set;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import com.schoolcanopy.common.ApiResponse;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class SessionFilter implements ContainerRequestFilter {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/invitations"
    );

    @Inject
    SessionService sessionService;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();

        // Skip authentication for public endpoints
        if (isPublicPath(path)) {
            return;
        }

        // Skip for health and readiness checks
        if (path.startsWith("/q/")) {
            return;
        }

        // Determine which cookie to use based on X-Portal header or try all
        String token = null;
        String portalHeader = requestContext.getHeaderString("X-Portal");

        if ("platform".equals(portalHeader)) {
            Cookie c = requestContext.getCookies().get("platform-session");
            if (c != null && !c.getValue().isBlank()) token = c.getValue();
        } else if ("school".equals(portalHeader)) {
            Cookie c = requestContext.getCookies().get("school-session");
            if (c != null && !c.getValue().isBlank()) token = c.getValue();
        } else if ("parent".equals(portalHeader)) {
            Cookie c = requestContext.getCookies().get("parent-session");
            if (c != null && !c.getValue().isBlank()) token = c.getValue();
        }

        // Fallback: try all cookies
        if (token == null) {
            for (String name : new String[]{"platform-session", "school-session", "parent-session", "session-token"}) {
                Cookie c = requestContext.getCookies().get(name);
                if (c != null && !c.getValue().isBlank()) { token = c.getValue(); break; }
            }
        }

        // Fall back to Authorization: Bearer <token>
        if (token == null) {
            String authHeader = requestContext.getHeaderString("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7).trim();
            }
        }

        if (token == null || token.isBlank()) {
            abort(requestContext);
            return;
        }

        Session session = sessionService.validateSession(token);
        if (session == null) {
            abort(requestContext);
            return;
        }

        // Store session info for downstream use
        requestContext.setProperty("session", session);
        requestContext.setProperty("userId", session.getUserId());
    }

    private boolean isPublicPath(String path) {
        for (String publicPath : PUBLIC_PATHS) {
            if (path.startsWith(publicPath)) {
                return true;
            }
        }
        return false;
    }

    private void abort(ContainerRequestContext ctx) {
        ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity(ApiResponse.error("Authentication required"))
                .build());
    }
}
