package com.devpilot.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.plan.application.PlanRuleSettings;
import com.devpilot.review.application.ReviewRuleSettings;
import com.devpilot.rubberduck.application.RubberDuckRuleSettings;
import com.devpilot.rubberduck.domain.RubberDuckPolicy;
import com.devpilot.testsupport.TestProperties;
import com.devpilot.testsupport.TestRuleSettings;
import com.devpilot.testsupport.UnitTest;
import com.devpilot.today.application.TodayRuleSettings;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * docs/06 §1 N-6, docs/09 §5.2: 운영 {@code application.yml} 기본값을 규칙 설정(bp/micro 정수)으로 바꾼 값이 vector
 * 테스트가 쓰는 {@link TestRuleSettings}와 같아야 한다. 설정 변환이 틀리면 vector는 통과해도 운영 계산이 달라진다.
 */
@UnitTest
class RuleSettingsFactoryTest {

    private final DevPilotProperties properties =
            TestProperties.defaults(Map.of("APP_BASE_URL", "http://localhost:5173"));

    @Test
    void shouldConvertBudgetAndRiskSettings() {
        assertThat(PlanRuleSettings.budget(properties)).isEqualTo(TestRuleSettings.budget());
        assertThat(PlanRuleSettings.risk(properties)).isEqualTo(TestRuleSettings.risk());
    }

    @Test
    void shouldConvertRubberDuckSettings() {
        assertThat(RubberDuckRuleSettings.policy(properties))
                .usingRecursiveComparison()
                .isEqualTo(new RubberDuckPolicy(TestRuleSettings.rubberDuck()));
    }

    @Test
    void shouldConvertPlannerSettings() {
        assertThat(TodayRuleSettings.planner(properties)).isEqualTo(TestRuleSettings.planner());
        assertThat(TodayRuleSettings.timeAllocation(properties))
                .isEqualTo(TestRuleSettings.timeAllocation());
        assertThat(TodayRuleSettings.reasons(properties)).isEqualTo(TestRuleSettings.reasons());
    }

    @Test
    void shouldConvertReviewSchedulerSettings() {
        assertThat(ReviewRuleSettings.scheduler(properties)).isEqualTo(TestRuleSettings.review());
    }

    @Test
    void shouldUseDocumentedComebackWindow() {
        assertThat(properties.planner().comebackInactiveDays()).isEqualTo(3);
    }

    @Test
    void shouldBindReviewAndRateLimitDefaults() {
        DevPilotProperties.Review review = properties.review();
        assertThat(review.maxPerDay()).isEqualTo(20);
        assertThat(review.comebackMaxPerDay()).isEqualTo(10);
        assertThat(review.hardStrategy()).isEqualTo(DevPilotProperties.HardStrategy.MULTIPLY);
        assertThat(review.suspendAfterFailures()).isEqualTo(4);

        DevPilotProperties.RateLimit rateLimit = properties.security().rateLimit();
        assertThat(rateLimit.requestsPerMinute()).isEqualTo(120);
        assertThat(rateLimit.calendarFeedPerHour()).isEqualTo(60);
        assertThat(rateLimit.calendarInvalidTokenPerHourPerIp()).isEqualTo(30);
        assertThat(rateLimit.devTokenPerHourPerIp()).isEqualTo(30);
    }
}
