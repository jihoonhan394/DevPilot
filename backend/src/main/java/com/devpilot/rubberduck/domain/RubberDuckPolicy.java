package com.devpilot.rubberduck.domain;

import java.util.List;
import java.util.Locale;

/**
 * 러버덕 규칙 RD-3~RD-5·RD-7 (docs/06 §9.5). 순수 규칙 클래스다(ARCH-12) — AI 출력에 맡기지 않고 서버가 결정한다.
 *
 * <pre>
 * RD-3 "모르겠다": 공백을 뺀 길이 &lt; dontKnowMaxChars 이고 dontKnowPhrases 중 하나를 대소문자 무시로 포함
 *      마지막 stuckTurnsBeforeHint턴이 모두 "모르겠다"이면 Hint Ladder를 제안한다
 * RD-4 턴 상한: IN_PROGRESS이고 turnCount &lt; maxTurns일 때만 턴을 받는다. 정리는 세션당 1회
 *      종료 시 턴이 0개면 ABANDONED(정리 AI 호출 없음), 1개 이상이면 COMPLETED
 * RD-5 설명 증거: rawGapCount == 0 이고 turnCount ≥ 3
 * RD-7 학습 이벤트: 세션 skill이 있을 때만 남긴다
 * </pre>
 */
public final class RubberDuckPolicy {

    /** RD-5: 설명 증거로 인정하는 최소 턴 수. */
    static final int EVIDENCE_MIN_TURNS = 3;

    private final Settings settings;

    public RubberDuckPolicy(Settings settings) {
        this.settings = settings;
    }

    /** RD-3 "모르겠다" 판정. 마스킹본을 그대로 넣는다(원문은 어디에도 없다, RD-6). */
    public boolean isDontKnow(String text) {
        String stripped = text.replaceAll("\\s", "");
        if (stripped.length() >= settings.dontKnowMaxChars()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return settings.dontKnowPhrases().stream()
                .anyMatch(phrase -> lower.contains(phrase.toLowerCase(Locale.ROOT)));
    }

    /**
     * RD-3: 마지막 {@code stuckTurnsBeforeHint}턴이 모두 "모르겠다"인가. {@code recentStuck}은 turn_no ASC이고 이번
     * 턴이 마지막이다. 턴 수가 임계값보다 적으면 항상 false다.
     */
    public boolean suggestHint(List<Boolean> recentStuck) {
        int required = settings.stuckTurnsBeforeHint();
        return recentStuck.size() >= required
                && recentStuck.subList(recentStuck.size() - required, recentStuck.size()).stream()
                        .allMatch(Boolean.TRUE::equals);
    }

    /** RD-4: 턴을 더 받을 수 있는가. */
    public boolean canSubmitTurn(RubberDuckStatus status, int turnCount) {
        return status == RubberDuckStatus.IN_PROGRESS && turnCount < settings.maxTurns();
    }

    /** RD-4: 종료 요청의 결과 상태. 턴이 0개면 정리 AI를 부르지 않고 {@code ABANDONED}로 끝낸다. */
    public RubberDuckStatus completionStatus(int turnCount) {
        return turnCount == 0 ? RubberDuckStatus.ABANDONED : RubberDuckStatus.COMPLETED;
    }

    /** RD-4: 정리 AI를 부를지 (턴이 1개 이상일 때만, 세션당 1회). */
    public boolean needsSummary(int turnCount) {
        return turnCount > 0;
    }

    /** RD-5: 가드 적용 전 gap이 0개이고 턴이 3 이상이면 EXPLANATION 증거다. */
    public boolean isExplanationEvidence(int rawGapCount, int turnCount) {
        return rawGapCount == 0 && turnCount >= EVIDENCE_MIN_TURNS;
    }

    /** 남은 턴 수 (응답 {@code remainingTurns}). */
    public int remainingTurns(int turnCount) {
        return Math.max(0, settings.maxTurns() - turnCount);
    }

    public int maxTurns() {
        return settings.maxTurns();
    }

    /**
     * 규칙 설정 ({@code devpilot.rubberduck}, docs/03 §9).
     *
     * @param dontKnowPhrases 부분 문자열로 찾는다 (대소문자 무시)
     */
    public record Settings(
            int maxTurns,
            int stuckTurnsBeforeHint,
            int dontKnowMaxChars,
            List<String> dontKnowPhrases) {

        public Settings {
            dontKnowPhrases = List.copyOf(dontKnowPhrases);
        }
    }
}
