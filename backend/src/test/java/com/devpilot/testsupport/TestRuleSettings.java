package com.devpilot.testsupport;

import com.devpilot.plan.domain.DeadlineRiskEvaluator;
import com.devpilot.plan.domain.StudyBudgetCalculator;

/**
 * 규칙 클래스 생성자에 넘기는 설정 (docs/09 §5.2 끝). {@code docs/03 §9} 기본값을 bp/micro 정수로 바꾼 값이다. 운영 설정 변환과 같은지는
 * {@code RuleSettingsFactoryTest}가 확인한다.
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
}
