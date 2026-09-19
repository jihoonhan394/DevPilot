package com.devpilot.content.application;

import java.util.List;

/**
 * {@code ContentValidator} 결과 (docs/19 §4). ERROR가 1개라도 있으면 기동 실패, WARN은 로그만 남긴다.
 *
 * @param errors 규칙 ID 순이 아니라 발견 순서
 */
public record ContentValidationReport(List<Issue> errors, List<Issue> warnings) {

    public ContentValidationReport {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /** 오류·경고 1건. {@code rule}은 CV-xx, {@code where}는 {@code <파일>#<식별자>}. */
    public record Issue(String rule, String where, String message) {}
}
