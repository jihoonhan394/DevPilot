package com.devpilot.plan.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.UnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 위험도가 여러 날 이어지면 replan을 권한다 (docs/06 §11.1, ADR-046). */
@UnitTest
class ReplanRecommendationPolicyTest {

    private static final int AFTER_DAYS = 7;

    private static List<RiskLevel> repeat(RiskLevel level, int times) {
        return java.util.Collections.nCopies(times, level);
    }

    @Test
    void shouldRecommendWhenEveryRecentDayIsHighOrWorse() {
        assertThat(
                        ReplanRecommendationPolicy.shouldRecommend(
                                repeat(RiskLevel.HIGH, AFTER_DAYS), AFTER_DAYS))
                .isTrue();
        assertThat(
                        ReplanRecommendationPolicy.shouldRecommend(
                                repeat(RiskLevel.CRITICAL, AFTER_DAYS), AFTER_DAYS))
                .isTrue();
    }

    /** 하루라도 숨통이 트이면 연속이 끊긴다 — 나쁜 날 하나로 재촉하지 않는다. */
    @Test
    void shouldNotRecommendWhenOneDayRecovered() {
        List<RiskLevel> withGap =
                List.of(
                        RiskLevel.CRITICAL,
                        RiskLevel.CRITICAL,
                        RiskLevel.MEDIUM,
                        RiskLevel.CRITICAL,
                        RiskLevel.CRITICAL,
                        RiskLevel.CRITICAL,
                        RiskLevel.CRITICAL);

        assertThat(ReplanRecommendationPolicy.shouldRecommend(withGap, AFTER_DAYS)).isFalse();
    }

    /** 가장 최근 날이 좋아졌으면 권하지 않는다 — 오늘 나아진 사람에게 할 말이 아니다. */
    @Test
    void shouldNotRecommendWhenTodayImproved() {
        List<RiskLevel> improvedToday =
                List.of(
                        RiskLevel.LOW,
                        RiskLevel.CRITICAL,
                        RiskLevel.CRITICAL,
                        RiskLevel.CRITICAL,
                        RiskLevel.CRITICAL,
                        RiskLevel.CRITICAL,
                        RiskLevel.CRITICAL);

        assertThat(ReplanRecommendationPolicy.shouldRecommend(improvedToday, AFTER_DAYS)).isFalse();
    }

    /** 스냅샷이 모자라면 아직 판단하지 않는다. 시작한 지 며칠 안 된 사람이 여기 해당한다. */
    @Test
    void shouldNotRecommendBeforeEnoughSnapshotsExist() {
        assertThat(
                        ReplanRecommendationPolicy.shouldRecommend(
                                repeat(RiskLevel.CRITICAL, AFTER_DAYS - 1), AFTER_DAYS))
                .isFalse();
        assertThat(ReplanRecommendationPolicy.shouldRecommend(List.of(), AFTER_DAYS)).isFalse();
    }

    /** 요청한 날 수보다 많이 읽어 왔으면 앞쪽만 본다. */
    @Test
    void shouldOnlyLookAtTheMostRecentDays() {
        List<RiskLevel> longerHistory =
                java.util.stream.Stream.concat(
                                repeat(RiskLevel.CRITICAL, AFTER_DAYS).stream(),
                                java.util.stream.Stream.of(RiskLevel.LOW, RiskLevel.LOW))
                        .toList();

        assertThat(ReplanRecommendationPolicy.shouldRecommend(longerHistory, AFTER_DAYS)).isTrue();
    }

    @Test
    void shouldNotRecommendForAnImpossibleThreshold() {
        assertThat(ReplanRecommendationPolicy.shouldRecommend(repeat(RiskLevel.CRITICAL, 3), 0))
                .isFalse();
    }
}
