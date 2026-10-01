package com.devpilot.today.domain;

import java.util.List;

/**
 * 학습 단위 (docs/19 §3.14). 5~15분짜리 한 바퀴다: 설명 → 예제 → 예측 → 빈칸 → 문제 → 견주기.
 *
 * @param key {@code <노트 key>.U<n>}. 진행·복습 기록이 가리키는 불변 키다
 * @param core 기한이 촉박하면 이것만 한다 (docs/06 TH-6)
 * @param prerequisiteUnits 막혔을 때 "먼저 볼 개념". 아직 만들지 않은 key도 들어갈 수 있다(CV-132)
 * @param variants 복습 때 낼 변형 문제. 비어 있으면 원 문제를 다시 낸다
 */
public record LessonUnit(
        String key,
        String title,
        int minutes,
        boolean core,
        String explain,
        LessonExample example,
        PredictQuestion predict,
        CompleteQuestion complete,
        LessonProblem problem,
        List<String> prerequisiteUnits,
        List<LessonProblem> variants) {

    public LessonUnit {
        prerequisiteUnits = List.copyOf(prerequisiteUnits);
        variants = List.copyOf(variants);
    }
}
