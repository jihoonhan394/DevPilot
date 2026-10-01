package com.devpilot.today.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 백지 문제 (docs/19 §3.14). 예제의 복사가 아니라 변형이다.
 *
 * <p><b>서버가 채점하지 않는다.</b> 사용자가 자기 IDE에서 돌려 보고 낸 답을 {@code modelAnswer}·{@code selfChecks}와 스스로
 * 견준다(docs/05 §21.6, docs/01 원칙 9).
 *
 * @param deliverables 무엇을 내야 하는지 2~4개
 * @param starterCode 빈 화면을 피하려고 답안칸에 미리 넣는 코드. 없을 수 있다
 * @param hints 2~3개. 뒤로 갈수록 구체적이고 답을 그대로 말하지 않는다
 * @param selfChecks 모범 답안과 견줘 스스로 체크할 것 2~4개
 */
public record LessonProblem(
        String prompt,
        List<String> deliverables,
        @Nullable String starterCode,
        List<String> hints,
        String modelAnswer,
        List<String> selfChecks) {

    public LessonProblem {
        deliverables = List.copyOf(deliverables);
        hints = List.copyOf(hints);
        selfChecks = List.copyOf(selfChecks);
    }
}
