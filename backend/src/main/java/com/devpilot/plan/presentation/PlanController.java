package com.devpilot.plan.presentation;

import com.devpilot.common.idempotency.IdempotencyService;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.web.CursorPage;
import com.devpilot.plan.application.BudgetView;
import com.devpilot.plan.application.MilestoneView;
import com.devpilot.plan.application.PlanCommandService;
import com.devpilot.plan.application.PlanQueryService;
import com.devpilot.plan.application.PlanSummaryView;
import com.devpilot.plan.application.PlanView;
import com.devpilot.plan.application.ReplanService;
import com.devpilot.plan.application.StudyBudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학습 계획 (docs/05 §7.2~§7.9). {@code /plans/active}·{@code /plans/active/budget}은 리터럴 경로라 {@code
 * /plans/{planId}}보다 먼저 매칭된다. 타 사용자 plan은 404 {@code PLAN_NOT_FOUND}다. replan preview는 저장하지 않으므로
 * {@code Idempotency-Key}가 선택이다. {@code POST /plans}는 Later(BL-GOL-16)다.
 */
@RestController
@RequestMapping("/api/v1/plans")
@Tag(name = "plan")
public class PlanController {

    private final PlanQueryService planQueryService;
    private final PlanCommandService planCommandService;
    private final ReplanService replanService;
    private final StudyBudgetService studyBudgetService;
    private final IdempotencyService idempotencyService;

    public PlanController(
            PlanQueryService planQueryService,
            PlanCommandService planCommandService,
            ReplanService replanService,
            StudyBudgetService studyBudgetService,
            IdempotencyService idempotencyService) {
        this.planQueryService = planQueryService;
        this.planCommandService = planCommandService;
        this.replanService = replanService;
        this.studyBudgetService = studyBudgetService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/active")
    @Operation(operationId = "planGetActive")
    public PlanView getActive(CurrentUser currentUser) {
        return planQueryService.getActive(currentUser.userId());
    }

    @GetMapping
    @Operation(operationId = "planList")
    public CursorPage<PlanSummaryView> list(
            CurrentUser currentUser,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) @Nullable @Size(max = 512) String cursor) {
        return planQueryService.list(currentUser.userId(), limit, cursor);
    }

    @GetMapping("/{planId}")
    @Operation(operationId = "planGet")
    public PlanView get(CurrentUser currentUser, @PathVariable UUID planId) {
        return planQueryService.get(currentUser.userId(), planId);
    }

    @PatchMapping("/{planId}/milestones/{milestoneId}")
    @Operation(operationId = "planUpdateMilestone")
    public MilestoneView updateMilestone(
            CurrentUser currentUser,
            @PathVariable UUID planId,
            @PathVariable UUID milestoneId,
            @Valid @RequestBody MilestonePatchRequest request) {
        return planCommandService.updateMilestone(
                currentUser.userId(), planId, milestoneId, request.toCommand());
    }

    @GetMapping("/active/budget")
    @Operation(operationId = "planGetActiveBudget")
    public BudgetView getActiveBudget(CurrentUser currentUser) {
        return studyBudgetService.budget(currentUser);
    }

    /**
     * 미리보기는 저장하지 않으므로 {@code Idempotency-Key}를 받아도 무시하고 record를 만들지 않는다(docs/05 §1.7). 헤더는 OpenAPI에
     * 선택으로만 나타난다.
     */
    @PostMapping("/{planId}/replan/preview")
    @Operation(operationId = "planPreviewReplan")
    public ReplanPreviewResponse previewReplan(
            CurrentUser currentUser,
            @RequestHeader(value = IdempotencyService.HEADER, required = false)
                    @Nullable String idempotencyKey,
            @PathVariable UUID planId,
            @Validated(Default.class) @RequestBody ReplanRequest request) {
        return ReplanPreviewResponse.from(
                replanService.preview(currentUser, planId, request.toCommand()));
    }

    @PostMapping("/{planId}/replan")
    @Operation(operationId = "planReplan")
    public ResponseEntity<ReplanCommitResponse> replan(
            CurrentUser currentUser,
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @PathVariable UUID planId,
            @Validated({Default.class, ReplanRequest.Commit.class}) @RequestBody
                    ReplanRequest request,
            HttpServletRequest httpRequest) {
        return idempotencyService.execute(
                currentUser.userId(),
                idempotencyKey,
                httpRequest,
                request,
                ReplanCommitResponse.class,
                () ->
                        ResponseEntity.status(HttpStatus.CREATED)
                                .body(
                                        ReplanCommitResponse.from(
                                                replanService.replan(
                                                        currentUser,
                                                        planId,
                                                        request.toCommand()))));
    }
}
