package com.devpilot.plan.presentation;

import com.devpilot.common.idempotency.IdempotencyService;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.web.CursorPage;
import com.devpilot.plan.application.MilestoneView;
import com.devpilot.plan.application.PlanCommandService;
import com.devpilot.plan.application.PlanQueryService;
import com.devpilot.plan.application.PlanSummaryView;
import com.devpilot.plan.application.PlanView;
import com.devpilot.plan.application.ReplanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * 학습 계획 (docs/05 §7.2~§7.4·§7.6·§7.8). {@code /plans/active}는 리터럴 경로라 {@code /plans/{planId}}보다 먼저
 * 매칭된다. 타 사용자 plan은 404 {@code PLAN_NOT_FOUND}다. {@code POST /plans}·replan preview·budget은
 * Later/S2다.
 */
@RestController
@RequestMapping("/api/v1/plans")
@Tag(name = "plan")
public class PlanController {

    private final PlanQueryService planQueryService;
    private final PlanCommandService planCommandService;
    private final ReplanService replanService;
    private final IdempotencyService idempotencyService;

    public PlanController(
            PlanQueryService planQueryService,
            PlanCommandService planCommandService,
            ReplanService replanService,
            IdempotencyService idempotencyService) {
        this.planQueryService = planQueryService;
        this.planCommandService = planCommandService;
        this.replanService = replanService;
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

    @PostMapping("/{planId}/replan")
    @Operation(operationId = "planReplan")
    public ResponseEntity<ReplanCommitResponse> replan(
            CurrentUser currentUser,
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @PathVariable UUID planId,
            @Valid @RequestBody ReplanRequest request,
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
