package com.schoolcanopy.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

import com.schoolcanopy.common.ApiResponse;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SessionResource {

    @Inject
    SessionService sessionService;

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        LoginResult result = sessionService.login(request);
        String cookieName = getCookieName(request.getPortal());
        NewCookie sessionCookie = new NewCookie.Builder(cookieName)
                .value(result.getToken())
                .path("/")
                .httpOnly(true)
                .secure(false)
                .maxAge((int) result.getTimeoutSeconds())
                .build();
        return Response.ok(ApiResponse.success(result.toDto()))
                .cookie(sessionCookie)
                .build();
    }

    @POST
    @Path("/logout")
    public Response logout(
            @CookieParam("session-token") String token,
            @CookieParam("platform-session") String platformToken,
            @CookieParam("school-session") String schoolToken,
            @CookieParam("parent-session") String parentToken) {

        // Find whichever token is present and invalidate it
        String activeToken = token != null ? token :
                platformToken != null ? platformToken :
                schoolToken != null ? schoolToken : parentToken;

        if (activeToken != null) {
            sessionService.logout(activeToken);
        }

        // Expire all possible cookie names
        NewCookie c1 = new NewCookie.Builder("session-token").value("").path("/").httpOnly(true).maxAge(0).build();
        NewCookie c2 = new NewCookie.Builder("platform-session").value("").path("/").httpOnly(true).maxAge(0).build();
        NewCookie c3 = new NewCookie.Builder("school-session").value("").path("/").httpOnly(true).maxAge(0).build();
        NewCookie c4 = new NewCookie.Builder("parent-session").value("").path("/").httpOnly(true).maxAge(0).build();

        return Response.ok(ApiResponse.success(null))
                .cookie(c1, c2, c3, c4)
                .build();
    }

    private String getCookieName(String portal) {
        if ("platform".equalsIgnoreCase(portal)) return "platform-session";
        if ("school".equalsIgnoreCase(portal)) return "school-session";
        if ("parent".equalsIgnoreCase(portal)) return "parent-session";
        return "session-token"; // fallback
    }
}
