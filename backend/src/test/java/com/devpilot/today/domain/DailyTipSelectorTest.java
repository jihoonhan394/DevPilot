package com.devpilot.today.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.domain.TipLevel;
import com.devpilot.common.domain.TipSeries;
import com.devpilot.testsupport.UnitTest;
import com.devpilot.testsupport.VectorLoader;
import com.devpilot.today.domain.DailyTipSelector.TipInput;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** docs/06 §5.12의 test vector 전부 (TIP-V1~TIP-V7). */
@UnitTest
class DailyTipSelectorTest {

    private static final String VECTOR_FILE = "06-05-daily-tip.yaml";

    private static final List<DailyTip> TIPS =
            List.of(
                    tip(
                            "TIP.DATABASE.INDEX.001",
                            TipSeries.DATABASE,
                            TipLevel.PRACTICAL,
                            "DATABASE.INDEX",
                            false),
                    tip(
                            "TIP.LOGGING.LEVELS.001",
                            TipSeries.LOGGING,
                            TipLevel.PRACTICAL,
                            "PRACTICAL_ENGINEERING.LOGGING",
                            false),
                    tip(
                            "TIP.LOGGING.LEVELS.002",
                            TipSeries.LOGGING,
                            TipLevel.BASIC,
                            "PRACTICAL_ENGINEERING.LOGGING",
                            false),
                    tip(
                            "TIP.OPERATIONS.HEALTHCHECK.001",
                            TipSeries.OPERATIONS,
                            TipLevel.BASIC,
                            "DEVOPS.DOCKER",
                            false),
                    tip(
                            "TIP.CONVENTION.COMMIT.001",
                            TipSeries.CONVENTION,
                            TipLevel.BASIC,
                            "DEVOPS.GIT",
                            true));

    private final DailyTipSelector selector = new DailyTipSelector();

    @ParameterizedTest(name = "[{index}] {0} {1}")
    @MethodSource("vectors")
    void shouldMatchVectorWhenTodaysTipIsChosen(
            String id, String caseName, Map<String, Object> row) {
        Optional<DailyTip> chosen =
                selector.select(
                        new TipInput(
                                TIPS,
                                strings(row, "shown"),
                                strings(row, "todayMain"),
                                strings(row, "recent"),
                                strings(row, "plan"),
                                Boolean.TRUE.equals(row.get("basicTipsFirst"))));

        assertThat(chosen.map(DailyTip::key).orElse(null))
                .as(id + " " + caseName)
                .isEqualTo(row.get("expected"));
    }

    /** 같은 입력이면 항상 같은 팁이다 — 하루에 하나라 흔들리면 안 된다 (TIP-3). */
    @Test
    void shouldBeDeterministic() {
        TipInput input =
                new TipInput(TIPS, Set.of(), Set.of(), Set.of(), Set.of("DEVOPS.DOCKER"), false);

        assertThat(selector.select(input)).isEqualTo(selector.select(input));
    }

    /** 은퇴한 팁은 남은 것이 그것뿐이어도 고르지 않는다 (TIP-1). */
    @Test
    void shouldNeverChooseARetiredTip() {
        Set<String> allButRetired =
                Set.of(
                        "TIP.DATABASE.INDEX.001",
                        "TIP.LOGGING.LEVELS.001",
                        "TIP.LOGGING.LEVELS.002",
                        "TIP.OPERATIONS.HEALTHCHECK.001");

        assertThat(
                        selector.select(
                                new TipInput(
                                        TIPS,
                                        allButRetired,
                                        Set.of("DEVOPS.GIT"),
                                        Set.of(),
                                        Set.of(),
                                        false)))
                .isEmpty();
    }

    static Stream<Arguments> vectors() {
        return VectorLoader.yamlRows(VECTOR_FILE).stream()
                .map(row -> Arguments.of(row.get("id"), row.get("case"), row));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> strings(Map<String, Object> row, String key) {
        List<String> values = (List<String>) row.get(key);
        return values == null ? Set.of() : Set.copyOf(values);
    }

    private static DailyTip tip(
            String key, TipSeries series, TipLevel level, String skillCode, boolean retired) {
        return new DailyTip(
                key,
                series,
                level,
                List.of(skillCode),
                "제목",
                "증상",
                "원인",
                null,
                "어디를 보나",
                "5분 실험",
                null,
                3,
                retired);
    }
}
