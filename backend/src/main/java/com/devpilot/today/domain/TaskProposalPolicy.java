package com.devpilot.today.domain;

import com.devpilot.common.config.TrackDefaults;
import com.devpilot.common.web.AxisLevels;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 후보 skill마다 main 과제 1개를 제안한다 (docs/06 §5.3, BL-TDY-03). 순수 규칙 클래스다(ARCH-12).
 *
 * <pre>
 * d = clamp(planning IMPLEMENTATION + 1, 1, trackDefaults.maxTaskDifficulty), 복귀 모드면 min(d, 2)
 * 1. AI 가능 + planning KNOWLEDGE ≥ trackDefaults.challengeMinKnowledge (ADR-045)
 *    + 조건을 만족하는 challenge(difficulty d, 없으면 d−1) → CHALLENGE
 * 2. AI 가능 + planning KNOWLEDGE ≥ trackDefaults.readCodeMinKnowledge + 선택 가능한 reading
 *    → READ_CODE (reading 시간, difficulty 2)
 * 3. planning KNOWLEDGE &lt; 2 → READING (개념 읽기 후보가 있으면 그 시간, 없으면 25. 둘 다 difficulty 1)
 * 4. projectNeed + energy != LOW + ACTIVE 사이드 프로젝트 → PROJECT_TASK (30, 3)
 * 5. 그 외 → EXPLAIN (15, 2)
 * </pre>
 *
 * 난이도 상한과 {@code READ_CODE} 문턱은 학습 트랙 기본값이 정한다({@link TrackDefaults}, docs/06 §5.3 표, BL-GOL-18).
 *
 * <p>challenge·reading·개념 읽기 후보의 조건 확인(14 plan-day 안 시도·제안, 해결 여부, 완료한 reading)은 호출자가 하고({@link
 * ConceptReadingSelection}), 이 클래스는 순서와 선택만 정한다. S2 빌드는 후보 목록을 비워 둔다(BL-TDY-14·BL-TDY-16은 S3).
 */
public final class TaskProposalPolicy {

    static final int READING_MINUTES = 25;
    static final int PROJECT_TASK_MINUTES = 30;
    static final int EXPLAIN_MINUTES = 15;
    static final int READ_CODE_DIFFICULTY = 2;
    static final int READING_DIFFICULTY = 1;
    static final int PROJECT_TASK_DIFFICULTY = 3;
    static final int EXPLAIN_DIFFICULTY = 2;

    private static final int COMEBACK_MAX_DIFFICULTY = 2;
    private static final int READING_MAX_KNOWLEDGE = 2;
    private static final int TITLE_MAX = 200;
    private static final int DESCRIPTION_MAX = 2000;
    private static final int SCENARIO_SUMMARY_MAX = 200;
    private static final Comparator<ChallengeOption> CHALLENGE_ORDER =
            Comparator.comparing(
                            ChallengeOption::seedKey,
                            Comparator.nullsLast(Comparator.<String>naturalOrder()))
                    .thenComparing(ChallengeOption::id);

