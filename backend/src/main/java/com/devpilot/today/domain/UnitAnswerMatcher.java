package com.devpilot.today.domain;

import java.util.List;

/**
 * 학습 단위의 예측·빈칸 채점 (docs/19 §3.14 "채점"). 순수 규칙 클래스다(ARCH-12) — AI를 쓰지 않는다.
 *
 * <p>비교 규칙: 앞뒤 공백을 버리고 <b>연속 공백을 하나로</b> 줄인 뒤 <b>대소문자를 구분해</b> 비교한다. 대소문자를 구분하는 것은 식별자와 애너테이션이 답이기
 * 때문이다({@code @GetMapping}과 {@code @getmapping}은 다른 것이다). 다르게 쓸 수 있는 표기는 콘텐츠의 허용 표기 목록에 모두 적는다.
 */
public final class UnitAnswerMatcher {

    private UnitAnswerMatcher() {}

    /** 비교용으로 다듬는다. */
    public static String normalize(String text) {
        return text.strip().replaceAll("\\s+", " ");
    }

    /** 한 답이 기대값과 같은가. */
    public static boolean matches(String given, String expected) {
        return normalize(given).equals(normalize(expected));
    }

    /** 한 답이 허용 표기 중 하나와 같은가. */
    public static boolean matchesAny(String given, List<String> accepted) {
        String normalized = normalize(given);
        return accepted.stream().anyMatch(value -> normalize(value).equals(normalized));
    }

    /** 빈칸마다 맞았는지. 답 개수는 호출 전에 맞춰 둔다. */
    public static List<Boolean> matchBlanks(List<String> given, List<List<String>> accepted) {
        return java.util.stream.IntStream.range(0, accepted.size())
                .mapToObj(index -> matchesAny(given.get(index), accepted.get(index)))
                .toList();
    }
}
