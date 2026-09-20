package com.devpilot.skill.domain;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.learning.domain.HintLevel;
import com.devpilot.learning.domain.LearningEventType;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * skill 레벨 규칙 (docs/06 §7.1~§7.4, vector §7.6). 순수 규칙 클래스다(ARCH-12) — 입력은 60일 이벤트 payload와 현재 레벨뿐이고
 * 저장은 {@code SkillStateUpdater}가 한다.
 *
 * <pre>
 * 1. DIAGNOSTIC_* 이벤트면 §7.4를 적용하고 끝낸다 (1단계 제한·cooldown 없음)
 * 2. 축마다 하락 규칙(§7.3)을 먼저 본다. 적용되면 그 축의 상승은 건너뛴다
 * 3. 축마다 다음 레벨(현재 + 1) 상승 규칙 하나만 본다
 * 4. 해당 축의 마지막 변경이 cooldown 안이면 바꾸지 않는다 (하락·상승 모두)
 * </pre>
 */
public final class SkillLevelRules {

    /** 진단 통과 rule code (docs/06 §7.4). */
    public static final String DIAGNOSTIC_RULE_CODE = "DIAG_PASSED";

    private static final int MAX_EVIDENCE_EVENT_IDS = 10;
    private static final int RECALL_FAIL_WINDOW_DAYS = 14;
    private static final int E2_COVERAGE_BP = 4_000;
    private static final int E3_COVERAGE_BP = 7_000;
    private static final int E4_COVERAGE_BP = 8_000;
    private static final int E5_COVERAGE_BP = 8_000;

    private final Settings settings;

