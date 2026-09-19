package com.devpilot.skill.application;

import com.devpilot.skill.domain.UserSkillState;
import com.devpilot.skill.infrastructure.UserSkillStateRepository;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code user_skill_state}의 유일한 쓰기 경로 (docs/03 §3.2, I-12). S1은 온보딩 초기화만 있고, 학습 이벤트 구독과 레벨 규칙
 * 적용({@code SkillLevelRules})은 S3(BL-SKL-05)에 붙는다.
 */
@Service
public class SkillStateUpdater {

    private final UserSkillStateRepository userSkillStateRepository;

    public SkillStateUpdater(UserSkillStateRepository userSkillStateRepository) {
        this.userSkillStateRepository = userSkillStateRepository;
    }

    /**
     * 온보딩 skill state 생성 (docs/05 §4.1 처리 5). 레벨 4축 0, {@code self_assessment_active = true}. 호출자
     * 트랜잭션에 참여한다.
     */
    @Transactional
    public void initializeForNewUser(UUID userId, List<InitialSkillState> states) {
        userSkillStateRepository.saveAll(
                states.stream()
                        .map(
                                state ->
                                        UserSkillState.initializeSelfAssessment(
                                                userId, state.skillId(), state.selfAssessedLevel()))
                        .toList());
    }

    /** skill 하나의 초기값. 진단 모드면 {@code selfAssessedLevel = null}. */
    public record InitialSkillState(UUID skillId, @Nullable Integer selfAssessedLevel) {}
}
