package com.devpilot.onboarding.presentation;

import com.devpilot.common.security.CurrentUser;
import com.devpilot.onboarding.application.DiagnosticSuggestionService;
import com.devpilot.onboarding.application.DiagnosticSuggestionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 진단 challenge 제안 (docs/05 §4.2, BL-TRN-13). 제안은 저장하지 않는다. */
@RestController
@RequestMapping("/api/v1/diagnostics")
@Tag(name = "onboarding")
public class DiagnosticsController {

    private final DiagnosticSuggestionService diagnosticSuggestionService;

    public DiagnosticsController(DiagnosticSuggestionService diagnosticSuggestionService) {
        this.diagnosticSuggestionService = diagnosticSuggestionService;
    }

    @GetMapping("/suggestions")
    @Operation(operationId = "onboardingGetDiagnosticSuggestions")
    public DiagnosticSuggestionsResponse getSuggestions(CurrentUser currentUser) {
        return new DiagnosticSuggestionsResponse(
                diagnosticSuggestionService.suggest(currentUser.userId()));
    }

    /** 응답 (docs/05 §4.2). */
    public record DiagnosticSuggestionsResponse(List<DiagnosticSuggestionView> items) {

        public DiagnosticSuggestionsResponse {
            items = List.copyOf(items);
        }
    }
}
