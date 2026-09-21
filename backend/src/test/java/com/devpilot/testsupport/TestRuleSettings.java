package com.devpilot.testsupport;

import com.devpilot.learning.domain.ComebackModePolicy;
import com.devpilot.learning.domain.RubricScorer;
import com.devpilot.plan.domain.DeadlineRiskEvaluator;
import com.devpilot.plan.domain.StudyBudgetCalculator;
import com.devpilot.review.domain.RuleBasedV1Scheduler;
import com.devpilot.rubberduck.domain.RubberDuckPolicy;
import com.devpilot.skill.domain.SkillLevelRules;
import com.devpilot.today.domain.PlannerScoring;
import com.devpilot.today.domain.ReasonTemplates;
import com.devpilot.today.domain.TimeAllocator;
import java.time.Duration;
import java.util.List;

/**
 * 규칙 클래스 생성자에 넘기는 설정 (docs/09 §5.2 끝). {@code docs/03 §9} 기본값을 bp/micro 정수로 바꾼 값이다. 운영 설정 변환과 같은지는
 * {@code RuleSettingsFactoryTest}(common.config)가 확인한다.
 */
public final class TestRuleSettings {

    private TestRuleSettings() {}

    public static StudyBudgetCalculator.Settings budget() {
        return new StudyBudgetCalculator.Settings(14, 7_000, 3_000);
    }

    public static DeadlineRiskEvaluator.Settings risk() {
        return new DeadlineRiskEvaluator.Settings(
                5_000, 10_000, 4_000, 8_000, 11_500, 8_000, 10_000, 12_500);
    }

    public static PlannerScoring.Settings planner() {
        return new PlannerScoring.Settings(
                new PlannerScoring.Weights(2_500, 2_000, 2_000, 1_500, 1_000, 1_000),
                new PlannerScoring.Modifiers(
                        12_000, 8_000, 7_000, 11_000, 8_000, 6_000, 11_500, 7_000, 7_000, 4_000),
                new PlannerScoring.FactorSettings(
                        300_000, 500_000, 200_000, 100_000, 300_000, 100_000, 1_000_000),
                30);
    }

    public static TimeAllocator.Settings timeAllocation() {
        return new TimeAllocator.Settings(15_000, 2_500, 5, 11_000, 10, 15, 3, 20, 10);
    }

    public static ReasonTemplates.Settings reasons() {
        return new ReasonTemplates.Settings(700_000, 400_000);
    }

    /** docs/06 §8.1 coverage 경계 (CORRECT 8000bp, PARTIAL 4000bp). */
    public static RubricScorer.Settings rubricScorer() {
        return new RubricScorer.Settings(8_000, 4_000);
    }

    public static RuleBasedV1Scheduler.Settings review() {
        return new RuleBasedV1Scheduler.Settings(1, 60, false, 12_000, 20_000, 30_000, 2, 4);
    }

    public static ComebackModePolicy comeback() {
        return new ComebackModePolicy(3);
    }

    /** docs/06 §7.1 기본값: 60일 창, 24시간 cooldown, 진단 상한 3, 러버덕 증거 coverage 7000. */
    public static SkillLevelRules.Settings skillLevel() {
        return new SkillLevelRules.Settings(60, Duration.ofHours(24), 3, 7_000);
    }

    /** docs/06 §9.5 기본값 (RD-3·RD-4). */
    public static RubberDuckPolicy.Settings rubberDuck() {
        return new RubberDuckPolicy.Settings(
                5,
                2,
                30,
                List.of("모르겠", "모름", "잘 모르", "생각 안", "idk", "no idea", "don't know", "dont know"));
    }
}
