package com.devpilot.review.application;

import com.devpilot.common.web.AiMeta;
import com.devpilot.common.web.AiMeta.GuardActionView;
import com.devpilot.integration.ai.AiGateway;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.AiRequest;
import com.devpilot.integration.ai.api.AiResult;
import com.devpilot.integration.ai.api.GuardContext;
import com.devpilot.integration.ai.api.PromptValue;
import com.devpilot.integration.ai.api.TruncateMode;
import com.devpilot.integration.ai.api.Truncation;
import com.devpilot.integration.ai.api.UserContentBlock;
import com.devpilot.integration.ai.api.output.ReviewEvaluateOutput;
import com.devpilot.integration.ai.api.output.RubricMetOutput;
import com.devpilot.learning.application.RubricScoringService;
import com.devpilot.learning.domain.EvaluatedOutcome;
import com.devpilot.learning.domain.RubricScorer.Score;
import com.devpilot.learning.domain.RubricScorer.ScoredCriterion;
import com.devpilot.review.domain.ReviewType;
import com.devpilot.review.domain.RubricItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 복습 답변의 AI 평가 (docs/05 §11.3 3~4단계, docs/17 §3.7, BL-MEM-09). 동기이고 대체가 있다 — 실패하면 답변은 그대로 저장하고
 * {@code evaluationSkippedReason}만 채운다. 호출은 트랜잭션 밖에서만 한다(T-2).
 *
 * <p>복습 rubric은 가중치가 없으므로 {@link RubricScoringService#evenWeights(int)}로 균등 배분한다(docs/06 §8.1).
 */
@Component
public class ReviewEvaluationSupport {

    private static final Truncation ANSWER_TEXT = Truncation.of(1, TruncateMode.TAIL_CHARS, 500);

    private final AiGateway aiGateway;
    private final RubricScoringService rubricScorer;

    public ReviewEvaluationSupport(AiGateway aiGateway, RubricScoringService rubricScorer) {
        this.aiGateway = aiGateway;
        this.rubricScorer = rubricScorer;
    }

    /** {@code REVIEW_EVALUATE} (docs/17 §3.7). 트랜잭션 밖에서 부른다. */
    public AiResult<ReviewEvaluateOutput> evaluate(EvaluationInput input) {
        Map<String, PromptValue> variables = new LinkedHashMap<>();
        variables.put("reviewType", PromptValue.of(input.reviewType().name()));
        variables.put("presentedPrompt", PromptValue.of(input.presentedPrompt()));
        variables.put("expectedAnswer", PromptValue.of(input.expectedAnswer()));
        variables.put("rubric", PromptValue.list(rubricLines(input.rubric()), "(없음)"));
        Set<String> rubricIds = new HashSet<>();
        input.rubric().forEach(item -> rubricIds.add(item.id()));
        return aiGateway.call(
                AiRequest.of(
                        AiOperation.REVIEW_EVALUATE,
                        variables,
                        List.of(
                                UserContentBlock.text(
                                        "answerText", input.answerText(), ANSWER_TEXT)),
                        ReviewEvaluateOutput.class,
                        input.userId(),
                        new GuardContext(0, rubricIds, null, Set.of())));
    }

    /** 판정 → coverage·outcome (docs/06 §8.1 복습 rubric 균등 배분). */
    public Evaluation score(List<RubricItem> rubric, ReviewEvaluateOutput output) {
        Map<String, Boolean> met = new LinkedHashMap<>();
        for (RubricMetOutput judgement : output.rubric()) {
            met.put(judgement.id(), Boolean.TRUE.equals(judgement.met()));
        }
        List<Integer> weights = rubricScorer.evenWeights(rubric.size());
        List<ScoredCriterion> criteria = new ArrayList<>();
        List<RubricResult> results = new ArrayList<>();
        for (int index = 0; index < rubric.size(); index++) {
            RubricItem item = rubric.get(index);
            boolean matched = Boolean.TRUE.equals(met.get(item.id()));
            criteria.add(new ScoredCriterion(item.id(), weights.get(index), null, matched));
            results.add(new RubricResult(item.id(), item.criterion(), matched));
        }
        Score score = rubricScorer.score(criteria);
        return new Evaluation(
                score.evaluatedOutcome(),
                score.rubricCoverageBp(),
                List.copyOf(results),
                output.feedback());
    }

    /** 방금 한 호출의 {@code aiMeta} (docs/05 §1.9.5). */
    public static AiMeta meta(AiResult<?> result) {
        return new AiMeta(
                result.model(),
                result.promptVersionLabel(AiOperation.REVIEW_EVALUATE),
                result.guardActions().stream()
                        .map(
                                action ->
                                        new GuardActionView(
                                                action.guard(), action.action(), action.detail()))
                        .toList());
    }

    private static List<String> rubricLines(List<RubricItem> rubric) {
        return rubric.stream().map(item -> item.id() + " " + item.criterion()).toList();
    }

    /** 평가 입력 (docs/17 §3.7 표). {@code answerText}는 마스킹본이다. */
    public record EvaluationInput(
            UUID userId,
            ReviewType reviewType,
            String presentedPrompt,
            String expectedAnswer,
            List<RubricItem> rubric,
            String answerText) {

        public EvaluationInput {
            rubric = List.copyOf(rubric);
        }
    }

    /** 평가 결과. {@code rubricResults}·{@code feedback}은 저장하지 않고 응답에만 쓴다(docs/05 §11.3 4단계). */
    public record Evaluation(
            EvaluatedOutcome evaluatedOutcome,
            int rubricCoverageBp,
            List<RubricResult> rubricResults,
            String feedback) {

        public Evaluation {
            rubricResults = List.copyOf(rubricResults);
        }
    }

    /** rubric 판정 1건 (docs/05 §11.3 {@code ReviewRubricResultView}). */
    public record RubricResult(String id, String criterion, boolean met) {}
}
