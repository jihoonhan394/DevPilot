package com.devpilot.integration.ai.guard;

import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.GuardContext;
import com.devpilot.integration.ai.api.output.ChallengeEvaluateOutput;
import com.devpilot.integration.ai.api.output.ChallengeGenerateOutput;
import com.devpilot.integration.ai.api.output.CoachFindingOutput;
import com.devpilot.integration.ai.api.output.CoachReviewOutput;
import com.devpilot.integration.ai.api.output.HintGenerateOutput;
import com.devpilot.integration.ai.api.output.IncorrectClaimOutput;
import com.devpilot.integration.ai.api.output.RequirementExtractOutput;
import com.devpilot.integration.ai.api.output.ReviewEvaluateOutput;
import com.devpilot.integration.ai.api.output.ReviewVariantOutput;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * enum 값 가드 (docs/17 §6.4). 출력 record의 enum 필드는 {@code String}이고 허용값은 docs/04 §3 레지스트리다 — {@code
 * integration.ai}는 도메인 enum을 참조할 수 없으므로 값을 여기 둔다(docs/17 §4.0). 레지스트리 밖 값은 위반(재시도), {@code
 * selfReviewAxes} 중복은 제거({@code REMOVED}).
 */
@Component
public class EnumGuard implements OutputGuard {

    static final Set<String> THINKING_AXIS =
            Set.of(
                    "CORRECTNESS",
                    "NULL_BOUNDARY",
                    "RESOURCE_LIFECYCLE",
                    "EXCEPTION_STRATEGY",
                    "SECURITY",
                    "PERFORMANCE",
                    "CONCURRENCY",
                    "OBSERVABILITY",
                    "MAINTAINABILITY",
                    "TRANSACTION_DATA_CONSISTENCY");
    static final Set<String> FINDING_TYPE = Set.of("BUG", "RISK", "LEARNING_POINT");
    static final Set<String> CONFIDENCE = Set.of("HIGH", "MEDIUM", "LOW");
    static final Set<String> VERIFICATION_STATUS =
            Set.of("VERIFIED", "SUPPORTED", "AI_JUDGMENT", "UNCERTAIN");
    static final Set<String> VERIFICATION_SOURCE_TYPE =
            Set.of(
                    "COMPILER",
                    "TEST_RESULT",
                    "STATIC_ANALYSIS",
                    "CURATED_SOURCE",
                    "OFFICIAL_DOC",
                    "SECURITY_GUIDE",
                    "AI_REASONING");
    static final Set<String> RUBRIC_AXIS = Set.of("IMPLEMENTATION", "EXPLANATION", "DEBUGGING");
    static final Set<String> REQUIREMENT_TYPE = Set.of("REQUIRED", "PREFERRED");
    static final List<String> HINT_LEVELS =
            List.of(
                    "SELF_EXPLAIN",
                    "QUESTION_ONLY",
                    "CONCEPT_HINT",
                    "DIRECTION",
                    "PSEUDOCODE",
                    "PARTIAL_CODE",
                    "FULL_EXAMPLE");

    static final String NOT_ALLOWED = "허용되지 않는 값";
    static final String RUBRIC_MISMATCH = "rubric id는 문항 rubric id와 정확히 같아야 한다";
    static final String RUBRIC_SEQUENCE = "rubric id는 R1부터 순서대로 연속이어야 한다";
    static final String HINT_LEVEL_MISMATCH = "level은 요청 단계와 같아야 한다";

    @Override
    public GuardName name() {
        return GuardName.ENUM;
    }

