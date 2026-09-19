package com.devpilot.integration.ai.api.output;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code RUBBER_DUCK} 출력 (docs/17 §4.10). {@code question}은 학습자에게 보인다. {@code targetsGap}은 보이지 않고
 * 저장·다음 턴 프롬프트에도 넣지 않는다. "모르겠다" 판정은 출력에 없다(서버 규칙, docs/06 §9.5 RD-3).
 */
public record RubberDuckTurnOutput(
        @NotBlank @Size(min = 10, max = 200) String question,
        @NotBlank @Size(min = 10, max = 60) String targetsGap) {}
