package com.devpilot.common.security;

import com.devpilot.common.config.DevPilotProperties;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 초대 사용자 allowlist (docs/03 §4.3, DEC-01). 이메일은 소문자·trim으로 비교하고, 이메일이 없어도 {@code sub}이 {@code
 * allowed-subjects}에 있으면 허용한다.
 */
@Component
public class AllowedUserPolicy {

    private final List<String> allowedEmails;
    private final List<String> allowedSubjects;

    public AllowedUserPolicy(DevPilotProperties properties) {
        this.allowedEmails = properties.security().allowedEmails();
        this.allowedSubjects = properties.security().allowedSubjects();
    }

    public boolean isAllowed(@Nullable String email, @Nullable String subject) {
        String normalizedEmail = normalizeEmail(email);
        return (normalizedEmail != null && allowedEmails.contains(normalizedEmail))
                || (subject != null && allowedSubjects.contains(subject));
    }

    public static @Nullable String normalizeEmail(@Nullable String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }
}
