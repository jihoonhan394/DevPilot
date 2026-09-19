package com.devpilot.plan.application;

import com.devpilot.plan.domain.PlanTemplate;
import com.devpilot.plan.domain.PlanTemplatePlacement;
import com.devpilot.plan.domain.PlanTemplatePlacement.DateSpan;
import com.devpilot.plan.domain.PlanTemplatePlacement.PlacementInput;
import com.devpilot.skill.domain.TargetRole;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 검증을 통과한 plan template 보관소 (docs/03 §3.2, docs/19 §3.4 O-2). DB 테이블이 없고 {@code ContentSeeder}가 기동
 * 시 등록한다. 역할마다 템플릿이 정확히 1개다(CV-30).
 */
@Component
public class PlanTemplateRegistry {

    private final Map<TargetRole, PlanTemplate> templates = new ConcurrentHashMap<>();
    private final PlanTemplatePlacement placement = new PlanTemplatePlacement();

    public void register(PlanTemplate template) {
        templates.put(template.targetRole(), template);
    }

    /** 등록되지 않았으면 콘텐츠 적재 실패이므로 {@link IllegalStateException}. */
    public PlanTemplate get(TargetRole role) {
        PlanTemplate template = templates.get(role);
        if (template == null) {
            throw new IllegalStateException("no plan template registered for " + role);
        }
        return template;
    }

    /**
     * 템플릿 milestone 날짜 배치 (docs/19 §5). {@code ContentValidator}의 CV-61 smoke test도 이 경로를 쓴다(다른 모듈은
     * 규칙 클래스를 직접 쓰지 않는다, ARCH-02).
     */
    public List<DateSpan> placeMilestones(
            PlanTemplate template, LocalDate today, LocalDate targetCompletionDate) {
        return placement.place(
                today,
                targetCompletionDate,
                template.milestones().stream()
                        .map(milestone -> new PlacementInput(milestone.weightBp()))
                        .toList(),
                template.minMilestoneDays());
    }
}
