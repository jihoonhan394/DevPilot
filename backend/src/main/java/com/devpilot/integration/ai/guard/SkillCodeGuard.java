package com.devpilot.integration.ai.guard;

import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.GuardContext;
import com.devpilot.integration.ai.api.output.ChallengeGenerateOutput;
import com.devpilot.integration.ai.api.output.CoachFindingOutput;
import com.devpilot.integration.ai.api.output.CoachReviewOutput;
import com.devpilot.integration.ai.api.output.RequirementExtractOutput;
import com.devpilot.integration.ai.api.output.RequirementItemOutput;
import com.devpilot.integration.ai.api.output.RubberDuckGap;
import com.devpilot.integration.ai.api.output.RubberDuckSummaryOutput;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * skill code 가드 (docs/17 §6.6). 수정만 하고 위반을 내지 않는다. 비교는 대소문자를 구분하는 정확 일치이고 유사 코드로 고치지 않는다.
 *
 * <ul>
 *   <li>{@code COACH_REVIEW} {@code findings[].relatedSkillCode}, {@code REQUIREMENT_EXTRACT}
 *       {@code requirements[].suggestedSkillCode}: 모르는 코드 → null, {@code NULLIFIED}
 *   <li>{@code CHALLENGE_GENERATE} {@code targetSkillCodes[]}·{@code transferTargets[]}: 모르는 코드·중복
 *       제거, {@code REMOVED}
 *   <li>{@code RUBBER_DUCK_SUMMARY} {@code gaps[].conceptKey}: 접두사가 알려진 skill code가 아니면 그 gap을
 *       버린다({@code REMOVED}) — 정리 전체를 실패로 만들지 않는다(docs/17 §4.11)
 * </ul>
 */
@Component
public class SkillCodeGuard implements OutputGuard {

    @Override
    public GuardName name() {
        return GuardName.SKILL_CODE;
    }

    @Override
    public GuardOutcome apply(
            AiOperation operation, Object output, GuardContext context, boolean lastAttempt) {
        GuardOutcome.Builder result = new GuardOutcome.Builder(name());
        Set<String> known = context.knownSkillCodes();
        Object value =
                switch (output) {
                    case CoachReviewOutput review -> coachReview(review, known, result);
                    case RequirementExtractOutput extracted ->
                            requirements(extracted, known, result);
                    case ChallengeGenerateOutput generated -> challenge(generated, known, result);
                    case RubberDuckSummaryOutput summary -> summary(summary, known, result);
                    default -> output;
                };
        return result.build(value);
    }

    /** {@code conceptKey}가 알려진 skill code와 같거나 {@code code + "."}로 시작하는가. */
    public static boolean hasKnownPrefix(String conceptKey, Set<String> knownSkillCodes) {
        for (String code : knownSkillCodes) {
            if (conceptKey.equals(code) || conceptKey.startsWith(code + ".")) {
                return true;
            }
        }
        return false;
    }

    private static CoachReviewOutput coachReview(
            CoachReviewOutput review, Set<String> known, GuardOutcome.Builder result) {
        List<CoachFindingOutput> findings = new ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < review.findings().size(); i++) {
            CoachFindingOutput finding = review.findings().get(i);
            String code = finding.relatedSkillCode();
            if (code != null && !known.contains(code)) {
                result.action("NULLIFIED", "findings[" + i + "].relatedSkillCode");
                finding =
                        new CoachFindingOutput(
                                finding.findingType(),
                                finding.category(),
                                finding.summary(),
                                finding.learningQuestion(),
                                finding.startLine(),
                                finding.endLine(),
                                finding.verificationStatus(),
                                finding.confidence(),
                                finding.sourceType(),
                                finding.sourceReference(),
                                null,
                                finding.mentionedByUser());
                changed = true;
            }
            findings.add(finding);
        }
        return changed
                ? new CoachReviewOutput(
                        review.confidentialSuspected(),
                        review.selfReviewAxes(),
                        review.incorrectClaims(),
                        findings)
                : review;
    }

    private static RequirementExtractOutput requirements(
            RequirementExtractOutput extracted, Set<String> known, GuardOutcome.Builder result) {
        List<RequirementItemOutput> items = new ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < extracted.requirements().size(); i++) {
            RequirementItemOutput item = extracted.requirements().get(i);
            String code = item.suggestedSkillCode();
            if (code != null && !known.contains(code)) {
                result.action("NULLIFIED", "requirements[" + i + "].suggestedSkillCode");
                item = new RequirementItemOutput(item.rawText(), item.requirementType(), null);
                changed = true;
            }
            items.add(item);
        }
        return changed ? new RequirementExtractOutput(items) : extracted;
    }

    private static ChallengeGenerateOutput challenge(
            ChallengeGenerateOutput generated, Set<String> known, GuardOutcome.Builder result) {
        List<String> targets =
                filterCodes(generated.targetSkillCodes(), known, "targetSkillCodes", result);
        List<String> transfers =
                filterCodes(generated.transferTargets(), known, "transferTargets", result);
        if (targets.equals(generated.targetSkillCodes())
                && transfers.equals(generated.transferTargets())) {
            return generated;
        }
        return new ChallengeGenerateOutput(
                generated.title(),
                targets,
                generated.difficulty(),
                generated.estimatedMinutes(),
                generated.scenario(),
                generated.prompt(),
                generated.constraints(),
                generated.expectedConcepts(),
                generated.rubric(),
                generated.commonMistakes(),
                transfers,
                generated.hints());
    }

    private static List<String> filterCodes(
            List<String> codes, Set<String> known, String path, GuardOutcome.Builder result) {
        Set<String> kept = new LinkedHashSet<>();
        for (int i = 0; i < codes.size(); i++) {
            String code = codes.get(i);
            if (!known.contains(code) || !kept.add(code)) {
                result.action("REMOVED", path + "[" + i + "]");
            }
        }
        return List.copyOf(kept);
    }

    private static RubberDuckSummaryOutput summary(
            RubberDuckSummaryOutput summary, Set<String> known, GuardOutcome.Builder result) {
        List<RubberDuckGap> gaps = new ArrayList<>();
        for (int i = 0; i < summary.gaps().size(); i++) {
            RubberDuckGap gap = summary.gaps().get(i);
            if (hasKnownPrefix(gap.conceptKey(), known)) {
                gaps.add(gap);
            } else {
                result.action("REMOVED", "gaps[" + i + "] conceptKey prefix");
            }
        }
        return gaps.size() == summary.gaps().size()
                ? summary
                : new RubberDuckSummaryOutput(gaps, summary.confirmed(), summary.overallNote());
    }
}
