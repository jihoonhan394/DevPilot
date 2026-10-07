package com.devpilot.common.web;

/** 축별 레벨 (docs/05 §2.2). {@code SkillAxis} 선언 순서, 각 0~5. 레벨 규칙 입력·출력과 응답에 같이 쓴다. */
public record AxisLevels(int knowledge, int implementation, int explanation, int debugging) {

    /** 네 축 모두 0. */
    public static final AxisLevels ZERO = new AxisLevels(0, 0, 0, 0);

    /** 네 축이 같은 값. */
    public static AxisLevels uniform(int level) {
        return new AxisLevels(level, level, level, level);
    }

    /**
     * 네 축이 모두 {@code targets} 이상인가. <b>목표 비교의 단일 출처</b>다 — Today({@code PlannerScoring})와 대시보드
     * ({@code DashboardProgressAssembler})가 같은 단계를 가리켜야 하는데(docs/05 §13.1, AC-38 S2) 모듈 의존 규칙
     * (ARCH-02)상 둘이 서로의 domain 클래스를 부를 수 없다. 그래서 비교만 여기로 모으고, 함께 요구하는 "학습 기록이 있다" ({@code
     * lastPracticedAt != null})는 양쪽에 두고 계약 테스트로 묶는다.
     *
     * @param targets <b>현재 판정 목표</b>를 넘긴다 — 호출자가 {@code MeasurableAxes.forProgress}를 이미 적용한 값이다
     *     (ADR-070, docs/06 §7.6b)
     */
    public boolean meets(AxisLevels targets) {
        return knowledge >= targets.knowledge()
                && implementation >= targets.implementation()
                && explanation >= targets.explanation()
                && debugging >= targets.debugging();
    }
}
