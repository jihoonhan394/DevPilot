package com.devpilot.common.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.skill.domain.SkillAxis;
import com.devpilot.skill.domain.SkillStateChange;
import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.type.format.jackson.Jackson3JsonFormatMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * BL-FND-11: Clock 기반 auditing, 애플리케이션 생성 UUID + {@code Persistable}, {@code uuid[]} 매핑, Jackson 3
 * JSON mapper.
 */
@IntegrationTest
class JpaMappingIntegrationTest extends ApiTestSupport {

    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void shouldStampAuditTimesFromInjectedClock() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode created =
                api.body(
                        api.post(user, "/api/v1/side-projects", TestApi.sideProjectRequest("감사 시각"))
                                .andExpect(status().isCreated()));
        clock.advance(Duration.ofMinutes(5));

        api.patch(
                        user,
                        "/api/v1/side-projects/{id}",
                        Map.of("stack", "Spring", "version", 0),
                        created.path("id").asString())
                .andExpect(jsonPath("$.createdAt").value("2026-10-05T10:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-05T10:05:00Z"));
    }

    @Test
    void shouldRoundTripUuidArrayWhenStateChangeIsStored() throws Exception {
        TestUser user = onboardedOwner();
        UUID userId = userId(user);
        UUID skillId =
                jdbc.queryForObject(
                        "select id from devpilot.skill where code = 'JAVA.EXCEPTION'", UUID.class);
        List<UUID> evidence = List.of(UUID.randomUUID(), UUID.randomUUID());
        SkillStateChange change =
                SkillStateChange.record(
                        userId,
                        skillId,
                        new SkillStateChange.LevelTransition(SkillAxis.KNOWLEDGE, 0, 1),
                        "K1_ANY_EVENT",
                        evidence,
                        Instant.parse("2026-10-05T10:00:00Z"));

        transactionTemplate.executeWithoutResult(status -> entityManager.persist(change));
        SkillStateChange loaded =
                transactionTemplate.execute(
                        status -> entityManager.find(SkillStateChange.class, change.getId()));

        assertThat(loaded).isNotNull();
        assertThat(loaded.getEvidenceEventIds()).containsExactlyElementsOf(evidence);
        assertThat(loaded.isNew()).isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "select cardinality(evidence_event_ids) from"
                                        + " devpilot.skill_state_change where id = ?",
                                Integer.class,
                                change.getId()))
                .isEqualTo(2);
    }

    @Test
    void shouldUseJackson3ForJsonColumns() {
        SessionFactoryImplementor sessionFactory =
                entityManagerFactory.unwrap(SessionFactoryImplementor.class);

        assertThat(sessionFactory.getSessionFactoryOptions().getJsonFormatMapper())
                .isInstanceOf(Jackson3JsonFormatMapper.class);
    }
}
