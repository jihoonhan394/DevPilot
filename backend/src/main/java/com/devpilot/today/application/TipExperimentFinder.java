package com.devpilot.today.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.today.domain.DailyTip;
import com.devpilot.today.infrastructure.UserDailyTipRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

/**
 * "해 볼게요"로 표시한 팁의 실험 후보 1건 (docs/06 §5.12 TIP-6, docs/05 §8.1).
 *
 * <p>과제가 아니다. planner의 점수·제안 분기·시간 배분 어디에도 들어가지 않고 {@code learning_task}도 만들지 않는다 — 계획을 흔들지 않고 남는
 * 시간에 해 볼 것 하나만 눈에 띄게 둔다.
 *
 * <p>{@code TodayQueryService}가 이 컴포넌트를 쓴다. 반대로 {@code DailyTipService}는 {@code TodayQueryService}를
 * 쓰므로 여기서 {@code DailyTipService}를 부르면 순환이 된다 — 저장소와 registry만 본다.
 */
@Component
class TipExperimentFinder {

    /** 후보를 훑는 깊이. 첫 번째가 본문 없는 팁일 수 있어 여유를 둔다. */
    private static final Limit SCAN = Limit.of(20);

    private final UserDailyTipRepository userDailyTipRepository;
    private final DailyTipRegistry dailyTipRegistry;
    private final DevPilotProperties properties;

    TipExperimentFinder(
            UserDailyTipRepository userDailyTipRepository,
            DailyTipRegistry dailyTipRegistry,
            DevPilotProperties properties) {
        this.userDailyTipRepository = userDailyTipRepository;
        this.dailyTipRegistry = dailyTipRegistry;
        this.properties = properties;
    }

    /**
     * {@code shown_on DESC → tip_key ASC}로 첫 번째. 표시한 그날은 빠진다(저장소 조건).
     *
     * <p>{@code experiment} 본문이 없는 팁은 건너뛴다 — 해 볼 것이 적혀 있지 않으면 보여 줄 것이 없다.
     */
    Optional<TipExperimentView> find(UUID userId, LocalDate today) {
        List<com.devpilot.today.domain.UserDailyTip> candidates =
                userDailyTipRepository.findExperimentCandidates(userId, today, SCAN);
        for (com.devpilot.today.domain.UserDailyTip candidate : candidates) {
            Optional<DailyTip> tip =
                    dailyTipRegistry
                            .find(candidate.getTipKey())
                            .filter(found -> found.experiment() != null);
            if (tip.isPresent()) {
                return tip.map(
                        found ->
                                new TipExperimentView(
                                        found.key(),
                                        found.title(),
                                        found.experiment(),
                                        properties.tips().experimentMinutes()));
            }
        }
        return Optional.empty();
    }
}
