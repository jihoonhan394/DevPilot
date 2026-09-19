package com.devpilot.onboarding.domain;

import com.devpilot.skill.domain.SkillCategory;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 온보딩 자기평가 전파 (docs/19 §9, docs/05 §4.1 처리 5). 역할 목표가 있는 활성 non-root skill마다 skill state 초기값을 만든다.
 * skill별 가감은 하지 않는다.
 *
 * <ul>
 *   <li>자기평가 모드({@code runDiagnostic = false}): 그 skill의 category 자기평가 값, 없으면 null
 *   <li>진단 모드({@code runDiagnostic = true}): 전부 null — 시작점은 진단 결과가 정한다(docs/06 §7.4)
 * </ul>
 */
public final class SelfAssessmentPropagation {

    public List<InitialState> propagate(
            List<TargetedSkill> skills,
            Map<SkillCategory, Integer> selfAssessments,
            boolean runDiagnostic) {
        Objects.requireNonNull(skills, "skills");
        Objects.requireNonNull(selfAssessments, "selfAssessments");
        return skills.stream()
                .map(
                        skill ->
                                new InitialState(
                                        skill.skillId(),
                                        runDiagnostic
                                                ? null
                                                : selfAssessments.get(skill.category())))
                .toList();
    }

    /** 전파 대상 skill. */
    public record TargetedSkill(UUID skillId, SkillCategory category) {}

    /** skill state 초기값. */
    public record InitialState(UUID skillId, @Nullable Integer selfAssessedLevel) {}
}
