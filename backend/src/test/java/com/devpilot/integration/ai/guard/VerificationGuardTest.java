package com.devpilot.integration.ai.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.GuardContext;
import com.devpilot.integration.ai.api.output.CoachFindingOutput;
import com.devpilot.integration.ai.api.output.CoachReviewOutput;
import com.devpilot.testsupport.TestProperties;
import com.devpilot.testsupport.UnitTest;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** docs/06 §10 vector 7행 + docs/17 §6.2 추가 vector. 서버는 URL을 fetch하지 않는다(호스트 문자열만 본다). */
@UnitTest
class VerificationGuardTest {

    private final VerificationGuard guard =
            new VerificationGuard(
                    TestProperties.testProfile().ai().trustedSourceHosts(),
                    Set.of("CS-SPRING-TX-ROLLBACK"));

    static Stream<Arguments> vectors() {
        return Stream.of(
                Arguments.of("VERIFIED", "AI_REASONING", null, "AI_JUDGMENT", "AI_REASONING"),
                Arguments.of(
                        "SUPPORTED",
                        "STATIC_ANALYSIS",
                        "PMD CloseResource",
                        "AI_JUDGMENT",
                        "AI_REASONING"),
                Arguments.of(
                        "SUPPORTED",
                        "OFFICIAL_DOC",
                        "https://pmd.github.io/pmd/pmd_rules_java_errorprone.html#closeresource",
                        "SUPPORTED",
                        "OFFICIAL_DOC"),
                Arguments.of(
                        "VERIFIED",
                        "OFFICIAL_DOC",
                        "https://docs.spring.io/spring-framework/reference/",
                        "SUPPORTED",
                        "OFFICIAL_DOC"),
                Arguments.of(
                        "SUPPORTED",
                        "OFFICIAL_DOC",
                        "https://blog.example.com/post",
                        "AI_JUDGMENT",
                        "AI_REASONING"),
                Arguments.of("UNCERTAIN", "COMPILER", null, "UNCERTAIN", "AI_REASONING"),
                Arguments.of("SUPPORTED", "AI_REASONING", null, "AI_JUDGMENT", "AI_REASONING"),
                Arguments.of(
                        "SUPPORTED",
                        "OFFICIAL_DOC",
                        "Spring Framework Reference",
                        "AI_JUDGMENT",
                        "AI_REASONING"),
                Arguments.of(
                        "SUPPORTED",
                        "OFFICIAL_DOC",
                        "http://docs.spring.io/spring-framework/reference/",
                        "AI_JUDGMENT",
                        "AI_REASONING"),
                Arguments.of(
                        "SUPPORTED",
                        "SECURITY_GUIDE",
                        "https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html",
                        "SUPPORTED",
                        "SECURITY_GUIDE"),
                Arguments.of(
                        "SUPPORTED",
                        "OFFICIAL_DOC",
                        "https://spring.io.evil.example/docs",
                        "AI_JUDGMENT",
                        "AI_REASONING"),
                Arguments.of(
                        "SUPPORTED",
                        "OFFICIAL_DOC",
                        "https://docs.spring.io@evil.example/",
                        "AI_JUDGMENT",
                        "AI_REASONING"),
                Arguments.of(
                        "UNCERTAIN",
                        "OFFICIAL_DOC",
                        "https://docs.oracle.com/en/java/javase/25/",
                        "UNCERTAIN",
                        "OFFICIAL_DOC"),
                Arguments.of(
                        "VERIFIED",
                        "CURATED_SOURCE",
                        "CS-SPRING-TX-ROLLBACK",
                        "VERIFIED",
                        "CURATED_SOURCE"),
                Arguments.of(
                        "VERIFIED", "CURATED_SOURCE", "CS-UNKNOWN", "AI_JUDGMENT", "AI_REASONING"));
    }

    @ParameterizedTest(name = "[{index}] {0} {1} {2}")
    @MethodSource("vectors")
    void shouldDowngradeAccordingToRulesWhenFindingIsChecked(
            String status,
            String sourceType,
            @Nullable String reference,
            String expectedStatus,
            String expectedSource) {
        GuardOutcome outcome =
                guard.apply(
                        AiOperation.COACH_REVIEW,
                        GuardFixtures.review(
                                List.of(GuardFixtures.verification(status, sourceType, reference))),
                        GuardContext.empty(),
                        false);

        CoachFindingOutput finding = ((CoachReviewOutput) outcome.value()).findings().getFirst();
        assertThat(finding.verificationStatus()).isEqualTo(expectedStatus);
        assertThat(finding.sourceType()).isEqualTo(expectedSource);
        assertThat(outcome.violations()).isEmpty();
    }

    @Test
    void shouldTrustSubdomainButNotLookalikeHost() {
        assertThat(guard.trusted("https://sub.docs.spring.io/x")).isTrue();
        assertThat(guard.trusted("https://docs.spring.io.evil.com/x")).isFalse();
        assertThat(guard.trusted("https://docs.spring.io./x")).isTrue();
        assertThat(guard.trusted("not a uri at all ::")).isFalse();
    }
}
