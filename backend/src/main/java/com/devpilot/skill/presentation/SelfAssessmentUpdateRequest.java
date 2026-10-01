package com.devpilot.skill.presentation;

import com.devpilot.skill.domain.SkillCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * {@code PUT /skills/me/self-assessment} 요청 (docs/05 §6.5).
 *
 * <p><b>적은 category만 바꾼다.</b> 전체 교체가 아니다 — 한 칸을 고치려고 열네 칸을 다시 보내게 하면 나머지를 실수로 덮어쓴다. 그래서 최소 1개만 있으면
 * 된다.
 *
 * <p>{@code CategoryLevel}은 온보딩의 {@code SelfAssessmentInput}과 같은 모양이지만 별도로 둔다 — presentation 클래스는
 * 모듈 경계를 넘지 않는다(ARCH-02, docs/03 §2.2).
 */
public record SelfAssessmentUpdateRequest(
        @NotNull @Size(min = 1, max = 14) List<@NotNull @Valid CategoryLevel> assessments) {

    public SelfAssessmentUpdateRequest {
        assessments = assessments == null ? List.of() : List.copyOf(assessments);
    }

    /** 카테고리 자기평가 한 칸. {@code level}은 {@code SkillLevel} ordinal 0~5. */
    public record CategoryLevel(
            @NotNull SkillCategory category, @NotNull @Min(0) @Max(5) Integer level) {}
}
