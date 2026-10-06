package com.devpilot.today.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devpilot.testsupport.UnitTest;
import com.devpilot.today.domain.DifficultyBandPolicy.Outcome;
import com.devpilot.today.domain.DifficultyBandPolicy.Settings;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * docs/06 §5.3 적정 난이도 밴드 vector DB-1~DB-10 (ADR-066).
 *
 * <p>고정 `planning + 1`은 실제로 몇 개를 맞히는지 보지 않았다. Wilson et al.(2019) 85% 규칙에 맞춰 측정된 성공률로 ±1 조정한다 — 법칙이
 * 아니라 목표 밴드이므로 한 번에 한 단계만, 표본이 모자라면 움직이지 않는다.
 */
@UnitTest
class DifficultyBandPolicyTest {

    private static final int MAX = 5;

    private final DifficultyBandPolicy policy =
            new DifficultyBandPolicy(new Settings(8_500, 7_000, 10, 5));

    /** {@code "1101"} → 최신순 성공/실패. */
    private static List<Outcome> solved(String pattern) {
        return pattern.chars().mapToObj(c -> new Outcome(c == '1')).toList();
    }

    static Stream<Arguments> vectors() {
        return Stream.of(
                Arguments.of("DB-1 표본이 모자라면 움직이지 않는다 (4개)", 3, "1111", 3),
                Arguments.of("DB-2 5개 전부 성공 = 100% > 85% → 올린다", 3, "11111", 4),
                Arguments.of("DB-3 10개 중 9개 = 90% > 85% → 올린다", 3, "1111111110", 4),
                Arguments.of("DB-4 10개 중 8개 = 80%, 밴드 안 → 그대로", 3, "1111111100", 3),
                Arguments.of("DB-5 10개 중 7개 = 70%, 하한과 같으므로 그대로", 3, "1111111000", 3),
                Arguments.of("DB-6 10개 중 6개 = 60% < 70% → 내린다", 3, "1111110000", 2),
                Arguments.of("DB-7 전부 실패 → 내린다", 3, "00000", 2),
                Arguments.of("DB-8 창을 넘는 것은 보지 않는다 (앞 10개만)", 3, "11111111110000", 4),
                Arguments.of("DB-9 상한에서는 더 올라가지 않는다", MAX, "11111", MAX),
                Arguments.of("DB-10 하한 1 아래로는 내려가지 않는다", 1, "00000", 1));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectors")
    void shouldMoveOneStepTowardTheBand(
            String description, int base, String pattern, int expected) {
        assertThat(policy.adjust(base, solved(pattern), MAX))
                .as("%s", description)
                .isEqualTo(expected);
    }

    /** 85%와 같을 때는 올리지 않는다 — "넘으면"이 조건이다. */
    @Test
    void shouldNotRaiseWhenTheRateEqualsTheUpperBound() {
        // 20개 중 17개 = 정확히 85%
        String pattern = "1".repeat(17) + "000";
        DifficultyBandPolicy wide = new DifficultyBandPolicy(new Settings(8_500, 7_000, 20, 5));

        assertThat(wide.adjust(3, solved(pattern), MAX)).isEqualTo(3);
    }

    /** 설정이 뒤집혀 있으면 기동 때 바로 막는다 — 조용히 이상하게 도는 것보다 낫다. */
    @Test
    void shouldRejectASettingsPairThatCannotFormABand() {
        assertThatThrownBy(() -> new Settings(7_000, 8_500, 10, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Settings(8_500, 7_000, 3, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
