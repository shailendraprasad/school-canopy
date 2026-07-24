package com.schoolcanopy.auth;

import java.util.ArrayList;
import java.util.List;

import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.schoolcanopy.common.ErrorDetail;
import com.schoolcanopy.common.exceptions.ValidationException;
import com.schoolcanopy.config.ConfigService;

@ApplicationScoped
public class PasswordService {

    @Inject
    ConfigService configService;

    public String hash(String plaintext) {
        return BCrypt.withDefaults().hashToString(12, plaintext.toCharArray());
    }

    public boolean matches(String plaintext, String hash) {
        if (plaintext == null || hash == null) {
            return false;
        }
        BCrypt.Result result = BCrypt.verifyer().verify(plaintext.toCharArray(), hash);
        return result.verified;
    }

    public void validatePassword(String password) {
        List<ErrorDetail> errors = new ArrayList<>();

        int minLength = configService.getPasswordMinLength();
        int maxLength = configService.getPasswordMaxLength();

        if (password == null || password.isEmpty()) {
            errors.add(new ErrorDetail("password", "REQUIRED", "Password is required"));
            throw new ValidationException(errors);
        }

        if (password.length() < minLength) {
            errors.add(new ErrorDetail("password", "TOO_SHORT",
                    "Password must be at least " + minLength + " characters"));
        }

        if (password.length() > maxLength) {
            errors.add(new ErrorDetail("password", "TOO_LONG",
                    "Password must be at most " + maxLength + " characters"));
        }

        if (configService.isPasswordRequireUppercase() && !password.chars().anyMatch(Character::isUpperCase)) {
            errors.add(new ErrorDetail("password", "MISSING_UPPERCASE",
                    "Password must contain at least one uppercase letter"));
        }

        if (configService.isPasswordRequireLowercase() && !password.chars().anyMatch(Character::isLowerCase)) {
            errors.add(new ErrorDetail("password", "MISSING_LOWERCASE",
                    "Password must contain at least one lowercase letter"));
        }

        if (configService.isPasswordRequireNumber() && !password.chars().anyMatch(Character::isDigit)) {
            errors.add(new ErrorDetail("password", "MISSING_NUMBER",
                    "Password must contain at least one number"));
        }

        if (configService.isPasswordRequireSpecial()
                && password.chars().allMatch(c -> Character.isLetterOrDigit(c))) {
            errors.add(new ErrorDetail("password", "MISSING_SPECIAL",
                    "Password must contain at least one special character"));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }
}
