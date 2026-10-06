package com.devpilot.today.domain;

import com.devpilot.common.math.FixedPointMath;
import java.util.List;
import java.util.Objects;

/**
 * 적정 난이도 밴드 (docs/06 §5.3, ADR-066).
 *
 * <p>§5.3은 난이도를 {@code planning IMPLEMENTATION + 1}로 정했다 — 지금 수준보다 한 단계 위라는 고정 규칙이고, <b>실제로 몇 개를
 * 맞히고 있는지는 보지 않았다.</b> 그래서 자기평가가 높으면 계속 어려운 문제가 나오고, 낮으면 계속 쉬운 문제가 나왔다.
 *
 * <p>Wilson et al.(2019) <i>The Eighty Five Percent Rule for optimal learning</i>는 정답률 <b>85%</b>
 * 부근에서 학습이 가장 빠르다고 본다 — 다 맞히면 무엇을 고칠지 알 수 없고, 다 틀리면 무엇이 통했는지 알 수 없다. 그래서 측정된 성공률이 상한을 넘으면 한 단계 올리고
 * 하한 아래면 한 단계 내린다.
 *
 * <p><b>법칙이 아니라 목표 밴드다.</b> 논문은 이진 분류 과제에서 유도한 값이고 저자 본인이 모든 학습에 그대로 적용되지는 않을 것이라고 적었다. 그래서 조정은
 * <b>한 번에 ±1</b>이고, 표본이 모자라면 움직이지 않는다.
 *
 * <p><b>사용자 전체 성공률을 쓴다(skill별이 아니다).</b> skill 하나에 attempt가 쌓이는 데 몇 주가 걸려 skill별 표본으로는 규칙이 영원히 발동하지
 * 않는다. 85% 규칙이 말하는 것도 "지금 내는 난이도에서 학습자가 얼마나 맞히는가"이므로 학습자 단위가 맞다. skill별 차이는 이미 {@code planning}이 들고
 * 있다.
 */
public final class DifficultyBandPolicy {

    private final Settings settings;

    public DifficultyBandPolicy(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * 측정된 성공률로 기준 난이도를 ±1 조정한다.
     *
     * @param baseDifficulty {@code planning IMPLEMENTATION + 1}을 트랙 상한으로 자른 값
     * @param outcomes 최근 평가가 끝난 attempt의 결과, <b>최신순</b>. 창을 넘는 것은 호출자가 잘라도 되고 여기서 잘라도 된다
     * @param maxDifficulty 트랙 상한 (docs/06 §5.3 {@code maxTaskDifficulty})
     * @return 조정된 난이도. 표본이 {@code minSamples} 미만이면 {@code baseDifficulty} 그대로
     */
    public int adjust(int baseDifficulty, List<Outcome> outcomes, int maxDifficulty) {
        List<Outcome> window = outcomes.stream().limit(settings.windowSize()).toList();
        if (window.size() < settings.minSamples()) {
            return baseDifficulty;
        }
        long solved = window.stream().filter(Outcome::solved).count();
        int rateBp =
                Math.toIntExact(
                        FixedPointMath.floorDiv(
                                Math.multiplyExact(solved, FixedPointMath.BP_SCALE),
                                window.size()));
        int adjusted = baseDifficulty;
        if (rateBp > settings.upperBp()) {
            adjusted = baseDifficulty + 1;
        } else if (rateBp < settings.lowerBp()) {
            adjusted = baseDifficulty - 1;
        }
        return Math.clamp((long) adjusted, 1, maxDifficulty);
    }

    /**
     * attempt 하나의 결과.
     *
     * @param solved 답을 맞혔는가 — {@code SOLVED_INDEPENDENTLY}·{@code SOLVED_WITH_HINTS}가 true다. 85%
     *     규칙은 "맞혔는가"를 재므로 힌트를 본 것도 성공으로 센다. 힌트 사용은 §7.2 상승 규칙이 따로 본다
     */
    public record Outcome(boolean solved) {}

    /**
     * 밴드 설정 (docs/03 §9 {@code devpilot.planner.difficulty-band}).
     *
     * @param upperBp 이 비율을 <b>넘으면</b> 한 단계 올린다 (기본 8_500 = 85%)
     * @param lowerBp 이 비율 <b>아래면</b> 한 단계 내린다 (기본 7_000 = 70%)
     * @param windowSize 최근 몇 개를 보나
     * @param minSamples 이보다 적으면 움직이지 않는다 — 두세 번으로 난이도를 흔들지 않는다
     */
    public record Settings(int upperBp, int lowerBp, int windowSize, int minSamples) {

        public Settings {
            if (lowerBp > upperBp) {
                throw new IllegalArgumentException("lowerBp must not exceed upperBp");
            }
            if (minSamples < 1 || windowSize < minSamples) {
                throw new IllegalArgumentException("windowSize must be at least minSamples >= 1");
            }
        }
    }
}
