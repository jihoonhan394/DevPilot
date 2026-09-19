package com.devpilot.learning.application;

import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.learning.infrastructure.LearningEventRepository;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 학습 이벤트 조회 (docs/03 §3.2). 다른 모듈(today·skill)이 규칙 입력으로 쓴다. 무효화된 이벤트는 제외한다(docs/04 §6). */
@Service
@Transactional(readOnly = true)
public class LearningEventQueryService {

    private final LearningEventRepository learningEventRepository;

    public LearningEventQueryService(LearningEventRepository learningEventRepository) {
        this.learningEventRepository = learningEventRepository;
    }

    /** {@code plan_date ≥ from}에 이 종류 이벤트가 있는 skill (예: 최근 1 plan-day의 {@code LEECH_DETECTED}). */
    public Set<UUID> skillsWithEventSince(
            UUID userId, LearningEventType eventType, LocalDate from) {
        return Set.copyOf(
                learningEventRepository.findSkillIdsWithEventSince(userId, eventType, from));
    }
}