    public SkillLevelRules(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** 규칙 1회 적용. 바꿀 것이 없으면 변경 목록이 비어 있다. */
    public Outcome evaluate(RuleInput input) {
        List<RuleEvent> events = input.events();
        RuleEvent diagnostic = firstOf(events, event -> isDiagnostic(event.eventType()));
        if (diagnostic != null) {
            return diagnosticOutcome(input, diagnostic);
        }
        List<LevelChange> changes = new ArrayList<>();
        boolean deactivate = false;
        for (SkillAxis axis : SkillAxis.values()) {
            LevelChange down = decline(input, axis);
            if (down != null) {
                deactivate = true;
                if (!inCooldown(input, axis)) {
                    changes.add(down);
                }
                continue;
            }
            if (inCooldown(input, axis)) {
                continue;
            }
            LevelChange up = rise(input, axis);
            if (up != null) {
                changes.add(up);
            }
        }
        return new Outcome(changes, deactivate, lastPracticedAt(events), evidenceCount(events));
    }

    // ---------------------------------------------------------------- 진단 (§7.4)

    private Outcome diagnosticOutcome(RuleInput input, RuleEvent event) {
        List<RuleEvent> events = input.events();
        if (event.eventType() == LearningEventType.DIAGNOSTIC_FAILED) {
            return new Outcome(List.of(), true, lastPracticedAt(events), evidenceCount(events));
        }
        Integer claimed = event.number("claimedLevel");
        Integer difficulty = event.number("difficulty");
        int basis = claimed != null ? claimed : (difficulty == null ? 0 : difficulty);
        int target = Math.min(basis, settings.diagnosticMaxLevel());
        List<LevelChange> changes = new ArrayList<>();
        addDiagnosticChange(
                changes, SkillAxis.KNOWLEDGE, input.current().knowledge(), target, event);
        addDiagnosticChange(
                changes, SkillAxis.IMPLEMENTATION, input.current().implementation(), target, event);
        return new Outcome(changes, false, lastPracticedAt(events), evidenceCount(events));
    }

    private static void addDiagnosticChange(
            List<LevelChange> changes, SkillAxis axis, int current, int target, RuleEvent event) {
        int next = Math.max(current, target);
        if (next != current) {
            changes.add(
                    new LevelChange(
                            axis, current, next, DIAGNOSTIC_RULE_CODE, List.of(event.id())));
        }
    }

    private static boolean isDiagnostic(LearningEventType type) {
        return type == LearningEventType.DIAGNOSTIC_PASSED
                || type == LearningEventType.DIAGNOSTIC_FAILED;
    }

    // ---------------------------------------------------------------- 하락 (§7.3)

    private @Nullable LevelChange decline(RuleInput input, SkillAxis axis) {
        return switch (axis) {
            case KNOWLEDGE -> knowledgeDecline(input);
            case IMPLEMENTATION -> implementationDecline(input);
            case DEBUGGING -> debuggingDecline(input);
            case EXPLANATION -> null;
        };
    }

    /**
     * {@code K_DOWN_RECALL_FAIL}: 가장 최근 {@code REVIEW_ANSWERED} 2개가 모두 AGAIN이고 최근 14 plan-day 안.
     */
    private @Nullable LevelChange knowledgeDecline(RuleInput input) {
        int current = input.current().knowledge();
        if (current < 3) {
            return null;
        }
        List<RuleEvent> answers =
                take(
                        input.events(),
                        event -> event.eventType() == LearningEventType.REVIEW_ANSWERED,
                        2);
        if (answers.size() < 2) {
            return null;
        }
        LocalDate windowStart = input.today().minusDays(RECALL_FAIL_WINDOW_DAYS - 1L);
        for (RuleEvent answer : answers) {
            if (!"AGAIN".equals(answer.text("finalRating"))
                    || answer.planDate().isBefore(windowStart)) {
                return null;
            }
        }
        return new LevelChange(
                SkillAxis.KNOWLEDGE,
                current,
                Math.max(2, current - 1),
                "K_DOWN_RECALL_FAIL",
                ids(answers));
    }

    /** {@code I_DOWN_TRANSFER_FAIL}: 방금 기록한 이벤트가 전이 challenge 실패. */
    private @Nullable LevelChange implementationDecline(RuleInput input) {
        int current = input.current().implementation();
        UUID triggerId = input.triggerEventId();
        if (current < 3 || triggerId == null) {
            return null;
        }
        RuleEvent trigger = firstOf(input.events(), event -> event.id().equals(triggerId));
        if (trigger == null
                || trigger.eventType() != LearningEventType.CHALLENGE_EVALUATED
                || !trigger.flag("isTransfer")
                || !"FAILED".equals(trigger.text("outcome"))) {
            return null;
        }
        return new LevelChange(
                SkillAxis.IMPLEMENTATION,
                current,
                Math.max(2, current - 1),
                "I_DOWN_TRANSFER_FAIL",
                List.of(trigger.id()));
    }

    /** {@code D_DOWN_REPEATED_MISS}: 같은 axis의 가장 최근 finding 3개가 모두 {@code MISSED}. */
    private @Nullable LevelChange debuggingDecline(RuleInput input) {
        int current = input.current().debugging();
        if (current < 2) {
            return null;
        }
        Map<String, List<RuleEvent>> byAxis = new LinkedHashMap<>();
        for (RuleEvent event : input.events()) {
            if (event.eventType() != LearningEventType.COACH_FINDING_CLOSED) {
                continue;
            }
            String axis = event.text("axis");
            if (axis != null) {
                byAxis.computeIfAbsent(axis, key -> new ArrayList<>()).add(event);
            }
        }
        for (List<RuleEvent> findings : byAxis.values()) {
            if (findings.size() < 3) {
                continue;
            }
            List<RuleEvent> latest = findings.subList(0, 3);
            boolean allMissed =
                    latest.stream().allMatch(event -> "MISSED".equals(event.text("discoveredBy")));
            if (allMissed) {
                return new LevelChange(
                        SkillAxis.DEBUGGING,
                        current,
                        Math.max(1, current - 1),
                        "D_DOWN_REPEATED_MISS",
                        ids(latest));
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 상승 (§7.2)

    private @Nullable LevelChange rise(RuleInput input, SkillAxis axis) {
        int current = level(input.current(), axis);
        int next = current + 1;
        if (next > 5) {
            return null;
        }
        List<RuleEvent> events = input.events();
        return switch (axis) {
            case KNOWLEDGE -> knowledgeRise(events, current, next);
            case IMPLEMENTATION -> implementationRise(events, current, next);
            case EXPLANATION -> explanationRise(events, current, next);
            case DEBUGGING -> debuggingRise(events, current, next);
        };
    }

    /** K 축 상승 (docs/06 §7.2). 규칙 코드 하나에 메서드 하나다. */
    private static @Nullable LevelChange knowledgeRise(
            List<RuleEvent> events, int current, int next) {
        return switch (next) {
            case 1 -> knowledgeAnyEvent(events, current, next);
            case 2 -> knowledgeRecallGuided(events, current, next);
            case 3 -> knowledgeRecallIndependent(events, current, next);
            case 4 -> knowledgeRecallLong(events, current, next);
            case 5 -> knowledgeTransfer(events, current, next);
            default -> null;
        };
    }

    /** {@code K1_ANY_EVENT}: 이벤트가 하나라도 있으면 된다. */
    private static @Nullable LevelChange knowledgeAnyEvent(
            List<RuleEvent> events, int current, int next) {
        return events.isEmpty()
                ? null
                : change(SkillAxis.KNOWLEDGE, current, next, "K1_ANY_EVENT", events.subList(0, 1));
    }

    /** {@code K2_RECALL_GUIDED}: 힌트 CONCEPT_HINT 이하로 HARD 이상 복습 2건. */
    private static @Nullable LevelChange knowledgeRecallGuided(
            List<RuleEvent> events, int current, int next) {
        List<RuleEvent> matches =
                filter(
                        events,
                        event ->
                                event.eventType() == LearningEventType.REVIEW_ANSWERED
                                        && event.ratingAtLeast("finalRating", "HARD")
                                        && event.hintAtMost("hintLevel", HintLevel.CONCEPT_HINT));
        return matches.size() >= 2
                ? change(SkillAxis.KNOWLEDGE, current, next, "K2_RECALL_GUIDED", matches)
                : null;
    }

    /** {@code K3_RECALL_INDEPENDENT}: 독립 회상 3건, 서로 다른 plan-day 2일 이상, 직전 간격 4일 이상. */
    private static @Nullable LevelChange knowledgeRecallIndependent(
            List<RuleEvent> events, int current, int next) {
        List<RuleEvent> matches = independentRecalls(events);
        if (matches.size() < 3) {
            return null;
        }
        Set<LocalDate> dates = new LinkedHashSet<>();
        matches.forEach(event -> dates.add(event.planDate()));
        Integer intervalBefore = matches.getFirst().number("intervalBefore");
        boolean ready = dates.size() >= 2 && intervalBefore != null && intervalBefore >= 4;
        return ready
                ? change(SkillAxis.KNOWLEDGE, current, next, "K3_RECALL_INDEPENDENT", matches)
                : null;
    }

    /** {@code K4_RECALL_LONG}: 직전 간격 14일 이상인 독립 회상 2건. */
    private static @Nullable LevelChange knowledgeRecallLong(
            List<RuleEvent> events, int current, int next) {
        List<RuleEvent> matches =
                filter(
                        independentRecalls(events),
                        event -> atLeast(event.number("intervalBefore"), 14));
        return matches.size() >= 2
                ? change(SkillAxis.KNOWLEDGE, current, next, "K4_RECALL_LONG", matches)
                : null;
    }

    /** {@code K5_TRANSFER}: 난이도 5 challenge를 혼자 해결. */
    private static @Nullable LevelChange knowledgeTransfer(
            List<RuleEvent> events, int current, int next) {
        List<RuleEvent> matches =
                filter(
                        events,
                        event ->
                                event.eventType() == LearningEventType.CHALLENGE_EVALUATED
                                        && equals(event.number("difficulty"), 5)
                                        && "SOLVED_INDEPENDENTLY".equals(event.text("outcome")));
        return matches.isEmpty()
                ? null
                : change(SkillAxis.KNOWLEDGE, current, next, "K5_TRANSFER", matches);
    }

    /** I 축 상승 (docs/06 §7.2). 규칙 코드 하나에 메서드 하나다. */
    private static @Nullable LevelChange implementationRise(
            List<RuleEvent> events, int current, int next) {
        return switch (next) {
            case 1 -> implementationAttempted(events, current, next);
            case 2 -> implementationSolvedGuided(events, current, next);
            case 3 -> implementationSolvedIndependent(events, current, next);
            case 4 -> implementationProductionLike(events, current, next);
            case 5 -> implementationTransfer(events, current, next);
            default -> null;
        };
    }

    /** {@code I1_ATTEMPTED}: 제출이 하나라도 있으면 된다. */
    private static @Nullable LevelChange implementationAttempted(
            List<RuleEvent> events, int current, int next) {
        List<RuleEvent> matches =
                filter(events, event -> event.eventType() == LearningEventType.CHALLENGE_SUBMITTED);
        return matches.isEmpty()
                ? null
                : change(SkillAxis.IMPLEMENTATION, current, next, "I1_ATTEMPTED", matches);
    }

    /** {@code I2_SOLVED_GUIDED}: 힌트 PARTIAL_CODE 이하로 해결. */
    private static @Nullable LevelChange implementationSolvedGuided(
            List<RuleEvent> events, int current, int next) {
        List<RuleEvent> matches =
                filter(
                        events,
                        event ->
                                solvedChallenge(event)
                                        && event.hintAtMost(
                                                "maxHintLevel", HintLevel.PARTIAL_CODE));
        return matches.isEmpty()
                ? null
                : change(SkillAxis.IMPLEMENTATION, current, next, "I2_SOLVED_GUIDED", matches);
    }

    /** {@code I3_SOLVED_INDEPENDENT}: 난이도 2 이상을 혼자 해결 3건, 서로 다른 challenge 2개 이상. */
    private static @Nullable LevelChange implementationSolvedIndependent(
            List<RuleEvent> events, int current, int next) {
        List<RuleEvent> matches =
                filter(
                        events,
                        event ->
                                event.eventType() == LearningEventType.CHALLENGE_EVALUATED
                                        && "SOLVED_INDEPENDENTLY".equals(event.text("outcome"))
                                        && atLeast(event.number("difficulty"), 2));
        return matches.size() >= 3 && distinct(matches, "challengeId") >= 2
                ? change(SkillAxis.IMPLEMENTATION, current, next, "I3_SOLVED_INDEPENDENT", matches)
                : null;
    }

    /** {@code I4_PRODUCTION_LIKE}: 난이도 4 이상을 CONCEPT_HINT 이하로 해결 + 증거 채택. */
    private static @Nullable LevelChange implementationProductionLike(
            List<RuleEvent> events, int current, int next) {
        List<RuleEvent> matches =
                filter(
                        events,
                        event ->
                                solvedChallenge(event)
                                        && atLeast(event.number("difficulty"), 4)
                                        && event.hintAtMost(
                                                "maxHintLevel", HintLevel.CONCEPT_HINT));
        List<RuleEvent> evidence =
                filter(events, event -> event.eventType() == LearningEventType.EVIDENCE_ACCEPTED);
        return matches.isEmpty() || evidence.isEmpty()
                ? null
                : change(
                        SkillAxis.IMPLEMENTATION,
                        current,
                        next,
                        "I4_PRODUCTION_LIKE",
                        concat(matches, evidence));
    }

    /** {@code I5_TRANSFER}: 난이도 5를 혼자 해결한 서로 다른 challenge 2개 이상 + 전이 challenge 포함. */
    private static @Nullable LevelChange implementationTransfer(
            List<RuleEvent> events, int current, int next) {
        List<RuleEvent> matches =
                filter(
                        events,
                        event ->
                                event.eventType() == LearningEventType.CHALLENGE_EVALUATED
                                        && "SOLVED_INDEPENDENTLY".equals(event.text("outcome"))
                                        && equals(event.number("difficulty"), 5));
        boolean transfer = matches.stream().anyMatch(event -> event.flag("isTransfer"));
        return distinct(matches, "challengeId") >= 2 && transfer
                ? change(SkillAxis.IMPLEMENTATION, current, next, "I5_TRANSFER", matches)
                : null;
    }

    /** E 축 상승 (docs/06 §7.2). 규칙 코드 하나에 메서드 하나다. */
    private @Nullable LevelChange explanationRise(List<RuleEvent> events, int current, int next) {
        return switch (next) {
            case 1 -> explanationAny(events, current, next);
            case 2 -> explanationPartial(events, current, next);
            case 3 -> explanationCoverage(events, current, next);
            case 4 -> explanationTransferQuestion(events, current, next);
            case 5 -> explanationTradeoff(events, current, next);
            default -> null;
        };
    }

    /** {@code E1_ANY_EXPLANATION}: 자기 설명·EXPLAIN 복습·러버덕 중 무엇이든 1건. */
    private static @Nullable LevelChange explanationAny(
            List<RuleEvent> events, int current, int next) {
        List<RuleEvent> matches =
                filter(
                        events,
                        event ->
                                event.eventType() == LearningEventType.SELF_EXPLANATION_SUBMITTED
                                        || (event.eventType() == LearningEventType.REVIEW_ANSWERED
                                                && "EXPLAIN".equals(event.text("reviewType")))
                                        || event.eventType()
                                                == LearningEventType.RUBBER_DUCK_COMPLETED);
        return matches.isEmpty()
                ? null
                : change(SkillAxis.EXPLANATION, current, next, "E1_ANY_EXPLANATION", matches);
    }

    /** {@code E2_PARTIAL}: coverage 40%(bp) 이상 설명 증거 1건. */
    private @Nullable LevelChange explanationPartial(
            List<RuleEvent> events, int current, int next) {
        List<Evidence> matches = explanationEvidence(events, E2_COVERAGE_BP, false);
        return matches.isEmpty()
                ? null
                : change(
                        SkillAxis.EXPLANATION,
                        current,
                        next,
                        "E2_PARTIAL",
                        evidenceEvents(matches));
    }

    /** {@code E3_COVERAGE}: coverage 70%(bp) 이상 독립 설명 증거 2건. */
    private @Nullable LevelChange explanationCoverage(
            List<RuleEvent> events, int current, int next) {
        List<Evidence> matches = explanationEvidence(events, E3_COVERAGE_BP, true);
        return matches.size() >= 2
                ? change(
                        SkillAxis.EXPLANATION,
                        current,
                        next,
                        "E3_COVERAGE",
                        evidenceEvents(matches))
                : null;
    }

    /** {@code E4_TRANSFER_QUESTION}: coverage 80%(bp) 이상 증거 + 변형 문항 정답. */
    private @Nullable LevelChange explanationTransferQuestion(
            List<RuleEvent> events, int current, int next) {
        List<Evidence> matches = explanationEvidence(events, E4_COVERAGE_BP, false);
        List<RuleEvent> variants =
                filter(
                        events,
                        event ->
                                event.eventType() == LearningEventType.REVIEW_ANSWERED
                                        && event.flag("wasVariant")
                                        && "CORRECT".equals(event.text("evaluatedOutcome")));
        return matches.isEmpty() || variants.isEmpty()
                ? null
                : change(
                        SkillAxis.EXPLANATION,
                        current,
                        next,
                        "E4_TRANSFER_QUESTION",
                        concat(evidenceEvents(matches), variants));
    }

    /** {@code E5_TRADEOFF}: 난이도 5 challenge의 설명 coverage 80%(bp) 이상. */
    private static @Nullable LevelChange explanationTradeoff(
            List<RuleEvent> events, int current, int next) {
        List<RuleEvent> matches =
                filter(
                        events,
                        event ->
                                event.eventType() == LearningEventType.CHALLENGE_EVALUATED
                                        && equals(event.number("difficulty"), 5)
                                        && atLeast(
                                                event.number("explanationCoverageBp"),
                                                E5_COVERAGE_BP));
        return matches.isEmpty()
                ? null
                : change(SkillAxis.EXPLANATION, current, next, "E5_TRADEOFF", matches);
    }

    private @Nullable LevelChange debuggingRise(List<RuleEvent> events, int current, int next) {
        return switch (next) {
            case 1 -> {
                List<RuleEvent> matches =
                        filter(
                                events,
                                event ->
                                        event.eventType()
                                                        == LearningEventType.COACH_REVIEW_COMPLETED
                                                || event.eventType()
                                                        == LearningEventType.COACH_FINDING_CLOSED);
                yield matches.isEmpty()
                        ? null
                        : change(SkillAxis.DEBUGGING, current, next, "D1_ANY_REVIEW", matches);
            }
            case 2 -> {
                List<RuleEvent> matches =
                        filter(
                                events,
                                event ->
                                        event.eventType() == LearningEventType.COACH_FINDING_CLOSED
                                                && event.textIn(
                                                        "discoveredBy",
                                                        "FOUND_AFTER_HINT",
                                                        "MENTIONED_UNPROMPTED")
                                                && event.hintAtMost(
                                                        "maxHintLevel", HintLevel.DIRECTION));
                yield matches.isEmpty()
                        ? null
                        : change(SkillAxis.DEBUGGING, current, next, "D2_FOUND_WITH_HINT", matches);
            }
            case 3 -> {
                List<RuleEvent> matches = unpromptedFindings(events);
                yield matches.size() >= 2
                        ? change(SkillAxis.DEBUGGING, current, next, "D3_FOUND_UNPROMPTED", matches)
                        : null;
            }
            case 4 -> {
                List<RuleEvent> matches = unpromptedFindings(events);
                boolean highConfidence =
                        matches.stream().anyMatch(event -> "HIGH".equals(event.text("confidence")));
                yield matches.size() >= 3
                                && distinct(matches, "coachReviewId") >= 2
                                && highConfidence
                        ? change(
                                SkillAxis.DEBUGGING,
                                current,
                                next,
                                "D4_REPEATED_UNPROMPTED",
                                matches)
                        : null;
            }
            case 5 -> {
                List<RuleEvent> matches = unpromptedFindings(events);
                yield distinct(matches, "axis") >= 3
                        ? change(SkillAxis.DEBUGGING, current, next, "D5_TRANSFER", matches)
                        : null;
            }
            default -> null;
        };
    }

    // ---------------------------------------------------------------- 보조

    /** 설명 증거 (docs/06 §7.2 "설명 증거와 coverage"). */
    private List<Evidence> explanationEvidence(
            List<RuleEvent> events, int minCoverageBp, boolean independentOnly) {
        List<Evidence> evidence = new ArrayList<>();
        for (RuleEvent event : events) {
            Evidence candidate = explanationEvidenceOf(event);
            if (candidate != null
                    && candidate.coverageBp() >= minCoverageBp
                    && (!independentOnly || candidate.independent())) {
                evidence.add(candidate);
            }
        }
        return List.copyOf(evidence);
    }

    /** 이벤트 1건을 설명 증거로 본다. 증거가 아니면 null. */
    private @Nullable Evidence explanationEvidenceOf(RuleEvent event) {
        return switch (event.eventType()) {
            case CHALLENGE_EVALUATED -> challengeExplanationEvidence(event);
            case REVIEW_ANSWERED -> reviewExplanationEvidence(event);
            case RUBBER_DUCK_COMPLETED -> rubberDuckExplanationEvidence(event);
            default -> null;
        };
    }

    /** challenge 평가의 {@code explanationCoverageBp}. 힌트 QUESTION_ONLY 이하면 독립이다. */
    private static @Nullable Evidence challengeExplanationEvidence(RuleEvent event) {
        Integer coverage = event.number("explanationCoverageBp");
        return coverage == null
                ? null
                : new Evidence(
                        event, coverage, event.hintAtMost("maxHintLevel", HintLevel.QUESTION_ONLY));
    }

    /** {@code EXPLAIN} 복습 답변의 {@code rubricCoverageBp}. 힌트 QUESTION_ONLY 이하면 독립이다. */
    private static @Nullable Evidence reviewExplanationEvidence(RuleEvent event) {
        Integer coverage = event.number("rubricCoverageBp");
        return coverage == null || !"EXPLAIN".equals(event.text("reviewType"))
                ? null
                : new Evidence(
                        event, coverage, event.hintAtMost("hintLevel", HintLevel.QUESTION_ONLY));
    }

    /** 공백 없이 3턴 이상 끝낸 러버덕. coverage는 설정값 고정이고 힌트를 열지 않았으면 독립이다. */
    private @Nullable Evidence rubberDuckExplanationEvidence(RuleEvent event) {
        Integer gapCount = event.number("gapCount");
        Integer turns = event.number("turns");
        return gapCount != null && gapCount == 0 && turns != null && turns >= 3
                ? new Evidence(event, settings.rubberDuckCoverageBp(), !event.flag("hintDisclosed"))
                : null;
    }

    private static List<RuleEvent> evidenceEvents(List<Evidence> evidence) {
        return evidence.stream().map(Evidence::event).toList();
    }

    /** D3 조건 이벤트 (docs/06 §7.2). D4·D5도 같은 집합을 쓴다. */
    private static List<RuleEvent> unpromptedFindings(List<RuleEvent> events) {
        return filter(
                events,
                event ->
                        event.eventType() == LearningEventType.COACH_FINDING_CLOSED
                                && "MENTIONED_UNPROMPTED".equals(event.text("discoveredBy"))
                                && event.textIn("findingType", "BUG", "RISK"));
    }

    private static List<RuleEvent> independentRecalls(List<RuleEvent> events) {
        return filter(
                events,
                event ->
                        event.eventType() == LearningEventType.REVIEW_ANSWERED
                                && event.ratingAtLeast("finalRating", "GOOD")
                                && event.hintAtMost("hintLevel", HintLevel.QUESTION_ONLY));
    }

    private static boolean solvedChallenge(RuleEvent event) {
        return event.eventType() == LearningEventType.CHALLENGE_EVALUATED
                && event.textIn("outcome", "SOLVED_INDEPENDENTLY", "SOLVED_WITH_HINTS");
    }

    private boolean inCooldown(RuleInput input, SkillAxis axis) {
        Instant changedAt = input.changedAt().get(axis);
        return changedAt != null
                && Duration.between(changedAt, input.now()).compareTo(settings.cooldown()) < 0;
    }

    private static LevelChange change(
            SkillAxis axis, int from, int to, String ruleCode, List<RuleEvent> evidence) {
        return new LevelChange(axis, from, to, ruleCode, ids(evidence));
    }

    private static List<UUID> ids(List<RuleEvent> events) {
        return events.stream().limit(MAX_EVIDENCE_EVENT_IDS).map(RuleEvent::id).toList();
    }

    private static List<RuleEvent> concat(List<RuleEvent> left, List<RuleEvent> right) {
        List<RuleEvent> all = new ArrayList<>(left);
        all.addAll(right);
        return List.copyOf(all);
    }

    private static List<RuleEvent> filter(List<RuleEvent> events, Predicate<RuleEvent> predicate) {
        return events.stream().filter(predicate).toList();
    }

    private static List<RuleEvent> take(
            List<RuleEvent> events, Predicate<RuleEvent> predicate, int count) {
        return events.stream().filter(predicate).limit(count).toList();
    }

    private static @Nullable RuleEvent firstOf(
            List<RuleEvent> events, Predicate<RuleEvent> predicate) {
        return events.stream().filter(predicate).findFirst().orElse(null);
    }

    private static int distinct(List<RuleEvent> events, String key) {
        Set<String> values = new LinkedHashSet<>();
        events.forEach(
                event -> {
                    String value = event.text(key);
                    if (value != null) {
                        values.add(value);
                    }
                });
        return values.size();
    }

    private static boolean atLeast(@Nullable Integer value, int minimum) {
        return value != null && value >= minimum;
    }

    private static boolean equals(@Nullable Integer value, int expected) {
        return value != null && value == expected;
    }

    private static int level(AxisLevels levels, SkillAxis axis) {
        return switch (axis) {
            case KNOWLEDGE -> levels.knowledge();
            case IMPLEMENTATION -> levels.implementation();
            case EXPLANATION -> levels.explanation();
            case DEBUGGING -> levels.debugging();
        };
    }

    private static @Nullable Instant lastPracticedAt(List<RuleEvent> events) {
        Instant latest = null;
        for (RuleEvent event : events) {
            if (latest == null || event.occurredAt().isAfter(latest)) {
                latest = event.occurredAt();
            }
        }
        return latest;
    }

    private static int evidenceCount(List<RuleEvent> events) {
        return (int)
                events.stream()
                        .filter(event -> event.eventType() == LearningEventType.EVIDENCE_ACCEPTED)
                        .count();
    }

    /** 윈도 시작 plan-day (docs/06 §7.1 {@code rule-window-days}). */
    public Instant windowStart(Instant now) {
        return now.minus(settings.ruleWindowDays(), ChronoUnit.DAYS);
    }

    /**
     * 규칙 입력.
     *
     * @param events 최근 {@code rule-window-days} 안의 무효화되지 않은 이벤트, <b>최신순</b>
     * @param triggerEventId 방금 기록한 이벤트 (I 하락 규칙). 없으면 null
     * @param changedAt 축별 마지막 변경 시각 (cooldown). 없으면 map에 없다
     */
    public record RuleInput(
            AxisLevels current,
            List<RuleEvent> events,
            @Nullable UUID triggerEventId,
            Map<SkillAxis, Instant> changedAt,
            LocalDate today,
            Instant now) {

        public RuleInput {
            events = List.copyOf(events);
            changedAt = Map.copyOf(changedAt);
        }
    }

    /**
     * 규칙 결과.
     *
     * @param deactivateSelfAssessment 하락·진단 실패면 true (docs/06 §7.3·§7.4)
     */
    public record Outcome(
            List<LevelChange> changes,
            boolean deactivateSelfAssessment,
            @Nullable Instant lastPracticedAt,
            int evidenceCount) {

        public Outcome {
            changes = List.copyOf(changes);
        }
    }

    /** 축 하나의 레벨 변경 1건. */
    public record LevelChange(
            SkillAxis axis,
            int fromLevel,
            int toLevel,
            String ruleCode,
            List<UUID> evidenceEventIds) {

        public LevelChange {
            evidenceEventIds = List.copyOf(evidenceEventIds);
        }
    }

    /** 설명 증거 1건 (docs/06 §7.2). */
    private record Evidence(RuleEvent event, int coverageBp, boolean independent) {}

    /**
     * 설정 ({@code devpilot.skill.*}, {@code devpilot.rubberduck.evidence-coverage-bp}).
     *
     * @param rubberDuckCoverageBp 러버덕 설명 증거의 고정 coverage (docs/06 §7.2)
     */
    public record Settings(
            int ruleWindowDays,
            Duration cooldown,
            int diagnosticMaxLevel,
            int rubberDuckCoverageBp) {}
}
