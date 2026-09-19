package com.devpilot.rubberduck.application;

import com.devpilot.common.web.AiMeta;
import com.devpilot.common.web.AiMeta.GuardActionView;
import com.devpilot.integration.ai.AiGateway;
import com.devpilot.integration.ai.api.AiCallMetaReader;
import com.devpilot.integration.ai.api.AiCallMetaReader.AiCallMeta;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.AiRequest;
import com.devpilot.integration.ai.api.AiResult;
import com.devpilot.integration.ai.api.GuardAction;
import com.devpilot.integration.ai.api.GuardContext;
import com.devpilot.integration.ai.api.PromptValue;
import com.devpilot.integration.ai.api.TruncateMode;
import com.devpilot.integration.ai.api.Truncation;
import com.devpilot.integration.ai.api.UserContentBlock;
import com.devpilot.integration.ai.api.output.RubberDuckSummaryOutput;
import com.devpilot.integration.ai.api.output.RubberDuckTurnOutput;
import com.devpilot.integration.ai.masking.SecretMasker;
import com.devpilot.rubberduck.domain.RubberDuckSession;
import com.devpilot.rubberduck.domain.RubberDuckTurn;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 러버덕의 AI 입력 구성·호출 (docs/17 §3.11·§3.12). 마스킹(RD-6)도 여기서 한다 — 저장·AI 전송 전에 한 번만 거치고 원문은 어디에도 남기지
 * 않는다. {@code AiGateway} 호출은 트랜잭션 밖에서만 한다(T-2는 gateway가 확인한다).
 */
@Component
class RubberDuckAiSupport {

    private static final String MASKING_SOURCE = "RUBBER_DUCK_TURN";
    private static final Truncation TARGET_SUMMARY = Truncation.of(3, TruncateMode.TAIL_CHARS, 600);
    private static final Truncation TURN_CONVERSATION =
            Truncation.of(2, TruncateMode.ITEMS_FROM_START, 2);
    private static final Truncation SUMMARY_CONVERSATION =
            Truncation.of(1, TruncateMode.ITEMS_FROM_START, 3);
    private static final Truncation EXPLANATION = Truncation.of(4, TruncateMode.TAIL_CHARS, 2_000);
    private static final String NONE = "(없음)";

    private final AiGateway aiGateway;
    private final SecretMasker secretMasker;
    private final AiCallMetaReader aiCallMetaReader;

    RubberDuckAiSupport(
            AiGateway aiGateway, SecretMasker secretMasker, AiCallMetaReader aiCallMetaReader) {
        this.aiGateway = aiGateway;
        this.secretMasker = secretMasker;
        this.aiCallMetaReader = aiCallMetaReader;
    }

    /** 설명 마스킹 (RD-6, docs/05 §1.11 4단계). private key면 422 {@code SECRET_DETECTED_BLOCKED}. */
    String mask(UUID userId, String explanation) {
        return secretMasker.maskOrReject(userId, MASKING_SOURCE, explanation);
    }

    /** 턴 질문 (docs/17 §3.11). 트랜잭션 밖에서 부른다. */
    AiResult<RubberDuckTurnOutput> askQuestion(TurnInput input) {
        Map<String, PromptValue> variables = commonVariables(input.context());
        List<UserContentBlock> userContent =
                List.of(
                        UserContentBlock.items(
                                "conversation", input.conversation(), TURN_CONVERSATION),
                        UserContentBlock.text(
                                "learnerExplanation", input.maskedExplanation(), EXPLANATION));
        return aiGateway.call(
                AiRequest.of(
                        AiOperation.RUBBER_DUCK,
                        variables,
                        userContent,
                        RubberDuckTurnOutput.class,
                        input.context().userId(),
                        GuardContext.empty()));
    }

