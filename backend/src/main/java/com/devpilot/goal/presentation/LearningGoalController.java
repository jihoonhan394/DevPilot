package com.devpilot.goal.presentation;

import com.devpilot.common.security.CurrentUser;
import com.devpilot.goal.application.LearningGoalQueryService;
import com.devpilot.goal.application.LearningGoalService;
import com.devpilot.goal.application.LearningGoalView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 학습 목표 (docs/05 §5.1·§5.2, BL-GOL-01). 생성은 온보딩에서만 한다. */
@RestController
@RequestMapping("/api/v1/learning-goal")
@Tag(name = "goal")
public class LearningGoalController {

    private final LearningGoalQueryService learningGoalQueryService;
    private final LearningGoalService learningGoalService;

    public LearningGoalController(
            LearningGoalQueryService learningGoalQueryService,
            LearningGoalService learningGoalService) {
        this.learningGoalQueryService = learningGoalQueryService;
        this.learningGoalService = learningGoalService;
    }

    @GetMapping
    @Operation(operationId = "goalGetGoal")
    public LearningGoalView getGoal(CurrentUser currentUser) {
        return learningGoalQueryService.get(currentUser.userId());
    }

    @PutMapping
    @Operation(operationId = "goalUpdateGoal")
    public LearningGoalView updateGoal(
            CurrentUser currentUser, @Valid @RequestBody LearningGoalUpdateRequest request) {
        return learningGoalService.update(currentUser, request.toCommand(), request.version());
    }
}