    /** 후보 skill의 제안 과제. */
    public Proposal propose(ProposalInput input) {
        SkillContext skill = input.skill();
        AxisLevels planning = skill.planning();
        TrackDefaults track = input.trackDefaults();
        // 0. 재현 과제가 걸려 있으면 그것이 오늘의 main이다 (docs/06 §5.10). AI를 쓰지 않으므로
        // 예산이 바닥나도 나온다 — 혼자 할 수 있는지 확인하는 것이 이 과제의 목적이다.
        RedoTaskPolicy.RedoCandidate redo = input.redoCandidate();
        if (redo != null) {
            return redo(redo);
        }
        // 1. 가르치고 나서 시험한다 (docs/06 §5.13 TH-5). 노트에 남은 단위가 있고 아직 아는 것이 적으면
        // 문제·코드 읽기보다 먼저 개념을 뗀다 — 순서가 거꾸로면 답만 맞히고 어디에 쓰는지는 모른 채 지난다
        LessonStep lessonStep = input.lessonStep();
        if (lessonStep != null && planning.knowledge() < READING_MAX_KNOWLEDGE) {
            return lesson(skill, lessonStep);
        }
        int difficulty = Math.clamp(planning.implementation() + 1L, 1, track.maxTaskDifficulty());
        if (input.comebackMode()) {
            difficulty = Math.min(difficulty, COMEBACK_MAX_DIFFICULTY);
        }
        if (input.aiAvailable()) {
            Optional<Proposal> withAi = proposeWithAi(input, difficulty);
            if (withAi.isPresent()) {
                return withAi.get();
            }
        }
        if (planning.knowledge() < READING_MAX_KNOWLEDGE) {
            return firstConceptReading(input.conceptReadings())
                    .map(material -> reading(skill, material))
                    .orElseGet(() -> reading(skill));
        }
        SideProjectRef project = input.activeSideProject();
        if (skill.projectNeed() && input.energy() != EnergyLevel.LOW && project != null) {
            return projectTask(skill, project, input.projectGuide());
        }
        return explain(skill);
    }

    /**
     * AI를 쓸 수 있을 때의 1·2번 분기 (docs/06 §5.3). 둘 다 고를 수 없으면 비어 있고, 호출자가 아래 분기로 이어 간다.
     *
     * <p>ADR-045: 개념을 한 번도 안 본 skill에는 문제를 내지 않는다. 코드 읽기에만 문턱이 있고 문제 풀기에는 없으면, 지식 0인 학습자가 어디에 쓰는지
     * 모른 채 답만 맞히게 된다.
     */
    private static Optional<Proposal> proposeWithAi(ProposalInput input, int difficulty) {
        AxisLevels planning = input.skill().planning();
        TrackDefaults track = input.trackDefaults();
        Optional<ChallengeOption> challenge =
                planning.knowledge() >= track.challengeMinKnowledge()
                        ? chooseChallenge(input.challenges(), difficulty)
                        : Optional.empty();
        if (challenge.isPresent()) {
            return Optional.of(challenge(challenge.get()));
        }
        if (planning.knowledge() < track.readCodeMinKnowledge()) {
            return Optional.empty();
        }
        return firstReading(input.readings()).map(TaskProposalPolicy::readCode);
    }

    /** difficulty d, 없으면 d−1(≥ 1). 같은 difficulty 안에서는 seed_key ASC(null 뒤) → id ASC. */
    static Optional<ChallengeOption> chooseChallenge(
            List<ChallengeOption> options, int difficulty) {
        Optional<ChallengeOption> exact = challengeAt(options, difficulty);
        if (exact.isPresent() || difficulty <= 1) {
            return exact;
        }
        return challengeAt(options, difficulty - 1);
    }

