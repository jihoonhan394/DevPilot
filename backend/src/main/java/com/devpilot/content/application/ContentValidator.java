package com.devpilot.content.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.content.domain.RawContent;
import com.devpilot.plan.application.PlanTemplateRegistry;
import com.devpilot.plan.domain.PlanTemplate;
import com.devpilot.plan.domain.PlanTemplatePlacement.DateSpan;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 콘텐츠 검증 (docs/19 §4, BL-CNT-01). 규칙 ID는 문서의 CV-xx 그대로이고 결과는 기준 검증기 {@code
 * content/tools/validate_content.py}와 같다. ERROR는 기동 실패, WARN은 로그다. CV-04(나열 안 된 파일)는 파일시스템 검사라
 * CI에서만 한다. SD-01·SD-02(DB 비교)는 적재 중에 한다.
 */
@Component
public class ContentValidator {

    /** CV-61 smoke test 기준일 (docs/19 §4.1, 기준 검증기와 같은 값). */
    private static final LocalDate SMOKE_TODAY = LocalDate.of(2026, 10, 1);

    private final PlanTemplateRegistry planTemplateRegistry;
    private final List<String> trustedSourceHosts;

    public ContentValidator(
            PlanTemplateRegistry planTemplateRegistry, DevPilotProperties properties) {
        this.planTemplateRegistry = planTemplateRegistry;
        this.trustedSourceHosts = properties.ai().trustedSourceHosts();
    }

    public ContentValidationReport validate(RawContent content) {
        ValidationContext context = new ValidationContext(content, trustedSourceHosts);
        if (!CatalogChecks.check(context)) {
            return context.report();
        }
        SkillTreeChecks.check(context);
        RoleTargetChecks.check(context);
        PlanTemplateChecks.check(context);
        ReviewCardChecks.check(context);
        ChallengeChecks.check(context);
        CuratedSourceChecks.check(context);
        CuratedRepoChecks.check(context);
        checkPlacementSmoke(context);
        return context.report();
    }

    /** CV-61: 고정 입력 4개에서 모든 milestone이 {@code today ≤ start ≤ end ≤ targetCompletionDate}. */
    private void checkPlacementSmoke(ValidationContext context) {
        List<LocalDate> targets =
                List.of(
                        SMOKE_TODAY,
                        SMOKE_TODAY.plusDays(9),
                        SMOKE_TODAY.plusDays(30),
                        SMOKE_TODAY.plusDays(210));
        for (Map<String, Object> document : context.validTemplates) {
            PlanTemplate template = CatalogMapping.template(document);
            for (LocalDate target : targets) {
                checkPlacement(context, template, target);
            }
        }
    }

    private void checkPlacement(
            ValidationContext context, PlanTemplate template, LocalDate target) {
        try {
            List<DateSpan> spans =
                    planTemplateRegistry.placeMilestones(template, SMOKE_TODAY, target);
            for (int i = 0; i < spans.size(); i++) {
                DateSpan span = spans.get(i);
                if (span.start().isBefore(SMOKE_TODAY)
                        || span.end().isBefore(span.start())
                        || span.end().isAfter(target)) {
                    context.error(
                            "CV-61",
                            template.templateKey(),
                            "placement out of range for "
                                    + template.milestones().get(i).key()
                                    + ": "
                                    + span.start()
                                    + ".."
                                    + span.end());
                }
            }
        } catch (IllegalArgumentException | ArithmeticException exception) {
            context.error(
                    "CV-61",
                    template.templateKey(),
                    "placement failed: " + exception.getClass().getSimpleName());
        }
    }
}