    @Override
    public GuardOutcome apply(
            AiOperation operation, Object output, GuardContext context, boolean lastAttempt) {
        GuardOutcome.Builder result = new GuardOutcome.Builder(name());
        Object value =
                switch (output) {
                    case CoachReviewOutput review -> coachReview(review, result);
                    case ChallengeGenerateOutput generated -> {
                        for (int i = 0; i < generated.rubric().size(); i++) {
                            check(
                                    generated.rubric().get(i).axis(),
                                    RUBRIC_AXIS,
                                    "rubric[" + i + "].axis",
                                    result);
                        }
                        yield generated;
                    }
                    case ChallengeEvaluateOutput evaluated -> {
                        rubricIds(
                                evaluated.rubric().stream().map(item -> item.id()).toList(),
                                context.expectedRubricIds(),
                                result);
                        yield evaluated;
                    }
                    case ReviewEvaluateOutput evaluated -> {
                        rubricIds(
                                evaluated.rubric().stream().map(item -> item.id()).toList(),
                                context.expectedRubricIds(),
                                result);
                        yield evaluated;
                    }
                    case ReviewVariantOutput variant -> {
                        for (int i = 0; i < variant.rubric().size(); i++) {
                            if (!("R" + (i + 1)).equals(variant.rubric().get(i).id())) {
                                result.violation("rubric[" + i + "].id", RUBRIC_SEQUENCE);
                            }
                        }
                        yield variant;
                    }
                    case HintGenerateOutput hint -> {
                        boolean known =
                                HINT_LEVELS.contains(hint.level())
                                        && !"SELF_EXPLAIN".equals(hint.level());
                        if (!known) {
                            result.violation("level", NOT_ALLOWED);
                        } else if (!Objects.equals(hint.level(), context.requestedHintLevel())) {
                            result.violation("level", HINT_LEVEL_MISMATCH);
                        }
                        yield hint;
                    }
                    case RequirementExtractOutput extracted -> {
                        for (int i = 0; i < extracted.requirements().size(); i++) {
                            check(
                                    extracted.requirements().get(i).requirementType(),
                                    REQUIREMENT_TYPE,
                                    "requirements[" + i + "].requirementType",
                                    result);
                        }
                        yield extracted;
                    }
                    default -> output;
                };
        return result.build(value);
    }

    private static CoachReviewOutput coachReview(
            CoachReviewOutput review, GuardOutcome.Builder result) {
        Set<String> axes = new LinkedHashSet<>();
        for (int i = 0; i < review.selfReviewAxes().size(); i++) {
            String axis = review.selfReviewAxes().get(i);
            check(axis, THINKING_AXIS, "selfReviewAxes[" + i + "]", result);
            if (!axes.add(axis)) {
                result.action("REMOVED", "selfReviewAxes[" + i + "] duplicate");
            }
        }
        for (int i = 0; i < review.incorrectClaims().size(); i++) {
            IncorrectClaimOutput claim = review.incorrectClaims().get(i);
            check(claim.axis(), THINKING_AXIS, "incorrectClaims[" + i + "].axis", result);
        }
        for (int i = 0; i < review.findings().size(); i++) {
            CoachFindingOutput finding = review.findings().get(i);
            String path = "findings[" + i + "]";
            check(finding.category(), THINKING_AXIS, path + ".category", result);
            check(finding.findingType(), FINDING_TYPE, path + ".findingType", result);
            check(finding.confidence(), CONFIDENCE, path + ".confidence", result);
            check(
                    finding.verificationStatus(),
                    VERIFICATION_STATUS,
                    path + ".verificationStatus",
                    result);
            check(finding.sourceType(), VERIFICATION_SOURCE_TYPE, path + ".sourceType", result);
        }
        if (axes.size() == review.selfReviewAxes().size()) {
            return review;
        }
        return new CoachReviewOutput(
                review.confidentialSuspected(),
                new ArrayList<>(axes),
                review.incorrectClaims(),
                review.findings());
    }

    private static void rubricIds(
            List<String> ids, Set<String> expected, GuardOutcome.Builder result) {
        Set<String> unique = new HashSet<>(ids);
        if (unique.size() != ids.size() || !unique.equals(expected)) {
            result.violation("rubric", RUBRIC_MISMATCH);
        }
    }

    private static void check(
            String value, Set<String> allowed, String path, GuardOutcome.Builder result) {
        if (!allowed.contains(value)) {
            result.violation(path, NOT_ALLOWED);
        }
    }
}
