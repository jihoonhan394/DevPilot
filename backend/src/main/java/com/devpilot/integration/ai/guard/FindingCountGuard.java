package com.devpilot.integration.ai.guard;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.GuardContext;
import com.devpilot.integration.ai.api.output.CoachFindingOutput;
import com.devpilot.integration.ai.api.output.CoachReviewOutput;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * finding 개수·줄 범위 가드 (docs/17 §6.5, {@code COACH_REVIEW}만). 수정만 한다.
 *
 * <ol>
 *   <li>개수가 {@code devpilot.coach.max-findings}를 넘으면 정렬(findingType → confidence → startLine, null
 *       마지막 → 출력 순서) 후 앞 N개, {@code TRUNCATED}
 *   <li>줄 범위를 {@code contentLineCount}에 맞춘다: {@code NULLIFIED} 또는 {@code CORRECTED}
 *   <li>(category, startLine, endLine, summary.strip())이 같은 뒤 finding 제거, {@code REMOVED}
 * </ol>
 */
@Component
public class FindingCountGuard implements OutputGuard {

    private static final List<String> TYPE_ORDER = List.of("BUG", "RISK", "LEARNING_POINT");
    private static final List<String> CONFIDENCE_ORDER = List.of("HIGH", "MEDIUM", "LOW");

    private final int maxFindings;

    @Autowired
    public FindingCountGuard(DevPilotProperties properties) {
        this(properties.coach().maxFindings());
    }

    FindingCountGuard(int maxFindings) {
        this.maxFindings = maxFindings;
    }

    @Override
    public GuardName name() {
        return GuardName.FINDING_COUNT;
    }

    @Override
    public GuardOutcome apply(
            AiOperation operation, Object output, GuardContext context, boolean lastAttempt) {
        if (!(output instanceof CoachReviewOutput review)) {
            return GuardOutcome.unchanged(output);
        }
        GuardOutcome.Builder result = new GuardOutcome.Builder(name());
        List<CoachFindingOutput> findings = new ArrayList<>(review.findings());
        if (findings.size() > maxFindings) {
            List<Indexed> indexed = new ArrayList<>();
            for (int i = 0; i < findings.size(); i++) {
                indexed.add(new Indexed(i, findings.get(i)));
            }
            indexed.sort(ORDER);
            result.action("TRUNCATED", "findings " + findings.size() + "→" + maxFindings);
            findings =
                    new ArrayList<>(
                            indexed.subList(0, maxFindings).stream()
                                    .map(Indexed::finding)
                                    .toList());
        }
        List<CoachFindingOutput> normalized = new ArrayList<>();
        for (int i = 0; i < findings.size(); i++) {
            normalized.add(lines(findings.get(i), context.contentLineCount(), i, result));
        }
        List<CoachFindingOutput> unique = new ArrayList<>();
        Set<List<Object>> seen = new HashSet<>();
        for (int i = 0; i < normalized.size(); i++) {
            CoachFindingOutput finding = normalized.get(i);
            List<Object> key =
                    java.util.Arrays.asList(
                            finding.category(),
                            finding.startLine(),
                            finding.endLine(),
                            finding.summary().strip());
            if (seen.add(key)) {
                unique.add(finding);
            } else {
                result.action("REMOVED", "findings[" + i + "] duplicate");
            }
        }
        Object value =
                unique.equals(review.findings())
                        ? review
                        : new CoachReviewOutput(
                                review.confidentialSuspected(),
                                review.selfReviewAxes(),
                                review.incorrectClaims(),
                                unique);
        return result.build(value);
    }

    private static CoachFindingOutput lines(
            CoachFindingOutput finding, int lineCount, int index, GuardOutcome.Builder result) {
        @Nullable Integer start = finding.startLine();
        @Nullable Integer end = finding.endLine();
        if (start != null && start > lineCount) {
            start = null;
        }
        if (end != null && end > lineCount) {
            end = null;
        }
        if (start == null && end != null) {
            end = null;
        }
        if (start != null && end == null) {
            end = start;
        }
        if (start != null && start > end) {
            start = null;
            end = null;
        }
        if (Objects.equals(start, finding.startLine()) && Objects.equals(end, finding.endLine())) {
            return finding;
        }
        result.action(
                start == null ? "NULLIFIED" : "CORRECTED",
                "findings["
                        + index
                        + "] lines "
                        + finding.startLine()
                        + "-"
                        + finding.endLine()
                        + "→"
                        + start
                        + "-"
                        + end);
        return new CoachFindingOutput(
                finding.findingType(),
                finding.category(),
                finding.summary(),
                finding.learningQuestion(),
                start,
                end,
                finding.verificationStatus(),
                finding.confidence(),
                finding.sourceType(),
                finding.sourceReference(),
                finding.relatedSkillCode(),
                finding.mentionedByUser());
    }

    private static final Comparator<Indexed> ORDER =
            Comparator.<Indexed>comparingInt(
                            entry -> rank(TYPE_ORDER, entry.finding().findingType()))
                    .thenComparingInt(entry -> rank(CONFIDENCE_ORDER, entry.finding().confidence()))
                    .thenComparing(
                            entry -> entry.finding().startLine(),
                            Comparator.nullsLast(Comparator.<Integer>naturalOrder()))
                    .thenComparingInt(Indexed::index);

    private static int rank(List<String> order, String value) {
        int index = order.indexOf(value);
        return index < 0 ? order.size() : index;
    }

    private record Indexed(int index, CoachFindingOutput finding) {}
}
