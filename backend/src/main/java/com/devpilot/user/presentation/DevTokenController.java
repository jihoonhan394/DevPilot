package com.devpilot.user.presentation;

import com.devpilot.user.application.DevTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개발 토큰 endpoint (docs/05 §1.4.5). {@code auth-mode = devtoken}일 때만 등록된다. 인증·IK·온보딩 가드 밖이다. 안전성은
 * "tailnet 밖에서 닿을 수 없다"는 배포 전제에만 기대므로 공개 배포 전에 제거·교체한다(docs/03 §4.2).
 */
@RestController
@RequestMapping("/api/v1/dev")
@Tag(name = "dev")
@SecurityRequirements
@ConditionalOnProperty(
        prefix = "devpilot.security",
        name = "auth-mode",
        havingValue = "devtoken",
        matchIfMissing = true)
public class DevTokenController {

    private static final String TOKEN_TYPE = "Bearer";

    private final DevTokenService devTokenService;

    public DevTokenController(DevTokenService devTokenService) {
        this.devTokenService = devTokenService;
    }

    @PostMapping("/token")
    @Operation(operationId = "devIssueToken")
    public DevTokenResponse issueToken(@Valid @RequestBody DevTokenRequest request) {
        DevTokenService.IssuedDevToken token = devTokenService.issue(request.email());
        return new DevTokenResponse(token.accessToken(), TOKEN_TYPE, token.expiresAt());
    }

    @GetMapping("/jwks.json")
    @Operation(operationId = "devJwks")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(devTokenService.publicJwks());
    }
}
