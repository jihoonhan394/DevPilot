package com.devpilot.training.application;

import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.skill.application.UserSkillStateQueryService;
import com.devpilot.skill.application.UserSkillStateQueryService.AssessmentState;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * attempt 결과가 skill 쪽에서 읽어 오는 것들 (docs/03 §2.2 모듈 간 질의). {@link AttemptViewAssembler}에서 떼어 냈다 — 생성자
 * 인자 수 한도 때문이고, 묶이는 이유도 하나다: <b>둘 다 "이 challenge가 묻는 skill"에 대한 질의</b>다.
 */
@Component
class AttemptSkillNotes {

    private final SkillCatalogQueryService skillCatalogQueryService;
    private final UserSkillStateQueryService userSkillStateQueryService;

    AttemptSkillNotes(
            SkillCatalogQueryService skillCatalogQueryService,
            UserSkillStateQueryService userSkillStateQueryService) {
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.userSkillStateQueryService = userSkillStateQueryService;
    }

    Map<UUID, SkillRef> refs(Collection<UUID> skillIds) {
        return skillCatalogQueryService.findRefs(skillIds);
    }

    /**
     * 자기평가가 거둬진 skill을 {@code code} ASC로 (docs/05 §10.6, ADR-063·064).
     *
     * <p>주장이 거둬지면 {@code 06} §7.5가 planning을 근거로 떨어뜨려 다음 과제의 난이도가 내려가고 예산이 늘어난다. 그 사실을 말하지 않으면 사용자는
     * <b>왜 갑자기 쉬워졌는지</b>도, <b>왜 목표일이 멀어졌는지</b>도 모른다.
     */
    List<SkillRef> claimWithdrawn(UUID userId, Collection<UUID> skillIds) {
        Map<UUID, AssessmentState> states = userSkillStateQueryService.assessmentStates(userId);
        return refs(skillIds).values().stream()
                .filter(ref -> withdrawn(states.get(ref.id())))
                .sorted(Comparator.comparing(SkillRef::code))
                .toList();
    }

    private static boolean withdrawn(@Nullable AssessmentState state) {
        return state != null && state.selfAssessedLevel() != null && !state.active();
    }
}
