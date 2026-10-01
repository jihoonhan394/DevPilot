package com.devpilot.skill.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.common.time.UserTimeSettingsProvider;
import com.devpilot.common.time.UserTimeSettingsProvider.UserTimeSettings;
import com.devpilot.learning.application.LearningEventQueryService;
import com.devpilot.learning.application.LearningEventQueryService.RecordedEventView;
import com.devpilot.learning.domain.LearningEventRecorded;
import com.devpilot.skill.domain.RuleEvent;
import com.devpilot.skill.domain.SkillLevelRules;
import com.devpilot.skill.domain.SkillLevelRules.LevelChange;
import com.devpilot.skill.domain.SkillLevelRules.Outcome;
import com.devpilot.skill.domain.SkillLevelRules.RuleInput;
import com.devpilot.skill.domain.SkillStateChange;
import com.devpilot.skill.domain.SkillStateChange.LevelTransition;
import com.devpilot.skill.domain.UserSkillState;
import com.devpilot.skill.infrastructure.SkillStateChangeRepository;
import com.devpilot.skill.infrastructure.UserSkillStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code user_skill_state}의 유일한 쓰기 경로 (docs/03 §3.2, I-12, BL-SKL-05). 온보딩 초기화와, 학습 이벤트가 기록될 때마다
 * 동기로 도는 레벨 규칙({@link SkillLevelRules}, docs/06 §7)을 담당한다.
 *
 * <p>{@link LearningEventRecorded}를 같은 트랜잭션에서 받는다({@code @EventListener}). {@code skill_id}가 null인
 * 이벤트는 무시한다(docs/06 §7.1).
 */
@Service
public class SkillStateUpdater {

    private final UserSkillStateRepository userSkillStateRepository;
    private final SkillStateChangeRepository skillStateChangeRepository;
    private final LearningEventQueryService learningEventQueryService;
    private final UserTimeSettingsProvider userTimeSettingsProvider;
    private final Clock clock;
    private final SkillLevelRules rules;

    public SkillStateUpdater(
            UserSkillStateRepository userSkillStateRepository,
            SkillStateChangeRepository skillStateChangeRepository,
            LearningEventQueryService learningEventQueryService,
            UserTimeSettingsProvider userTimeSettingsProvider,
            Clock clock,
            DevPilotProperties properties) {
        this.userSkillStateRepository = userSkillStateRepository;
        this.skillStateChangeRepository = skillStateChangeRepository;
        this.learningEventQueryService = learningEventQueryService;
        this.userTimeSettingsProvider = userTimeSettingsProvider;
        this.clock = clock;
        this.rules = new SkillLevelRules(SkillRuleSettings.levelRules(properties));
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

    /** 학습 이벤트 → 레벨 규칙 (docs/06 §7.1). 기록 트랜잭션 안에서 동기로 돈다. */
    @EventListener
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void onLearningEventRecorded(LearningEventRecorded event) {
        UUID skillId = event.skillId();
        if (skillId == null) {
            return;
        }
        apply(event.userId(), skillId, event.eventId());
    }

    /** 사용자·skill 하나를 다시 평가한다. 바뀐 축 수를 돌려준다. */
    @Transactional
    public int apply(UUID userId, UUID skillId, @Nullable UUID triggerEventId) {
        Instant now = clock.instant();
        UserTimeSettings time = userTimeSettingsProvider.timeSettings(userId);
        LocalDate today = PlanDayCalculator.planDate(now, time.zoneId(), time.dayStartHour());
        List<RuleEvent> events =
                learningEventQueryService
                        .recentForSkill(userId, skillId, rules.windowStart(now))
                        .stream()
                        .map(SkillStateUpdater::toRuleEvent)
                        .toList();
        UserSkillState state =
                userSkillStateRepository
                        .findByUserIdAndSkillId(userId, skillId)
                        .orElseGet(
                                () ->
                                        userSkillStateRepository.save(
                                                UserSkillState.forRules(userId, skillId)));
        Outcome outcome =
                rules.evaluate(
                        new RuleInput(
                                state.getEvidenceLevels(),
                                events,
                                triggerEventId,
                                state.changedAtByAxis(),
                                today,
                                now));
        for (LevelChange change : outcome.changes()) {
            state.applyLevelChange(change.axis(), change.toLevel(), now);
            skillStateChangeRepository.save(
                    SkillStateChange.record(
                            userId,
                            skillId,
                            new LevelTransition(
                                    change.axis(), change.fromLevel(), change.toLevel()),
                            change.ruleCode(),
                            change.evidenceEventIds(),
                            now));
        }
        if (outcome.deactivateSelfAssessment()) {
            state.deactivateSelfAssessment();
        }
        state.refreshEvidence(outcome.lastPracticedAt(), outcome.evidenceCount());
        userSkillStateRepository.flush();
        return outcome.changes().size();
    }

    private static RuleEvent toRuleEvent(RecordedEventView view) {
        return new RuleEvent(
                view.id(), view.eventType(), view.planDate(), view.occurredAt(), view.payload());
    }

    /** skill 하나의 초기값. 진단 모드면 {@code selfAssessedLevel = null}. */
    public record InitialSkillState(UUID skillId, @Nullable Integer selfAssessedLevel) {}
}
