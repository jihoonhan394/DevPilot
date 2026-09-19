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
 * docs/05 §19.7 {@code GET /readings/{readingKey}} (AC-28 S4, BL-TDY-16). 코드 본문이 없고, 은퇴한 reading도
 * 200이며, 사용자 소유 리소스가 아니다.
 */
@IntegrationTest
class ReadingControllerIntegrationTest extends ApiTestSupport {

    private static final String READINGS = "/api/v1/readings/{readingKey}";
    private static final String ACTIVE_KEY = "READ.TESTREPO.ORDER_SERVICE.001";
    private static final String RETIRED_KEY = "READ.TESTREPO.LEGACY_CONTROLLER.001";

    @Test
    void shouldReturnReadingWithoutSourceCode() throws Exception {
        TestUser user = onboardedOwner();

        JsonNode reading =
                api.body(
                        api.get(user, READINGS, ACTIVE_KEY)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.key").value(ACTIVE_KEY))
                                .andExpect(jsonPath("$.repo.key").value("testrepo"))
                                .andExpect(jsonPath("$.repo.name").value("Test Repository"))
                                .andExpect(
                                        jsonPath("$.repo.pinnedCommit")
                                                .value("0123456789abcdef0123456789abcdef01234567"))
                                .andExpect(jsonPath("$.startLine").value(10))
                                .andExpect(jsonPath("$.endLine").value(60))
                                .andExpect(jsonPath("$.estimatedMinutes").value(15))
                                .andExpect(jsonPath("$.retired").value(false)));

        assertThat(reading.propertyNames())
                .containsExactlyInAnyOrder(
                        "key",
                        "repo",
                        "path",
                        "startLine",
                        "endLine",
                        "skills",
                        "estimatedMinutes",
                        "question",
                        "lookFor",
                        "retired");
        assertThat(reading.path("repo").propertyNames())
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
        assertThat(reading.path("lookFor")).isNotEmpty();
    }

    @Test
    void shouldStillResolveRetiredReading() throws Exception {
        // docs/19 §8.2: 은퇴한 단위도 지난 과제·세션이 가리키므로 조회된다
        api.get(onboardedOwner(), READINGS, RETIRED_KEY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value(RETIRED_KEY))
                .andExpect(jsonPath("$.retired").value(true))
                .andExpect(jsonPath("$.startLine").value(20))
                .andExpect(jsonPath("$.endLine").value(45));
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
}
