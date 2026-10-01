package com.devpilot.skill.application;

import com.devpilot.skill.domain.SkillCategory;
import com.devpilot.skill.domain.UserSkillState;
import com.devpilot.skill.infrastructure.UserSkillStateRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자기평가 수정 (docs/05 §6.5). 온보딩에서 한 번 적고 끝이던 값을 나중에 고칠 수 있게 한다 — 그러지 못하면 "Spring을 2로 적었는데 사실 1이었다"를
 * 깨달은 사람이 진도 전체를 초기화해야 한다.
 *
 * <p>규칙 두 가지를 지킨다.
 *
 * <ul>
 *   <li><b>적은 category만 바꾼다.</b> 전체 교체가 아니다 — 한 칸을 고치려고 열네 칸을 다시 보내게 하면 나머지를 실수로 덮어쓴다
 *   <li><b>{@code self_assessment_active}는 건드리지 않는다.</b> 진단 실패로 꺼진 분야는 꺼진 채로 둔다(docs/06 §7.4). 값을
 *       다시 적어 부정적 증거를 지울 수 있게 하면 자기평가가 증거를 덮어쓰게 되고, 규칙은 그 반대다(docs/06 §7.5)
 * </ul>
 *
 * <p>레벨(evidence)은 바꾸지 않는다. planning level은 다음 조회에서 docs/06 §7.5로 다시 계산된다.
 */
@Service
public class SelfAssessmentRevisionService {

    private final UserSkillStateRepository userSkillStateRepository;
    private final SkillCatalogQueryService skillCatalogQueryService;

    SelfAssessmentRevisionService(
            UserSkillStateRepository userSkillStateRepository,
            SkillCatalogQueryService skillCatalogQueryService) {
        this.userSkillStateRepository = userSkillStateRepository;
        this.skillCatalogQueryService = skillCatalogQueryService;
    }

    /**
     * 적은 category의 skill state에 새 자기평가 값을 쓴다.
     *
     * @return 실제로 값이 바뀐 행 수
     */
    @Transactional
    public int revise(UUID userId, Map<SkillCategory, Integer> levels) {
        if (levels.isEmpty()) {
            return 0;
        }
        Map<UUID, SkillInfo> skills = skillCatalogQueryService.activeSkillDetails();
        List<UserSkillState> states = userSkillStateRepository.findByUserId(userId);
        int revised = 0;
        for (UserSkillState state : states) {
            SkillInfo skill = skills.get(state.getSkillId());
            if (skill == null) {
                continue;
            }
            Integer level = levels.get(skill.category());
            if (level == null) {
                continue;
            }
            state.reviseSelfAssessment(level);
            revised++;
        }
        return revised;
    }
}
