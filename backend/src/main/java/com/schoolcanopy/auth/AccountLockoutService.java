package com.schoolcanopy.auth;

import java.time.LocalDateTime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.schoolcanopy.config.ConfigService;
import com.schoolcanopy.user.UserAccount;
import com.schoolcanopy.user.UserAccountRepository;

@ApplicationScoped
public class AccountLockoutService {

    @Inject
    ConfigService configService;

    @Inject
    UserAccountRepository userAccountRepository;

    public boolean isLocked(UserAccount user) {
        if (user.getLockedUntil() == null) {
            return false;
        }
        if (user.getLockedUntil().isAfter(LocalDateTime.now())) {
            return true;
        }
        // Lockout expired — reset
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        userAccountRepository.persist(user);
        return false;
    }

    @Transactional
    public void recordFailedAttempt(UserAccount user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        int maxAttempts = configService.getMaxFailedAttempts();
        if (attempts >= maxAttempts) {
            long lockoutMinutes = configService.getLockoutDurationMinutes();
            user.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutMinutes));
        }

        userAccountRepository.persist(user);
    }

    @Transactional
    public void resetAttempts(UserAccount user) {
        if (user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userAccountRepository.persist(user);
        }
    }
}
