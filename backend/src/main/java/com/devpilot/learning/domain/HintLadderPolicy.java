package com.devpilot.learning.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * Hint Ladder 판정 (docs/06 §9.1 HL-1~HL-6, vector §9.2). 순수 규칙 클래스다(ARCH-12) — 저장·AI 호출·이벤트는 {@code
 * HintService}가 한다. challenge attempt와 coach finding이 같은 판정을 쓴다.
 *
 * <p>검사 순서는 docs/05 §10.8 3~7단계 그대로다: HL-2 → HL-1 → HL-4 → HL-5 → HL-6.
 */
public final class HintLadderPolicy {

    /** 가장 낮은 AI 미사용 단계 (HL-6: 1~3단계는 사전 hint). */
    private static final Set<HintLevel> PREGENERATED_LEVELS =
            Set.of(HintLevel.QUESTION_ONLY, HintLevel.CONCEPT_HINT, HintLevel.DIRECTION);

    /** 판정 결과 종류. */
    public enum Outcome {
        /** HL-2 위반: 자기 설명 기록이 없다 → 409 {@code SELF_EXPLANATION_REQUIRED}. */
        SELF_EXPLANATION_REQUIRED,
        /** HL-1: 이미 공개한 단계 → 저장된 내용을 그대로 돌려준다. AI를 부르지 않는다. */
        RETURN_STORED,
        /** HL-4 위반: {@code PSEUDOCODE} 이상인데 확인이 없다 → 409 {@code HINT_CONFIRMATION_REQUIRED}. */
        CONFIRMATION_REQUIRED,
        /** HL-5 위반: {@code FULL_EXAMPLE} 조건 미충족 → 409 {@code FULL_EXAMPLE_NOT_ALLOWED}. */
        FULL_EXAMPLE_NOT_ALLOWED,
        /** HL-6: 대상의 사전 hint를 공개한다. */
        DISCLOSE_PREGENERATED,
        /** HL-6: AI로 만든다. */
        DISCLOSE_GENERATED
    }

    /** 요청 1건을 판정한다. */
    public Decision decide(HintRequestContext context) {
        HintLevel requested = context.requestedLevel();
        if (!context.selfExplanationRecorded()) {
            return Decision.of(Outcome.SELF_EXPLANATION_REQUIRED);
        }
        if (requested.ordinal() <= context.maxHintLevel().ordinal()) {
            HintLevel stored = storedLevelFor(requested, context.storedLevels());
            if (stored != null) {
                return new Decision(Outcome.RETURN_STORED, stored, List.of());
            }
        }
        if (requested.ordinal() >= HintLevel.PSEUDOCODE.ordinal()
                && !context.acknowledgeEvidenceImpact()) {
            return Decision.of(Outcome.CONFIRMATION_REQUIRED);
        }
        if (requested == HintLevel.FULL_EXAMPLE
                && context.submissionCount() == 0
                && !context.giveUp()) {
            return Decision.of(Outcome.FULL_EXAMPLE_NOT_ALLOWED);
        }
        List<HintLevel> skipped = skippedLevels(context.maxHintLevel(), requested);
        boolean pregenerated =
                PREGENERATED_LEVELS.contains(requested)
                        && context.pregeneratedLevels().contains(requested);
        return new Decision(
                pregenerated ? Outcome.DISCLOSE_PREGENERATED : Outcome.DISCLOSE_GENERATED,
                requested,
                skipped);
    }

    /**
     * HL-1 반환 단계: 요청 단계 이하에서 저장된 가장 높은 단계, 없으면 저장된 가장 낮은 단계 (docs/05 §10.8 4단계). 저장된 내용이 하나도 없으면
     * null이다.
     */
    static @Nullable HintLevel storedLevelFor(HintLevel requested, Set<HintLevel> stored) {
        if (stored.isEmpty()) {
            return null;
        }
        TreeSet<HintLevel> sorted = new TreeSet<>(stored);
        HintLevel atOrBelow = sorted.floor(requested);
        return atOrBelow == null ? sorted.first() : atOrBelow;
    }

    /** HL-3: 이전 max와 요청 단계 사이(양 끝 제외)의 단계. */
    static List<HintLevel> skippedLevels(HintLevel previousMax, HintLevel requested) {
        List<HintLevel> skipped = new ArrayList<>();
        for (int ordinal = previousMax.ordinal() + 1; ordinal < requested.ordinal(); ordinal++) {
            skipped.add(HintLevel.values()[ordinal]);
        }
        return List.copyOf(skipped);
    }

    /**
     * 요청 맥락.
     *
     * @param maxHintLevel 대상의 현재 {@code max_hint_level}
     * @param storedLevels 이 대상에 저장된 {@code hint_disclosure}의 단계 (HL-1)
     * @param pregeneratedLevels 대상이 가진 사전 hint 단계 (challenge {@code hints_json}. coach finding은 비어
     *     있다)
     * @param selfExplanationRecorded HL-2 선행 조건 충족 여부 (러버덕 턴 예외 포함, docs/05 §10.8 3단계)
     * @param submissionCount HL-5 판정에 쓰는 제출 횟수 (coach finding은 0)
     */
    public record HintRequestContext(
            HintLevel requestedLevel,
            HintLevel maxHintLevel,
            Set<HintLevel> storedLevels,
            Set<HintLevel> pregeneratedLevels,
            boolean selfExplanationRecorded,
            int submissionCount,
            boolean acknowledgeEvidenceImpact,
            boolean giveUp) {

        public HintRequestContext {
            Objects.requireNonNull(requestedLevel, "requestedLevel");
            Objects.requireNonNull(maxHintLevel, "maxHintLevel");
            storedLevels = Set.copyOf(storedLevels);
            pregeneratedLevels = Set.copyOf(pregeneratedLevels);
        }
    }

    /**
     * 판정.
     *
     * @param level 돌려주거나 새로 공개할 단계. 거절이면 null
     * @param skippedLevels HL-3 건너뛴 단계. 저장 내용 반환이면 빈 목록
     */
    public record Decision(
            Outcome outcome, @Nullable HintLevel level, List<HintLevel> skippedLevels) {

        public Decision {
            skippedLevels = List.copyOf(skippedLevels);
        }

        static Decision of(Outcome outcome) {
            return new Decision(outcome, null, List.of());
        }

        /** 공개할 단계. 거절 판정이면 {@link IllegalStateException}. */
        public HintLevel requireLevel() {
            if (level == null) {
                throw new IllegalStateException("hint decision has no level: " + outcome);
            }
            return level;
        }
    }
}
