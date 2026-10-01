package com.devpilot.today.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * docs/05 §19.7 {@code GET /readings/{readingKey}} (AC-28 S4, BL-TDY-16). 코드 읽기({@code kind =
 * CODE})와 개념 읽기({@code kind = CONCEPT})를 한 endpoint가 돌려주고, 본문(코드·문서)은 없으며, 은퇴한 단위도 200이고, 사용자 소유
 * 리소스가 아니다.
 */
@IntegrationTest
class ReadingControllerIntegrationTest extends ApiTestSupport {

    private static final String READINGS = "/api/v1/readings/{readingKey}";
    private static final String ACTIVE_KEY = "READ.TESTREPO.ORDER_SERVICE.001";
    private static final String RETIRED_KEY = "READ.TESTREPO.LEGACY_CONTROLLER.001";
    private static final String CONCEPT_KEY = "DOC.TESTJAVA.EXCEPTION.001";
    private static final String RETIRED_CONCEPT_KEY = "DOC.TESTJAVA.LEGACY.001";

    @Test
    void shouldReturnCodeReadingWithoutSourceCode() throws Exception {
        TestUser user = onboardedOwner();

        JsonNode reading =
                api.body(
                        api.get(user, READINGS, ACTIVE_KEY)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.key").value(ACTIVE_KEY))
                                .andExpect(jsonPath("$.kind").value("CODE"))
                                .andExpect(jsonPath("$.code.repo.key").value("testrepo"))
                                .andExpect(jsonPath("$.code.repo.name").value("Test Repository"))
                                .andExpect(
                                        jsonPath("$.code.repo.pinnedCommit")
                                                .value("0123456789abcdef0123456789abcdef01234567"))
                                .andExpect(jsonPath("$.code.startLine").value(10))
                                .andExpect(jsonPath("$.code.endLine").value(60))
                                .andExpect(jsonPath("$.estimatedMinutes").value(15))
                                .andExpect(jsonPath("$.retired").value(false)));

        assertThat(reading.propertyNames())
                .containsExactlyInAnyOrder(
                        "key", "kind", "skills", "estimatedMinutes", "retired", "code", "concept");
        assertThat(reading.path("concept").isNull()).isTrue();
        assertThat(reading.path("code").propertyNames())
                .containsExactlyInAnyOrder(
                        "repo", "path", "startLine", "endLine", "question", "lookFor");
        assertThat(reading.path("code").path("repo").propertyNames())
                .containsExactlyInAnyOrder(
                        "key",
                        "name",
                        "url",
                        "subPath",
                        "license",
                        "licenseNote",
                        "stack",
                        "why",
                        "cloneHint",
                        "pinnedCommit");
        assertThat(reading.path("skills").get(0).path("code").asString())
                .isEqualTo("SPRING.TRANSACTION");
        assertThat(reading.path("code").path("lookFor")).isNotEmpty();
    }

    @Test
    void shouldReturnConceptReadingWithoutDocumentBody() throws Exception {
        // docs/19 §3.13: READING 과제가 읽을 공식 문서 1페이지. 서버는 url을 fetch하지 않는다
        JsonNode reading =
                api.body(
                        api.get(onboardedOwner(), READINGS, CONCEPT_KEY)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.key").value(CONCEPT_KEY))
                                .andExpect(jsonPath("$.kind").value("CONCEPT"))
                                .andExpect(jsonPath("$.estimatedMinutes").value(20))
                                .andExpect(jsonPath("$.retired").value(false))
                                .andExpect(jsonPath("$.concept.publisher").value("Oracle"))
                                .andExpect(jsonPath("$.concept.versionScope").value("Java SE 25"))
                                .andExpect(jsonPath("$.concept.verifiedAt").value("2026-09-21")));

        assertThat(reading.path("code").isNull()).isTrue();
        assertThat(reading.path("concept").propertyNames())
                .containsExactlyInAnyOrder(
                        "title",
                        "url",
                        "publisher",
                        "versionScope",
                        "whyRead",
                        "checkPoints",
                        "verifiedAt");
        assertThat(reading.path("concept").path("url").asString()).startsWith("https://");
        assertThat(reading.path("concept").path("checkPoints")).hasSize(3);
        assertThat(reading.path("skills").get(0).path("code").asString())
                .isEqualTo("JAVA.EXCEPTION");
    }

    @Test
    void shouldStillResolveRetiredReadings() throws Exception {
        // docs/19 §8.2: 은퇴한 단위도 지난 과제·세션이 가리키므로 조회된다 (두 종류 모두)
        TestUser user = onboardedOwner();

        api.get(user, READINGS, RETIRED_KEY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value(RETIRED_KEY))
                .andExpect(jsonPath("$.kind").value("CODE"))
                .andExpect(jsonPath("$.retired").value(true))
                .andExpect(jsonPath("$.code.startLine").value(20))
                .andExpect(jsonPath("$.code.endLine").value(45));
        api.get(user, READINGS, RETIRED_CONCEPT_KEY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value(RETIRED_CONCEPT_KEY))
                .andExpect(jsonPath("$.kind").value("CONCEPT"))
                .andExpect(jsonPath("$.retired").value(true))
                .andExpect(jsonPath("$.concept.title").isNotEmpty());
    }

    @Test
    void shouldRejectMalformedKeyAndUnknownKey() throws Exception {
        TestUser user = onboardedOwner();

        api.get(user, READINGS, "read.testrepo.x")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("readingKey"))
                .andExpect(jsonPath("$.errors[0].code").value("Pattern"));
        api.get(user, READINGS, "READ.NOPE.TOPIC.001")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        api.get(user, READINGS, "DOC.NOPE.TOPIC.001")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldRequireTokenButNotOwnership() throws Exception {
        mockMvc.perform(get(READINGS, ACTIVE_KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        String first =
                api.get(onboardedOwner(), READINGS, ACTIVE_KEY)
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String second =
                api.get(onboardedOwner(), READINGS, ACTIVE_KEY)
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void shouldShowTheSameConceptReadingToEveryUser() throws Exception {
        // docs/05 §19.7: 공용 콘텐츠다 — 다른 사용자의 토큰이어도 404가 아니라 같은 내용을 본다
        String first =
                api.get(onboardedOwner(), READINGS, CONCEPT_KEY)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String second =
                api.get(onboardedOwner(), READINGS, CONCEPT_KEY)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(second).isEqualTo(first);
    }
}
