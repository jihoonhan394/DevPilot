package com.devpilot.user.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.ForbiddenException;
import com.devpilot.common.logging.AuditEvent;
import com.devpilot.common.logging.AuditLogger;
import com.devpilot.common.security.AllowedUserPolicy;
import com.devpilot.common.security.DevTokenConfig;
import com.devpilot.common.security.DevTokenSigningKey;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * devtoken 발급 (docs/03 §4.2, docs/05 §1.4.5). allowlist 이메일에만 EC P-256 서명 JWT를 준다. 사용자 행은 만들지 않는다 —
 * 첫 인증 요청의 JIT 프로비저닝이 만든다(S1).
 */
@Service
@ConditionalOnProperty(
        prefix = "devpilot.security",
        name = "auth-mode",
        havingValue = "devtoken",
        matchIfMissing = true)
public class DevTokenService {

    /** RFC 4122 DNS namespace (UUID v5 sub). */
    private static final UUID DNS_NAMESPACE =
            UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

    private static final int UUID_BYTES = 16;
    private static final int VERSION_INDEX = 6;
    private static final int VARIANT_INDEX = 8;

    private final JwtEncoder jwtEncoder;
    private final DevTokenSigningKey signingKey;
    private final AllowedUserPolicy allowedUserPolicy;
    private final AuditLogger auditLogger;
    private final Clock clock;
    private final String issuer;
    private final Duration tokenTtl;

    public DevTokenService(
            JwtEncoder jwtEncoder,
            DevTokenSigningKey signingKey,
            AllowedUserPolicy allowedUserPolicy,
            AuditLogger auditLogger,
            Clock clock,
            DevPilotProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.signingKey = signingKey;
        this.allowedUserPolicy = allowedUserPolicy;
        this.auditLogger = auditLogger;
        this.clock = clock;
        this.issuer = properties.security().devtoken().issuer();
        this.tokenTtl = properties.security().devtoken().tokenTtl();
    }

    public IssuedDevToken issue(String email) {
        String normalizedEmail =
                Objects.requireNonNull(AllowedUserPolicy.normalizeEmail(email), "email");
        UUID subject = subjectFor(normalizedEmail);
        if (!allowedUserPolicy.isAllowed(normalizedEmail, subject.toString())) {
            auditLogger.log(
                    AuditEvent.AUTH_DEVTOKEN_REJECTED,
                    Map.of("emailRef", AuditLogger.emailRef(normalizedEmail)));
            throw new ForbiddenException(
                    ErrorCode.USER_NOT_ALLOWED, "email is not in the allowlist");
        }
        Instant issuedAt = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(tokenTtl);
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(issuer)
                        .audience(List.of(DevTokenConfig.AUDIENCE))
                        .subject(subject.toString())
                        .claim("email", normalizedEmail)
                        .claim(
                                "amr",
                                List.of(
                                        Map.of(
                                                "method",
                                                "devtoken",
                                                "timestamp",
                                                issuedAt.getEpochSecond())))
                        .issuedAt(issuedAt)
                        .expiresAt(expiresAt)
                        .build();
        JwsHeader header =
                JwsHeader.with(SignatureAlgorithm.ES256).keyId(signingKey.keyId()).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        auditLogger.log(
                AuditEvent.AUTH_DEVTOKEN_ISSUED,
                Map.of("emailRef", AuditLogger.emailRef(normalizedEmail)));
        return new IssuedDevToken(token, expiresAt);
    }

    public Map<String, Object> publicJwks() {
        return signingKey.publicJwks();
    }

    /** {@code sub = UUID v5(namespace DNS, email)} — 같은 이메일은 항상 같은 sub다 (docs/05 §1.4.5). */
    static UUID subjectFor(String normalizedEmail) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            ByteBuffer namespace = ByteBuffer.allocate(UUID_BYTES);
            namespace.putLong(DNS_NAMESPACE.getMostSignificantBits());
            namespace.putLong(DNS_NAMESPACE.getLeastSignificantBits());
            sha1.update(namespace.array());
            sha1.update(normalizedEmail.getBytes(StandardCharsets.UTF_8));
            byte[] hash = sha1.digest();
            hash[VERSION_INDEX] = (byte) ((hash[VERSION_INDEX] & 0x0f) | 0x50);
            hash[VARIANT_INDEX] = (byte) ((hash[VARIANT_INDEX] & 0x3f) | 0x80);
            ByteBuffer buffer = ByteBuffer.wrap(hash, 0, UUID_BYTES);
            return new UUID(buffer.getLong(), buffer.getLong());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is not available", exception);
        }
    }

    /** 발급 결과. */
    public record IssuedDevToken(String accessToken, Instant expiresAt) {}
}
