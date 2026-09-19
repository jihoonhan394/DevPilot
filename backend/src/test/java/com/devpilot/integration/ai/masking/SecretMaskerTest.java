package com.devpilot.integration.ai.masking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devpilot.common.error.DevPilotException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.logging.AuditLogger;
import com.devpilot.common.logging.UserRefCalculator;
import com.devpilot.testsupport.AuditLogCapture;
import com.devpilot.testsupport.TestProperties;
import com.devpilot.testsupport.UnitTest;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.env.MockEnvironment;

/** docs/17 §7.3 V1~V23 (AC-14 S1). 가짜 secret은 소스에 토큰 형태 리터럴로 쓰지 않고 런타임에 조합한다(docs/07 §11.3). */
@UnitTest
class SecretMaskerTest {

    private static final String BEGIN = "-----BEGIN ";

    private final SecretMasker masker =
            new SecretMasker(
                    new AuditLogger(),
                    new UserRefCalculator(TestProperties.testProfile(), new MockEnvironment()));

    static Stream<Arguments> vectors() {
        String aws = "AKIA" + "IOSFODNN7EXAMPLE";
        return Stream.of(
                Arguments.of("V1", BEGIN + "RSA PRIVATE KEY-----\nMIIEow", null, 0, true),
                Arguments.of(
                        "V2",
                        "aws.accessKeyId=" + aws,
                        "aws.accessKeyId=[REDACTED:AWS_ACCESS_KEY]",
                        1,
                        false),
                Arguments.of(
                        "V3",
                        "GITHUB_TOKEN=" + "ghp_" + "a1B2".repeat(9),
                        "GITHUB_TOKEN=[REDACTED:GITHUB_TOKEN]",
                        1,
                        false),
                Arguments.of(
                        "V4",
                        "token: " + "github_pat_" + "A".repeat(30),
                        "token: [REDACTED:GITHUB_TOKEN]",
                        1,
                        false),
                Arguments.of(
                        "V5",
                        "DEEPSEEK_API_KEY=" + "sk-" + "0123456789abcdef".repeat(2),
                        "DEEPSEEK_API_KEY=[REDACTED:DEEPSEEK_API_KEY]",
                        1,
                        false),
                Arguments.of(
                        "V6",
                        "key: " + "sb_secret_" + "q".repeat(32),
                        "key: [REDACTED:SUPABASE_SECRET_KEY]",
                        1,
                        false),
                Arguments.of(
                        "V7",
                        "Authorization: Bearer "
                                + "eyJhbGciOiJIUzI1NiJ9"
                                + "."
                                + "eyJzdWIiOiIxMjM0NTY3ODkwIn0"
                                + "."
                                + "dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                        "Authorization: Bearer [REDACTED:JWT]",
                        1,
                        false),
                Arguments.of(
                        "V8",
                        "slack=" + "xox" + "b-" + "1234567890-abcdefghij",
                        "slack=[REDACTED:SLACK_TOKEN]",
                        1,
                        false),
                Arguments.of(
                        "V9",
                        "const k = \"" + "AIza" + "S".repeat(35) + "\";",
                        "const k = \"[REDACTED:GOOGLE_API_KEY]\";",
                        1,
                        false),
                Arguments.of(
                        "V10",
                        "jdbc:postgresql://db.example.com:5432/app?user=app&password="
                                + "S3cretPw99",
                        "jdbc:postgresql://db.example.com:5432/app?user=app&password=[REDACTED:JDBC_PASSWORD]",
                        1,
                        false),
                Arguments.of(
                        "V11",
                        "postgres://app:" + "S3cretPw99" + "@db.example.com:5432/app",
                        "postgres://app:[REDACTED:URL_PASSWORD]@db.example.com:5432/app",
                        1,
                        false),
                Arguments.of(
                        "V12",
                        "spring.datasource.password=" + "Sup3rS3cret!",
                        "spring.datasource.password=[REDACTED:GENERIC_SECRET]",
                        1,
                        false),
                Arguments.of(
                        "V13",
                        "String password = request.getPassword();",
                        "String password = request.getPassword();",
                        0,
                        false),
                Arguments.of(
                        "V14", "this.password = password;", "this.password = password;", 0, false),
                Arguments.of(
                        "V15", "password: ${DB_PASSWORD}", "password: ${DB_PASSWORD}", 0, false),
                Arguments.of(
                        "V16",
                        "private static final String API_KEY = \"" + "k8s-prod-2026-xyz" + "\";",
                        "private static final String API_KEY = \"[REDACTED:GENERIC_SECRET]\";",
                        1,
                        false),
                Arguments.of(
                        "V17",
                        "aws=" + aws + "\nclient_secret: \"" + "q9W8e7R6t5Y4" + "\"",
                        "aws=[REDACTED:AWS_ACCESS_KEY]\n"
                                + "client_secret: \"[REDACTED:GENERIC_SECRET]\"",
                        2,
                        false),
                Arguments.of("V18", "password = \"short\"", "password = \"short\"", 0, false),
                Arguments.of("V19", "\"token\": \"********\"", "\"token\": \"********\"", 0, false),
                Arguments.of("V20", BEGIN + "PUBLIC KEY-----", BEGIN + "PUBLIC KEY-----", 0, false),
                Arguments.of("V21", BEGIN + "PGP PRIVATE KEY BLOCK-----", null, 0, true),
                Arguments.of(
                        "V22",
                        "String token = jwtProvider.create(user);",
                        "String token = jwtProvider.create(user);",
                        0,
                        false),
                Arguments.of(
                        "V23", "secret = props.jwtSecret", "secret = props.jwtSecret", 0, false));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("vectors")
    void shouldMaskAccordingToVectorWhenTextContainsSecret(
            String id, String input, @Nullable String expected, int count, boolean blocked) {
        MaskingResult result = masker.mask(input);

        assertThat(result.maskedText()).as(id).isEqualTo(expected);
        assertThat(result.maskedCount()).as(id).isEqualTo(count);
        assertThat(result.blocked()).as(id).isEqualTo(blocked);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("privateKeyHeaders")
    void shouldBlockEveryPrivateKeyHeaderWhenAcceptanceTableIsApplied(
            String header, boolean blocked) {
        assertThat(masker.mask(header + "\nabc").blocked()).isEqualTo(blocked);
    }

    static Stream<Arguments> privateKeyHeaders() {
        return Stream.of(
                Arguments.of(BEGIN + "PRIVATE KEY-----", true),
                Arguments.of(BEGIN + "RSA PRIVATE KEY-----", true),
                Arguments.of(BEGIN + "OPENSSH PRIVATE KEY-----", true),
                Arguments.of(BEGIN + "ENCRYPTED PRIVATE KEY-----", true),
                Arguments.of(BEGIN + "PGP PRIVATE KEY BLOCK-----", true),
                Arguments.of(BEGIN + "PUBLIC KEY-----", false),
                Arguments.of(BEGIN + "CERTIFICATE-----", false));
    }

    @Test
    void shouldRejectAndAuditWhenPrivateKeyIsSubmitted() {
        try (AuditLogCapture capture = AuditLogCapture.start()) {
            assertThatThrownBy(
                            () ->
                                    masker.maskOrReject(
                                            UUID.randomUUID(),
                                            "RUBBER_DUCK_TURN",
                                            BEGIN + "EC PRIVATE KEY-----\nabc"))
                    .isInstanceOf(DevPilotException.class)
                    .extracting(exception -> ((DevPilotException) exception).errorCode())
                    .isEqualTo(ErrorCode.SECRET_DETECTED_BLOCKED);

            assertThat(capture.events()).containsExactly("SECRET_BLOCKED");
            assertThat(capture.fields(0))
                    .extracting(pair -> pair.key)
                    .containsExactlyInAnyOrder("event", "userRef", "source", "type");
        }
    }

    @Test
    void shouldReturnMaskedTextWhenSecretIsNotBlocking() {
        String masked =
                masker.maskOrReject(
                        UUID.randomUUID(), "SIDE_PROJECT", "key=" + "AKIA" + "IOSFODNN7EXAMPLE");

        assertThat(masked).isEqualTo("key=[REDACTED:AWS_ACCESS_KEY]");
        assertThat(masker.maskOrRejectNullable(UUID.randomUUID(), "SIDE_PROJECT", null)).isNull();
    }
}
