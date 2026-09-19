package com.devpilot.testsupport;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** ES256 테스트 토큰 발급 (docs/09 §4.1). 키와 issuer를 받아 서명한다. */
public final class TestJwtFactory {

    private TestJwtFactory() {}

    public static String sign(ECKey key, JWTClaimsSet claims) {
        try {
            SignedJWT jwt =
                    new SignedJWT(
                            new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build(),
                            claims);
            jwt.sign(new ECDSASigner(key));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("cannot sign test token", exception);
        }
    }

    /** 기본 claim: iss, aud=authenticated, sub, email, iat, exp=iat+3600. */
    public static JWTClaimsSet claims(
            String issuer,
            UUID subject,
            String email,
            Instant issuedAt,
            Consumer<JWTClaimsSet.Builder> customizer) {
        JWTClaimsSet.Builder builder =
                new JWTClaimsSet.Builder()
                        .issuer(issuer)
                        .audience(List.of("authenticated"))
                        .subject(subject.toString())
                        .claim("email", email)
                        .claim("iat", issuedAt.getEpochSecond())
                        .claim("exp", issuedAt.plusSeconds(3600).getEpochSecond());
        customizer.accept(builder);
        return builder.build();
    }
}
