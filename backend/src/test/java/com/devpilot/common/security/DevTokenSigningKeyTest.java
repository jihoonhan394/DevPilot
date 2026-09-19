package com.devpilot.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devpilot.testsupport.TestEcKeys;
import com.devpilot.testsupport.UnitTest;
import com.nimbusds.jose.JOSEException;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@UnitTest
class DevTokenSigningKeyTest {

    @Test
    void shouldExposePublicJwksWithoutPrivatePartWhenGenerated() {
        DevTokenSigningKey key = DevTokenSigningKey.generate();

        Map<String, Object> jwks = key.publicJwks();

        @SuppressWarnings("unchecked")
        Map<String, Object> jwk = ((List<Map<String, Object>>) jwks.get("keys")).get(0);
        assertThat(jwk)
                .containsEntry("kty", "EC")
                .containsEntry("crv", "P-256")
                .containsEntry("alg", "ES256")
                .containsEntry("use", "sig")
                .containsEntry("kid", key.keyId())
                .doesNotContainKey("d");
    }

    @Test
    void shouldReadPublicKeyFromPemWhenSec1ContainsIt() throws JOSEException {
        KeyPair pair = TestEcKeys.generate();

        DevTokenSigningKey key = DevTokenSigningKey.fromPem(TestEcKeys.pkcs8Pem(pair, true));

        ECPublicKey expected = (ECPublicKey) pair.getPublic();
        assertThat(key.publicJwk().toECPublicKey().getW()).isEqualTo(expected.getW());
    }

    @Test
    void shouldAcceptEscapedNewlinesWhenPemIsOnOneLine() {
        String oneLine = TestEcKeys.pkcs8Pem(TestEcKeys.generate(), true).replace("\n", "\n");

        DevTokenSigningKey key = DevTokenSigningKey.fromPem(oneLine);

        assertThat(key.keyId()).isNotBlank();
    }

    @Test
    void shouldFailWhenPemHasNoEmbeddedPublicKey() {
        String pem = TestEcKeys.pkcs8Pem(TestEcKeys.generate(), false);

        assertThatThrownBy(() -> DevTokenSigningKey.fromPem(pem))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no embedded public key");
    }

    @Test
    void shouldFailWhenPemIsNotBase64() {
        assertThatThrownBy(() -> DevTokenSigningKey.fromPem("not a key!"))
                .isInstanceOf(IllegalStateException.class);
    }
}
