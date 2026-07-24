package com.schoolcanopy.auth;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.schoolcanopy.common.exceptions.UnauthorizedException;
import com.schoolcanopy.common.exceptions.ValidationException;
import com.schoolcanopy.config.ConfigService;
import com.schoolcanopy.school.School;
import com.schoolcanopy.school.SchoolRepository;
import com.schoolcanopy.user.UserAccount;
import com.schoolcanopy.user.UserAccountRepository;

@ApplicationScoped
public class SessionService {

    private static final Set<String> PLATFORM_PORTAL_ROLES = Set.of("SUPER_ADMIN", "PLATFORM_TEAM_MEMBER");
    private static final Set<String> SCHOOL_PORTAL_ROLES = Set.of("SCHOOL_ADMINISTRATOR", "OFFICE_STAFF", "TEACHER");
    private static final Set<String> PARENT_PORTAL_ROLES = Set.of("PARENT");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Inject
    UserAccountRepository userAccountRepository;

    @Inject
    SessionRepository sessionRepository;

    @Inject
    PasswordService passwordService;

    @Inject
    SchoolRepository schoolRepository;

    @Inject
    AccountLockoutService accountLockoutService;

    @Inject
    ConfigService configService;

    @Transactional
    public LoginResult login(LoginRequest request) {
        // Find user by email
        UserAccount user = userAccountRepository.findByEmail(request.getEmail());
        if (user == null) {
            throw new UnauthorizedException("Invalid credentials");
        }

        // Check if account is locked
        if (accountLockoutService.isLocked(user)) {
            throw new ValidationException("account", "LOCKED", "Account is temporarily locked");
        }

        // Check if school is deactivated (for school-level users)
        if (user.getSchoolId() != null && user.getSchool() != null
                && "DEACTIVATED".equals(user.getSchool().getStatus())) {
            throw new ValidationException("school", "DEACTIVATED", "School is deactivated");
        }

        // Validate portal-role match
        validatePortalRoleMatch(request.getPortal(), user.getRole());

        // Verify password
        if (!passwordService.matches(request.getPassword(), user.getPasswordHash())) {
            accountLockoutService.recordFailedAttempt(user);
            throw new UnauthorizedException("Invalid credentials");
        }

        // Check if user is active
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        // Reset failed attempts on success
        accountLockoutService.resetAttempts(user);

        // Create session
        String token = generateToken();
        long timeoutMinutes = configService.getSessionTimeoutMinutes();

        Session session = new Session();
        session.setToken(token);
        session.setUserId(user.getId());
        session.setCreatedAt(LocalDateTime.now());
        session.setLastActivityAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusMinutes(timeoutMinutes));
        sessionRepository.persist(session);

        // Update last login
        user.setLastLoginAt(LocalDateTime.now());
        userAccountRepository.persist(user);

        LoginResult result = new LoginResult(token, timeoutMinutes * 60, user.getRole(), user.getName(), user.getEmail());

        // Add school branding if user belongs to a school
        if (user.getSchoolId() != null) {
            School school = schoolRepository.findById(user.getSchoolId());
            if (school != null) {
                result.setBrandColor(school.getBrandColor());
                result.setLogoUrl(school.getLogoUrl());
                result.setSchoolName(school.getName());
            }
        }

        return result;
    }

    @Transactional
    public void logout(String token) {
        sessionRepository.deleteByToken(token);
    }

    @Transactional
    public void revokeSessionsForUser(java.util.UUID userId) {
        sessionRepository.deleteByUserId(userId);
    }

    @Transactional
    public void revokeSessionsForSchool(java.util.UUID schoolId) {
        sessionRepository.deleteBySchoolId(schoolId);
    }

    public Session validateSession(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Session session = sessionRepository.findByToken(token);
        if (session == null) {
            return null;
        }
        // Check expiry
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            sessionRepository.deleteByToken(token);
            return null;
        }
        // Check inactivity timeout
        long timeoutMinutes = configService.getSessionTimeoutMinutes();
        if (session.getLastActivityAt().plusMinutes(timeoutMinutes).isBefore(LocalDateTime.now())) {
            sessionRepository.deleteByToken(token);
            return null;
        }
        // Update last activity
        session.setLastActivityAt(LocalDateTime.now());
        sessionRepository.persist(session);
        return session;
    }

    private void validatePortalRoleMatch(String portal, String role) {
        if ("platform".equalsIgnoreCase(portal)) {
            if (!PLATFORM_PORTAL_ROLES.contains(role)) {
                throw new ValidationException("portal", "ROLE_MISMATCH",
                        "This portal is not available for your account");
            }
        } else if ("school".equalsIgnoreCase(portal)) {
            if (!SCHOOL_PORTAL_ROLES.contains(role)) {
                throw new ValidationException("portal", "ROLE_MISMATCH",
                        "This portal is not available for your account");
            }
        } else if ("parent".equalsIgnoreCase(portal)) {
            if (!PARENT_PORTAL_ROLES.contains(role)) {
                throw new ValidationException("portal", "ROLE_MISMATCH",
                        "This portal is not available for your account");
            }
        } else {
            throw new ValidationException("portal", "INVALID", "Invalid portal identifier");
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
