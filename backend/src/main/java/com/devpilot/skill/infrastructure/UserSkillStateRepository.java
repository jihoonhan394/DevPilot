package com.devpilot.skill.infrastructure;

import com.devpilot.skill.domain.UserSkillState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 사용자별 skill 상태 (docs/04 §2). 조회 조건에 항상 {@code userId}가 있다(I-15). */
public interface UserSkillStateRepository extends JpaRepository<UserSkillState, UUID> {

    List<UserSkillState> findByUserId(UUID userId);

    /** 규칙 적용 대상 1행 (docs/06 §7.1). 없으면 규칙이 만든다. */
    Optional<UserSkillState> findByUserIdAndSkillId(UUID userId, UUID skillId);
}
