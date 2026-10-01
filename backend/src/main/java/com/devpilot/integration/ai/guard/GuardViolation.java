package com.devpilot.integration.ai.guard;

/**
 * 가드 위반 1건 (docs/17 §6.1). 재시도 feedback 블록에 {@code <path>: <message> (<GUARD>)}로 들어간다 — 경로와 고정 문구만
 * 쓰고 모델 출력이나 사용자 입력을 되풀이하지 않는다(docs/17 §5.2).
 */
public record GuardViolation(String guard, String path, String message) {

    /** feedback 블록 한 줄. */
    public String feedbackLine() {
        return "- " + path + ": " + message + " (" + guard + ")";
    }
}