    /** 같은 skill에서 difficulty를 1씩 낮춰 가며 {@code estimated ≤ limit}인 첫 challenge (docs/06 §5.6). */
    static Optional<ChallengeOption> challengeWithin(
            List<ChallengeOption> options, int startDifficulty, int limit) {
        for (int difficulty = startDifficulty; difficulty >= 1; difficulty--) {
            int level = difficulty;
            Optional<ChallengeOption> found =
                    options.stream()
                            .filter(option -> option.difficulty() == level)
                            .filter(option -> option.estimatedMinutes() <= limit)
                            .min(CHALLENGE_ORDER);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static Optional<ChallengeOption> challengeAt(
            List<ChallengeOption> options, int difficulty) {
        return options.stream()
                .filter(option -> option.difficulty() == difficulty)
                .min(CHALLENGE_ORDER);
    }

    /** reading 선택: key ASC 첫 번째 (docs/06 §5.3 "reading 선택"). */
    static Optional<ReadingOption> firstReading(List<ReadingOption> readings) {
        return readings.stream().min(Comparator.comparing(ReadingOption::key));
    }

    /** 개념 읽기 선택: key ASC 첫 번째 (docs/06 §5.3 "개념 읽기 선택"). 비면 자료 없이 제안한다. */
    static Optional<ConceptReading> firstConceptReading(List<ConceptReading> conceptReadings) {
        return conceptReadings.stream().min(Comparator.comparing(ConceptReading::key));
    }

    /**
     * 개념 익히기 (docs/06 §5.3 1번). 새 {@code TaskType}을 만들지 않고 {@code READING}을 재사용한다 — {@code
     * readingKey}가 노트 key를 가리키고 화면이 {@code kind}로 구분한다(docs/05 §19.7).
     *
     * <p>예상 시간은 오늘 낼 단위들의 합이라 노트 전체가 아니다. 며칠에 걸쳐 나눠 낸다(TH-2).
     */
    static Proposal lesson(SkillContext skill, LessonStep step) {
        String more =
                step.remainingAfter() == 0
                        ? "이 단위를 마치면 이 노트가 끝납니다."
                        : "마치면 " + step.remainingAfter() + "단위가 남습니다.";
        String description =
                "단위 "
                        + step.unitKeys().size()
                        + "개를 차례로 봅니다. 각 단위는 예측 → 빈칸 → 직접 만들기로 끝납니다. "
                        + more;
        return new Proposal(
                TaskType.READING,
                step.minutes(),
                READING_DIFFICULTY,
                truncate(skill.name() + " 개념 익히기 — " + step.firstUnitTitle(), TITLE_MAX),
                truncate(description, DESCRIPTION_MAX),
                null,
                step.lessonKey(),
                null,
                null,
                null,
                null);
    }

    /** CHALLENGE: 제목 = challenge 제목, 설명 = scenario 앞 200자. */
    static Proposal challenge(ChallengeOption option) {
        String scenario = option.scenario();
        return new Proposal(
                TaskType.CHALLENGE,
                option.estimatedMinutes(),
                option.difficulty(),
                truncate(option.title(), TITLE_MAX),
                scenario == null ? null : truncate(scenario, SCENARIO_SUMMARY_MAX),
                option.id(),
                null,
                null,
                null,
                null,
                null);
    }

    /** READ_CODE: 제목 {@code {repo.name} 읽기 — {파일명} {시작}~{끝}줄}, 설명은 질문 + 완료 조건 안내. */
    static Proposal readCode(ReadingOption reading) {
        String path = reading.path();
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        String title =
                reading.repoName()
                        + " 읽기 — "
                        + fileName
                        + " "
                        + reading.startLine()
                        + "~"
                        + reading.endLine()
                        + "줄";
        return new Proposal(
                TaskType.READ_CODE,
                reading.estimatedMinutes(),
                READ_CODE_DIFFICULTY,
                truncate(title, TITLE_MAX),
                truncate(reading.question() + "\n읽고 나서 러버덕으로 설명하면 완료입니다.", DESCRIPTION_MAX),
                null,
                reading.key(),
                null,
                reading.repoName(),
                null,
                null);
    }

    /** READING (개념 읽기 후보 없음): {@code {skill.name} 핵심 개념 정리}. 자료를 가리키지 못하는 지금까지의 과제다. */
    static Proposal reading(SkillContext skill) {
        return new Proposal(
                TaskType.READING,
                READING_MINUTES,
                READING_DIFFICULTY,
                truncate(skill.name() + " 핵심 개념 정리", TITLE_MAX),
                describe(skill, "공식 문서를 읽고 핵심 3가지를 스스로 적어 보세요."),
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * READING (개념 읽기 있음): {@code {skill.name} 개념 읽기 — {conceptReading.title}}. 예상 시간은 콘텐츠 값을 그대로
     * 쓰고(docs/06 §5.3) key를 {@code learning_task.reading_key}에 저장한다. 자료 제목·링크·{@code checkPoints}는
     * 화면이 {@code GET /readings/{readingKey}}로 가져온다(docs/02 SCR-TODAY).
     */
    static Proposal reading(SkillContext skill, ConceptReading material) {
        return new Proposal(
                TaskType.READING,
                material.estimatedMinutes(),
                READING_DIFFICULTY,
                truncate(skill.name() + " 개념 읽기 — " + material.title(), TITLE_MAX),
                truncate(material.whyRead() + "\n읽고 나서 핵심 3가지를 스스로 적어 보세요.", DESCRIPTION_MAX),
                null,
                material.key(),
                null,
                null,
                null,
                null);
    }

    /**
     * PROJECT_TASK: {@code {프로젝트 이름}에 {skill.name} 적용하기} (SP-2). 프로젝트 id는 생성 시점에 고정한다(SP-3).
     *
     * <p>설명은 그 skill 노트의 {@code inProject}다 (ADR-048) — 노트가 이미 "이 개념을 프로젝트 어디에 쓰는가"를 쓰고 있고, 그래야 읽기와
     * 만들기가 같은 글로 이어진다. 노트가 없으면 일반 문장으로 돌아간다.
     */
    static Proposal projectTask(
            SkillContext skill, SideProjectRef project, @Nullable String projectGuide) {
        String description =
                projectGuide == null || projectGuide.isBlank()
                        ? project.name() + "에서 이 개념을 적용할 지점을 찾아 구현하고 이유를 적어 보세요."
                        : projectGuide;
        return new Proposal(
                TaskType.PROJECT_TASK,
                PROJECT_TASK_MINUTES,
                PROJECT_TASK_DIFFICULTY,
                truncate(project.name() + "에 " + skill.name() + " 적용하기", TITLE_MAX),
                truncate(description, DESCRIPTION_MAX),
                null,
                null,
                project.id(),
                null,
                null,
                null);
    }

    /**
     * REDO (docs/06 §5.10 0번 분기): 며칠 전에 끝낸 것을 <b>AI 없이 처음부터 다시</b> 만든다.
     *
     * <p>예상 시간과 난이도는 원본 그대로다(RE-4) — 같은 것을 다시 만드는 과제라 줄일 수 없다.
     */
    static Proposal redo(RedoTaskPolicy.RedoCandidate candidate) {
        RedoTaskPolicy.RedoOrigin origin = candidate.origin();
        return new Proposal(
                TaskType.REDO,
                origin.estimatedMinutes(),
                originalDifficulty(origin.taskType(), origin.difficulty()),
                truncate(origin.title() + " 혼자 다시 만들기", TITLE_MAX),
                truncate(
                        candidate.daysAfter()
                                + "일 전에 한 과제입니다. 이번에는 AI 도움 없이 처음부터 혼자 다시 만들어 보세요."
                                + " 막히면 기록해 두고, 끝나고 혼자 해냈는지 답해 주세요.",
                        DESCRIPTION_MAX),
                null,
                null,
                null,
                null,
                origin.taskId(),
                candidate.daysAfter());
    }

    /**
     * 원본 과제의 difficulty (docs/06 §5.3). {@code CHALLENGE}는 그 문제의 난이도이고, {@code PROJECT_TASK}는 제안할 때
     * 쓴 고정값이다. 문제가 지워졌으면 EXPLAIN과 같은 난이도로 둔다.
     *
     * <p>과제 행에 난이도를 저장하지 않아(docs/04 §5.1) 재현 과제를 만들 때도, 완료 이벤트를 남길 때도 여기서 다시 정한다.
     */
    public static int originalDifficulty(
            TaskType originalType, @Nullable Integer challengeDifficulty) {
        if (originalType == TaskType.PROJECT_TASK) {
            return PROJECT_TASK_DIFFICULTY;
        }
        return challengeDifficulty == null ? EXPLAIN_DIFFICULTY : challengeDifficulty;
    }

    /** EXPLAIN: {@code {skill.name} 내 말로 설명하기}. */
    public static Proposal explain(SkillContext skill) {
        return new Proposal(
                TaskType.EXPLAIN,
                EXPLAIN_MINUTES,
                EXPLAIN_DIFFICULTY,
                truncate(skill.name() + " 내 말로 설명하기", TITLE_MAX),
                describe(skill, "5문장 이내로 설명하고 예시를 하나 드세요."),
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /** RECALL: {@code {skill.name} 5분 떠올리기}. 시간은 호출자가 정한다(docs/06 §5.6). */
    public static Proposal recall(SkillContext skill, int estimatedMinutes) {
        return new Proposal(
                TaskType.RECALL,
                estimatedMinutes,
                1,
                truncate(skill.name() + " 5분 떠올리기", TITLE_MAX),
                "자료를 보지 않고 기억나는 내용을 적어 보세요.",
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /** REVIEW 과제 제목: {@code 복습 {n}장}. */
    public static String reviewTitle(int cardCount) {
        return "복습 " + cardCount + "장";
    }

    private static String describe(SkillContext skill, String guide) {
        String description = skill.description();
        String text = description.isBlank() ? guide : description + "\n" + guide;
        return truncate(text, DESCRIPTION_MAX);
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    /**
     * 제안 대상 skill.
     *
     * @param planning docs/06 §7.5 planning level
     * @param projectNeed 학습 목표의 집중 skill
     */
    public record SkillContext(
            String code,
            String name,
            String description,
            AxisLevels planning,
            boolean projectNeed) {}

    /** 풀 수 있는 challenge 후보 (VALIDATED PRACTICE, 호출자가 거른 것). */
    public record ChallengeOption(
            UUID id,
            @Nullable String seedKey,
            String title,
            @Nullable String scenario,
            int difficulty,
            int estimatedMinutes) {}

    /** 선택 가능한 reading 후보 (호출자가 거른 것). */
    public record ReadingOption(
            String key,
            String repoName,
            String path,
            int startLine,
            int endLine,
            int estimatedMinutes,
            String question) {}

    /** 생성 시점의 ACTIVE 사이드 프로젝트 중 {@code updated_at}이 가장 최근인 것 (SP-3). */
    public record SideProjectRef(UUID id, String name) {}

    /**
     * 제안 입력.
     *
     * @param aiAvailable {@code aiStatus ∉ {DISABLED, BALANCE_EXHAUSTED}}
     * @param trackDefaults 학습 목표의 트랙 기본값 (docs/06 §5.1·§5.3 표, {@code devpilot.tracks.<트랙>})
     * @param conceptReadings 3번 분기가 쓸 개념 읽기 후보 (key ASC, {@link ConceptReadingSelection}). AI 상태와
     *     무관하다 — 개념 읽기는 AI를 쓰지 않는다
     * @param activeSideProject 없으면 null (SP-1: PROJECT_TASK를 제안하지 않는다)
     */
    public record ProposalInput(
            SkillContext skill,
            EnergyLevel energy,
            boolean comebackMode,
            boolean aiAvailable,
            TrackDefaults trackDefaults,
            List<ChallengeOption> challenges,
            List<ReadingOption> readings,
            List<ConceptReading> conceptReadings,
            @Nullable SideProjectRef activeSideProject,
            @Nullable String projectGuide,
            RedoTaskPolicy.@Nullable RedoCandidate redoCandidate,
            @Nullable LessonStep lessonStep) {

        public ProposalInput {
            challenges = List.copyOf(challenges);
            readings = List.copyOf(readings);
            conceptReadings = List.copyOf(conceptReadings);
        }
    }

    /**
     * 제안 과제.
     *
     * @param readingKey READ_CODE는 항상, READING은 개념 읽기 후보가 있을 때만. 그 밖에는 null (I-17)
     * @param repoName READ_CODE일 때 저장소 이름 (reason {@code READ_REAL_CODE} 변수)
     */
    public record Proposal(
            TaskType taskType,
            int estimatedMinutes,
            int difficulty,
            String title,
            @Nullable String description,
            @Nullable UUID challengeId,
            @Nullable String readingKey,
            @Nullable UUID sideProjectId,
            @Nullable String repoName,
            @Nullable UUID redoSourceTaskId,
            @Nullable Integer redoDaysAfter) {}
}
