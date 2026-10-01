package com.devpilot.training.application;

import com.devpilot.common.math.FixedPointMath;
import com.devpilot.learning.domain.HintLevel;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.training.domain.Challenge;
import com.devpilot.training.domain.ChallengeRubricItem;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * challenge 검증 (BL-TRN-03, I-09·I-10). {@code VALIDATED} 조건을 모두 만족하면 {@code VALIDATED}, 아니면 {@code
 * REJECTED} + {@code rejection_reason}이다. 사유는 운영·seed 적재 로그용이고 사용자에게 보여 주지 않는다(docs/05 §10.4).
 *
 * <ul>
 *   <li>I-09: {@code prompt}, {@code rubric}, {@code expectedConcepts}가 있어야 한다
 *   <li>I-10: rubric {@code weightBp} 합이 정확히 10000이고 id는 {@code R1..Rn}이다
 *   <li>difficulty 1~5, hint는 1~3단계({@code QUESTION_ONLY}·{@code CONCEPT_HINT}·{@code DIRECTION})만
 *   <li>skill은 1개 이상이고 모두 catalog에 있어야 한다
 * </ul>
 */
@Service
public class ChallengeValidationService {

    /** 사전 hint로 쓸 수 있는 단계 (HL-6). */
    static final List<HintLevel> PREGENERATED_LEVELS =
            List.of(HintLevel.QUESTION_ONLY, HintLevel.CONCEPT_HINT, HintLevel.DIRECTION);

    private static final int MIN_DIFFICULTY = 1;
    private static final int MAX_DIFFICULTY = 5;
    private static final int REASON_MAX = 1000;

    private final SkillCatalogQueryService skillCatalogQueryService;
    private final Clock clock;

    public ChallengeValidationService(
            SkillCatalogQueryService skillCatalogQueryService, Clock clock) {
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.clock = clock;
    }

    /** 검증하고 상태를 바꾼다. 호출자 트랜잭션에 참여한다. */
    public Result validate(Challenge challenge) {
        List<String> problems = problems(challenge, knownSkillIds(challenge.getSkillIds()));
        Instant now = clock.instant();
        if (problems.isEmpty()) {
            challenge.validated(now);
            return new Result(true, List.of());
        }
        challenge.rejected(reason(problems), now);
        return new Result(false, problems);
    }

    /** 검증만 한다 (상태를 바꾸지 않는다). */
    public List<String> problems(Challenge challenge, Set<UUID> knownSkillIds) {
        List<String> problems = new ArrayList<>();
        if (isBlank(challenge.getPrompt())) {
            problems.add("prompt is required");
        }
        if (challenge.getExpectedConcepts().isEmpty()) {
            problems.add("expectedConcepts is required");
        }
        checkRubric(challenge.getRubric(), problems);
        int difficulty = challenge.getDifficulty();
        if (difficulty < MIN_DIFFICULTY || difficulty > MAX_DIFFICULTY) {
            problems.add("difficulty must be 1..5 (got " + difficulty + ")");
        }
        checkHints(challenge, problems);
        Set<UUID> skillIds = challenge.getSkillIds();
        if (skillIds.isEmpty()) {
            problems.add("at least one skill is required");
        }
        for (UUID skillId : skillIds) {
            if (!knownSkillIds.contains(skillId)) {
                problems.add("unknown skill " + skillId);
            }
        }
        return List.copyOf(problems);
    }

    private static void checkRubric(List<ChallengeRubricItem> rubric, List<String> problems) {
        if (rubric.isEmpty()) {
            problems.add("rubric is required");
            return;
        }
        long total = 0;
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < rubric.size(); index++) {
            ChallengeRubricItem item = rubric.get(index);
            total += item.weightBp();
            if (!ids.add(item.id())) {
                problems.add("duplicate rubric id " + item.id());
            }
            if (!("R" + (index + 1)).equals(item.id())) {
                problems.add("rubric ids must be R1..Rn in order (got " + item.id() + ")");
            }
            if (isBlank(item.criterion())) {
                problems.add("rubric " + item.id() + " needs a criterion");
            }
        }
        if (total != FixedPointMath.BP_SCALE) {
            problems.add("rubric weightBp must sum to 10000 (got " + total + ")");
        }
    }

    private static void checkHints(Challenge challenge, List<String> problems) {
        Set<HintLevel> levels = challenge.pregeneratedHintLevels();
        if (levels.isEmpty()) {
            problems.add("at least one pregenerated hint is required");
        }
        for (HintLevel level : levels) {
            if (!PREGENERATED_LEVELS.contains(level)) {
                problems.add("hint level " + level + " must not be pregenerated");
                continue;
            }
            if (isBlank(challenge.pregeneratedHint(level))) {
                problems.add("hint " + level + " is blank");
            }
        }
    }

    private Set<UUID> knownSkillIds(Set<UUID> candidates) {
        return skillCatalogQueryService.findRefs(candidates).keySet();
    }

    private static String reason(List<String> problems) {
        String joined = String.join("; ", problems);
        return joined.length() <= REASON_MAX ? joined : joined.substring(0, REASON_MAX);
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    /**
     * 검증 결과.
     *
     * @param problems 통과하면 빈 목록
     */
    public record Result(boolean validated, List<String> problems) {

        public Result {
            problems = List.copyOf(problems);
        }
    }
}
