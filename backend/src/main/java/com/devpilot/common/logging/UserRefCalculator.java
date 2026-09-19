package com.devpilot.common.logging;

import com.devpilot.common.config.DevPilotProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 로그용 사용자 식별자 {@code userRef = hex(HMAC-SHA256(DEVPILOT_LOG_HASH_KEY, userId))[0..12]} (docs/07
 * §6.2).
 *
 * <p>키는 64자 hex(32바이트)다. 값이 있는데 형식이 틀리면 모든 profile에서 기동 실패, {@code prod}에서 비어 있어도 기동 실패다. {@code
 * prod} 밖에서 비어 있으면 기동 시 임시 키를 만든다(재기동하면 로그 연결이 끊긴다).
 */
@Component
public class UserRefCalculator {

    private static final Logger log = LoggerFactory.getLogger(UserRefCalculator.class);
    private static final Pattern HEX_KEY = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final int REF_HEX_LENGTH = 12;
    private static final int KEY_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Profiles PROD_PROFILE = Profiles.of("prod");

    private final byte[] keyBytes;

    public UserRefCalculator(DevPilotProperties properties, Environment environment) {
        boolean prod = environment.acceptsProfiles(PROD_PROFILE);
        String configuredKey = properties.security().logHashKey();
        this.keyBytes = resolveKey(configuredKey, prod);
    }

    /** 내부 사용자 id의 {@code userRef}. */
    public String userRef(UUID userId) {
        return ref(userId.toString());
    }

    /** allowlist 거부 로그의 {@code subjectRef} (JWT {@code sub}, docs/07 §6.2). */
    public String subjectRef(String subject) {
        return ref(subject);
    }

    private String ref(String message) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(keyBytes, HMAC_SHA256));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, REF_HEX_LENGTH);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }

    static byte[] resolveKey(@Nullable String configured, boolean prod) {
        if (configured == null || configured.isBlank()) {
            if (prod) {
                throw new IllegalStateException(
                        "DEVPILOT_LOG_HASH_KEY is required in the prod profile (docs/07 §6.2)");
            }
            log.warn(
                    "DEVPILOT_LOG_HASH_KEY is empty: generated a temporary userRef key, refs"
                            + " change after restart");
            byte[] generated = new byte[KEY_BYTES];
            RANDOM.nextBytes(generated);
            return generated;
        }
        String trimmed = configured.trim();
        if (!HEX_KEY.matcher(trimmed).matches()) {
            throw new IllegalStateException(
                    "DEVPILOT_LOG_HASH_KEY must be 64 hex characters (docs/07 §6.2)");
        }
        return HexFormat.of().parseHex(trimmed);
    }
}
