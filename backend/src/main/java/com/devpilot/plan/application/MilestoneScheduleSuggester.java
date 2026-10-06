package com.devpilot.plan.application;

import com.devpilot.plan.application.ReplanCommand.MilestoneInput;
import com.devpilot.plan.domain.MilestoneRescheduler;
import com.devpilot.plan.domain.MilestoneRescheduler.Existing;
import com.devpilot.plan.domain.MilestoneStatus;
import com.devpilot.plan.domain.PlanTemplatePlacement;
import com.devpilot.plan.domain.PlanTemplatePlacement.DateSpan;
import com.devpilot.skill.domain.TargetRole;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 목표일에 맞춘 milestone 일정 제안 (docs/05 §7.7, docs/06 §11.5, ADR-067).
 *
 * <p>{@link MilestoneRescheduler}(순수 규칙)에 저장소에서 읽은 것만 넘겨 준다. {@code minMilestoneDays}는 계획 템플릿 콘텐츠에
 * 있으므로(`19` §5.1) 학습 트랙으로 템플릿을 찾아 꺼낸다.
 *
 * <p>{@link ReplanService}에서 떼어 냈다 — 생성자 인자 한도 때문이고, 묶이는 이유도 하나다: <b>둘 다 "날짜를 다시 배치하는 데 필요한
 * 것"</b>이다.
 */
@Component
class MilestoneScheduleSuggester {

    private final PlanTemplateRegistry templates;
    private final MilestoneRescheduler rescheduler =
            new MilestoneRescheduler(new PlanTemplatePlacement());

    MilestoneScheduleSuggester(PlanTemplateRegistry templates) {
        this.templates = templates;
    }

    /**
     * @param role 학습 목표의 트랙. null이면 제안하지 않는다 (템플릿을 고를 수 없다)
     * @param milestones 요청에 담긴 목록 — 사용자가 화면에서 고친 상태 그대로 본다
     * @return 입력을 {@code sortOrder} ASC로 정렬한 순서의 제안 날짜. 비었으면 <b>날짜로 답할 문제가 아니다</b>
     */
    Optional<List<MilestoneSchedule>> suggest(
            @Nullable TargetRole role,
            LocalDate today,
            LocalDate targetCompletionDate,
            List<MilestoneInput> milestones) {
        if (role == null || milestones.isEmpty()) {
            return Optional.empty();
        }
        List<MilestoneInput> ordered =
                milestones.stream()
                        .sorted(Comparator.comparingInt(MilestoneInput::sortOrder))
                        .toList();
        List<Existing> input =
                ordered.stream()
                        .map(
                                milestone ->
                                        new Existing(
                                                new DateSpan(
                                                        milestone.startDate(), milestone.endDate()),
                                                fixed(milestone.status())))
                        .toList();
        return rescheduler
                .reschedule(
                        today, targetCompletionDate, input, templates.get(role).minMilestoneDays())
                .map(spans -> toViews(ordered, spans));
    }

    /** 날짜를 그대로 둘 단계. 끝났거나 하는 중인 것을 옮기면 쌓인 기록과 어긋난다. {@code SKIPPED}도 지난 일이라 건드리지 않는다. */
    private static boolean fixed(MilestoneStatus status) {
        return status != MilestoneStatus.PLANNED;
    }

    private static List<MilestoneSchedule> toViews(
            List<MilestoneInput> ordered, List<DateSpan> spans) {
        List<MilestoneSchedule> views = new ArrayList<>(spans.size());
        for (int i = 0; i < spans.size(); i++) {
            MilestoneInput milestone = ordered.get(i);
            DateSpan span = spans.get(i);
            boolean changed =
                    !span.start().equals(milestone.startDate())
                            || !span.end().equals(milestone.endDate());
            views.add(
                    new MilestoneSchedule(
                            milestone.id(),
                            milestone.sortOrder(),
                            milestone.title(),
                            span.start(),
                            span.end(),
                            changed));
        }
        return List.copyOf(views);
    }
}
