package com.devpilot.skill.presentation;

import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.web.CursorPage;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillDetailQueryService;
import com.devpilot.skill.application.SkillDetailView;
import com.devpilot.skill.application.SkillHistoryQueryService;
import com.devpilot.skill.application.SkillStateChangeView;
import com.devpilot.skill.application.UserSkillStateQueryService;
import com.devpilot.skill.domain.TargetRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** skill catalog·내 skill state·레벨 이력·상세 (docs/05 §6.1~§6.4). */
@RestController
@RequestMapping("/api/v1/skills")
@Tag(name = "skill")
public class SkillController {

    private final SkillCatalogQueryService skillCatalogQueryService;
    private final UserSkillStateQueryService userSkillStateQueryService;
    private final SkillHistoryQueryService skillHistoryQueryService;
    private final SkillDetailQueryService skillDetailQueryService;

    public SkillController(
            SkillCatalogQueryService skillCatalogQueryService,
            UserSkillStateQueryService userSkillStateQueryService,
            SkillHistoryQueryService skillHistoryQueryService,
            SkillDetailQueryService skillDetailQueryService) {
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.userSkillStateQueryService = userSkillStateQueryService;
        this.skillHistoryQueryService = skillHistoryQueryService;
        this.skillDetailQueryService = skillDetailQueryService;
    }

    /** 공용 catalog. 온보딩 전에도 허용 (docs/05 §1.4.4). */
    @GetMapping("/tree")
    @Operation(operationId = "skillGetTree")
    public SkillTreeResponse getTree(@RequestParam(defaultValue = "JAVA_BACKEND") TargetRole role) {
        return SkillTreeResponse.from(skillCatalogQueryService.tree(role));
    }

    @GetMapping("/me")
    @Operation(operationId = "skillGetMyStates")
    public UserSkillStatesResponse getMyStates(CurrentUser currentUser) {
        return new UserSkillStatesResponse(
                userSkillStateQueryService.myStates(currentUser.userId()));
    }

    /**
     * skill 상세 + 학습 단계 6칸 (docs/05 §6.4). 비활성 skill도 열린다 — 지난 과제와 복습 카드가 가리키고 있다.
     *
     * <p>리터럴 경로 {@code /skills/tree}·{@code /skills/me}가 이 매핑보다 우선한다(Spring의 패턴 우선순위).
     */
    @GetMapping("/{skillId}")
    @Operation(operationId = "skillGet")
    public SkillDetailView getDetail(CurrentUser currentUser, @PathVariable UUID skillId) {
        return skillDetailQueryService.detail(currentUser.userId(), skillId);
    }

    /** 레벨 변경 이력 (docs/05 §6.3). 본인 이력만 보인다. */
    @GetMapping("/{skillId}/history")
    @Operation(operationId = "skillGetHistory")
    public CursorPage<SkillStateChangeView> getHistory(
            CurrentUser currentUser,
            @PathVariable UUID skillId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) @Nullable String cursor) {
        return skillHistoryQueryService.history(currentUser.userId(), skillId, limit, cursor);
    }
}
