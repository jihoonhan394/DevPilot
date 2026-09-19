package com.devpilot.today.presentation;

import com.devpilot.common.idempotency.IdempotencyService;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.today.application.TaskStatusView;
import com.devpilot.today.application.TodayPlanService;
import com.devpilot.today.application.TodayQueryService;
import com.devpilot.today.application.TodayView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 오늘 계획 (docs/05 §8.2~§8.4, BL-TDY-07·08·09). Today 기능은 AI를 호출하지 않는다(AC-12). 타 사용자 과제는 404 {@code
 * RESOURCE_NOT_FOUND}다.
 */
@RestController
@RequestMapping("/api/v1/today")
@Tag(name = "today")
public class TodayController {

    private final TodayPlanService todayPlanService;
    private final TodayQueryService todayQueryService;
    private final IdempotencyService idempotencyService;

    public TodayController(
            TodayPlanService todayPlanService,
            TodayQueryService todayQueryService,
            IdempotencyService idempotencyService) {
        this.todayPlanService = todayPlanService;
        this.todayQueryService = todayQueryService;
        this.idempotencyService = idempotencyService;
    }

    /** 생성·재생성 모두 200 (docs/05 §8.2). */
    @PostMapping("/generate")
    @Operation(operationId = "todayGenerate")
    public ResponseEntity<TodayView> generate(
            CurrentUser currentUser,
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody TodayGenerateRequest request,
            HttpServletRequest httpRequest) {
        return idempotencyService.execute(
                currentUser.userId(),
                idempotencyKey,
                httpRequest,
                request,
                TodayView.class,
                () ->
                        ResponseEntity.ok(
                                todayPlanService.generate(
                                        currentUser,
                                        request.availableMinutes(),
                                        request.energyLevel(),
                                        request.forceOrDefault())));
    }

    @GetMapping
    @Operation(operationId = "todayGet")
    public TodayView get(CurrentUser currentUser) {
        return todayQueryService.get(currentUser);
    }

    @PatchMapping("/tasks/{taskId}")
    @Operation(operationId = "todayUpdateTaskStatus")
    public TaskStatusView updateTaskStatus(
            CurrentUser currentUser,
            @PathVariable UUID taskId,
            @Valid @RequestBody TaskStatusPatchRequest request) {
        return todayPlanService.updateTaskStatus(
                currentUser.userId(), taskId, request.status(), request.version());
    }
}
