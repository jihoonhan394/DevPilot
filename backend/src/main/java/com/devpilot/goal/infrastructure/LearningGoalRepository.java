package com.devpilot.goal.infrastructure;

import com.devpilot.goal.domain.LearningGoal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 학습 목표 (docs/04 §2). 사용자당 1행(I-01). */
public interface LearningGoalRepository extends JpaRepository<LearningGoal, UUID> {

    Optional<LearningGoal> findByUserId(UUID userId);
}
