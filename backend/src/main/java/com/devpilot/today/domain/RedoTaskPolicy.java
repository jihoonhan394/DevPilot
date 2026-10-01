package com.devpilot.today.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 재현 과제 후보 고르기 (docs/06 §5.10 RE-1~RE-4).
 *
 * <p>AI가 옆에서 거들 때 풀린 것은 "이해했다"처럼 느껴진다. 실제로 할 수 있는지는 <b>며칠 뒤 혼자 처음부터 다시 만들 때</b> 드러난다. 이 클래스는 오늘 그
 * 확인을 걸 만한 원본 과제를 고른다.
 *
 * <p>순수 규칙이다 — 날짜·설정·후보 목록만 받고 저장소를 모른다.
 */
public final class RedoTaskPolicy {

    private final RedoSettings settings;

    public RedoTaskPolicy(RedoSettings settings) {
        this.settings = settings;
    }

    /**
     * 오늘 제안할 재현 후보 (docs/06 §5.10 "제안 절차"). skill 하나당 하나만 남긴다.
     *
     * @param today plan-day
     * @param originals 최근 완료된 {@code CHALLENGE}·{@code PROJECT_TASK}
     * @param existingRedos 그 원본들을 가리키는 모든 {@code REDO} 과제 (상태 무관)
     * @return skill code → 그 skill에서 고른 후보. 없으면 빈 map
     */
    public Map<String, RedoCandidate> selectBySkill(
            LocalDate today, List<RedoOrigin> originals, List<RedoAttempt> existingRedos) {
        record Eligible(LocalDate lastAttemptDate, RedoOrigin origin) {}

        List<Eligible> eligible = new ArrayList<>();
        for (RedoOrigin origin : originals) {
            LocalDate lastAttemptDate = lastAttemptDate(origin, existingRedos);
            if (isEligible(origin, existingRedos) && inWindow(today, lastAttemptDate)) {
                eligible.add(new Eligible(lastAttemptDate, origin));
            }
        }
        eligible.sort(
                Comparator.comparing(Eligible::lastAttemptDate)
                        .thenComparing(item -> item.origin().taskId()));
        Map<String, RedoCandidate> firstPerSkill = new LinkedHashMap<>();
        for (Eligible item : eligible) {
            RedoOrigin origin = item.origin();
            int daysAfter = (int) ChronoUnit.DAYS.between(origin.completedOn(), today);
            firstPerSkill.putIfAbsent(origin.skillCode(), new RedoCandidate(origin, daysAfter));
        }
        return Map.copyOf(firstPerSkill);
    }

    /**
     * 후보가 될 수 있는가 (RE-1·RE-3).
     *
     * <p>{@code skillCode}가 없으면 증거를 어느 skill에 붙일지 정할 수 없어 제외한다. 성공한 재현이 하나라도 있으면 그 원본은 끝난 것이고, 열려
     * 있는 재현이 있으면 그것부터 하면 된다. 시도가 상한에 이르면 더 붙잡지 않는다 — 두 번 실패한 것을 계속 내보내면 좌절만 남는다.
     */
    private boolean isEligible(RedoOrigin origin, List<RedoAttempt> existingRedos) {
        if (origin.skillCode() == null) {
            return false;
        }
        int attempts = 0;
        for (RedoAttempt redo : existingRedos) {
            if (!redo.sourceTaskId().equals(origin.taskId())) {
                continue;
            }
            if (redo.open()) {
                return false;
            }
            if (redo.succeeded()) {
                return false;
            }
            if (redo.countsAsAttempt()) {
                attempts++;
            }
        }
        return attempts < settings.maxAttempts();
    }

    /** 제안 창 안인가 (RE-2). 양쪽 끝을 포함한다. */
    private boolean inWindow(LocalDate today, LocalDate lastAttemptDate) {
        long days = ChronoUnit.DAYS.between(lastAttemptDate, today);
        return days >= settings.minDaysAfter() && days <= settings.maxDaysAfter();
    }

    /** 원본 완료일과 가장 최근 완료된 재현일 중 큰 값 (RE-2). */
    private static LocalDate lastAttemptDate(RedoOrigin origin, List<RedoAttempt> existingRedos) {
        LocalDate latest = origin.completedOn();
        for (RedoAttempt redo : existingRedos) {
            LocalDate completedOn = redo.completedOn();
            if (redo.sourceTaskId().equals(origin.taskId())
                    && completedOn != null
                    && completedOn.isAfter(latest)) {
                latest = completedOn;
            }
        }
        return latest;
    }

    /**
     * 재현할 원본 (RE-1).
     *
     * @param skillCode 없으면 후보가 되지 않는다 — 증거를 어느 skill에 붙일지 정할 수 없다
     * @param estimatedMinutes 재현 과제도 이 값을 그대로 쓴다 (RE-4)
     * @param completedOn 원본을 끝낸 plan-day. 제안 창은 여기서부터 센다 (RE-2)
     */
    public record RedoOrigin(
            UUID taskId,
            @Nullable String skillCode,
            TaskType taskType,
            String title,
            @Nullable String description,
            int estimatedMinutes,
            @Nullable Integer difficulty,
            LocalDate completedOn) {}

    /**
     * 오늘 고른 재현 후보.
     *
     * @param daysAfter 원본을 끝낸 날로부터 오늘까지의 일수. 과제 문구와 이유에 그대로 쓴다 (docs/06 §5.3·§5.8)
     */
    public record RedoCandidate(RedoOrigin origin, int daysAfter) {}

    /**
     * 그 원본을 가리키는 지난 재현 과제 (RE-3).
     *
     * @param completedOn 완료 plan-day. 아직 안 끝났으면 null
     * @param withoutAi 완료 때의 답. 그 밖이면 null
     */
    public record RedoAttempt(
            UUID sourceTaskId,
            TaskStatus status,
            @Nullable LocalDate completedOn,
            @Nullable Boolean withoutAi) {

        /** 아직 열려 있다 — 그것부터 하면 되므로 새로 만들지 않는다. */
        boolean open() {
            return status == TaskStatus.PLANNED || status == TaskStatus.IN_PROGRESS;
        }

        /** AI 없이 끝냈다 — 그 원본은 확인이 끝났다 (RE-8). */
        boolean succeeded() {
            return status == TaskStatus.COMPLETED && Boolean.TRUE.equals(withoutAi);
        }

        /**
         * 시도로 세는가 (RE-3). 완료(실패)와 {@code SKIPPED}만 센다. {@code DEFERRED}는 아직 해 보지 않은 것이라 세지 않는다 — 미룬
         * 것을 시도로 세면 바쁜 날이 기회를 깎는다.
         */
        boolean countsAsAttempt() {
            return status == TaskStatus.COMPLETED || status == TaskStatus.SKIPPED;
        }
    }

    /** {@code devpilot.planner.redo.*} (docs/03 §9). */
    public record RedoSettings(int minDaysAfter, int maxDaysAfter, int maxAttempts) {

        public RedoSettings {
            if (minDaysAfter > maxDaysAfter) {
                throw new IllegalArgumentException(
                        "redo.min-days-after must not exceed max-days-after");
            }
        }
    }
}
