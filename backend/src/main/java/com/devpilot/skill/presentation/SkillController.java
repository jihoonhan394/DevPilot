package com.devpilot.skill.presentation;

import com.devpilot.common.security.CurrentUser;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.UserSkillStateQueryService;
import com.devpilot.skill.domain.TargetRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** skill catalog·내 skill state (docs/05 §6.1·§6.2). */
@RestController
@RequestMapping("/api/v1/skills")
@Tag(name = "skill")
public class SkillController {

    private final SkillCatalogQueryService skillCatalogQueryService;
    private final UserSkillStateQueryService userSkillStateQueryService;

    public SkillController(
            SkillCatalogQueryService skillCatalogQueryService,
            UserSkillStateQueryService userSkillStateQueryService) {
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.userSkillStateQueryService = userSkillStateQueryService;
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
}