    /** 세션 정리 (docs/17 §3.12). 트랜잭션 밖에서 부른다. */
    AiResult<RubberDuckSummaryOutput> summarize(SummaryInput input) {
        Map<String, PromptValue> variables = new LinkedHashMap<>(commonVariables(input.context()));
        variables.put(
                "availableSkillCodes",
                PromptValue.of(
                        input.availableSkillCodes().isEmpty()
                                ? NONE
                                : String.join(", ", input.availableSkillCodes())));
        List<UserContentBlock> userContent =
                List.of(
                        UserContentBlock.items(
                                "conversation", input.conversation(), SUMMARY_CONVERSATION));
        return aiGateway.call(
                AiRequest.of(
                        AiOperation.RUBBER_DUCK_SUMMARY,
                        variables,
                        userContent,
                        RubberDuckSummaryOutput.class,
                        input.context().userId(),
                        GuardContext.withSkillCodes(input.knownSkillCodes())));
    }

    /** 저장된 호출의 {@code aiMeta} (docs/05 §1.9.5). 행이 지워졌으면 null. */
    @Nullable AiMeta meta(@Nullable UUID aiCallId) {
        if (aiCallId == null) {
            return null;
        }
        return aiCallMetaReader.find(aiCallId).map(RubberDuckAiSupport::toMeta).orElse(null);
    }

    /** 여러 턴의 {@code aiMeta} (없는 id는 map에 없다). */
    Map<UUID, AiMeta> metas(Collection<UUID> aiCallIds) {
        Map<UUID, AiMeta> metas = new LinkedHashMap<>();
        aiCallMetaReader.findAll(aiCallIds).forEach((id, meta) -> metas.put(id, toMeta(meta)));
        return metas;
    }

    /** 방금 한 호출의 {@code aiMeta} (docs/05 §9.7·§9.8 응답). */
    static AiMeta meta(AiResult<?> result, AiOperation operation) {
        return new AiMeta(
                result.model(),
                result.promptVersionLabel(operation),
                result.guardActions().stream().map(RubberDuckAiSupport::toView).toList());
    }

    /** 대화 줄 (docs/17 §3.11 {@code conversation}): {@code 나: …} / {@code 질문: …}. */
    static List<String> conversation(List<RubberDuckTurn> turns) {
        List<String> lines = new ArrayList<>();
        for (RubberDuckTurn turn : turns) {
            lines.add("나: " + turn.getUserText());
            String question = turn.getAiQuestion();
            if (question != null) {
                lines.add("질문: " + question);
            }
        }
        return List.copyOf(lines);
    }

    private static Map<String, PromptValue> commonVariables(TargetContext context) {
        Map<String, PromptValue> variables = new LinkedHashMap<>();
        variables.put("targetType", PromptValue.of(context.targetType()));
        variables.put(
                "targetSummary",
                PromptValue.truncatable(
                        context.targetSummary().isBlank() ? NONE : context.targetSummary(),
                        TARGET_SUMMARY));
        variables.put("skillSummary", PromptValue.of(context.skillSummary()));
        return variables;
    }

    private static AiMeta toMeta(AiCallMeta meta) {
        return new AiMeta(
                meta.model(),
                meta.promptVersion(),
                meta.guardActions().stream().map(RubberDuckAiSupport::toView).toList());
    }

    private static GuardActionView toView(GuardAction action) {
        return new GuardActionView(action.guard(), action.action(), action.detail());
    }

    /**
     * 두 operation이 같이 쓰는 입력 (docs/17 §3.11 표).
     *
     * @param skillSummary {@code skill.code}·이름·planning level 4축. 없으면 {@code (없음)}
     */
    record TargetContext(
            UUID userId, String targetType, String targetSummary, String skillSummary) {}

    /** 턴 입력. {@code maskedExplanation}은 마스킹본이다(RD-6). */
    record TurnInput(TargetContext context, List<String> conversation, String maskedExplanation) {

        TurnInput {
            conversation = List.copyOf(conversation);
        }
    }

    /** 정리 입력. */
    record SummaryInput(
            TargetContext context,
            List<String> conversation,
            List<String> availableSkillCodes,
            Set<String> knownSkillCodes) {

        SummaryInput {
            conversation = List.copyOf(conversation);
            availableSkillCodes = List.copyOf(availableSkillCodes);
            knownSkillCodes = Set.copyOf(knownSkillCodes);
        }
    }

    /** 세션 요약 문구 (로그·디버그용이 아니라 prompt 변수 구성에 쓴다). */
    static String targetTypeOf(RubberDuckSession session) {
        return session.getTargetType().name();
    }
}
