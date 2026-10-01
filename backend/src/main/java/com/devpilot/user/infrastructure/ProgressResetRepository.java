package com.devpilot.user.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 학습 진도 초기화의 DELETE (docs/05 §3.7, ADR-056, BL-SEC-19).
 *
 * <p>여러 모듈의 테이블을 지우지만 <b>다른 모듈의 entity·repository를 쓰지 않는다</b> — 테이블 이름만 아는 SQL이다(docs/03 §2.2). raw
 * SQL은 infrastructure에만 둘 수 있다(ARCH-14, docs/08 §5).
 *
 * <p>목록이 낡으면 지워야 할 것이 남는다. {@code ProgressResetCoverageTest}가 {@code information_schema}에서 {@code
 * user_id}를 가진 테이블을 읽어 이 목록과 대조한다.
 */
@Repository
public class ProgressResetRepository {

    /**
     * 지우는 테이블. {@code user_id}를 가진 것만 있다 — 자식 테이블({@code plan_milestone}, {@code coach_finding},
     * {@code rubber_duck_turn} 등)은 자기 컬럼에 {@code user_id}가 없고, 부모를 지우면 FK cascade로 따라간다.
     *
     * <p>{@code ai_call_log}(비용·감사)와 {@code idempotency_record}(요청 중복 처리용, 자체 TTL이 있다)는 <b>여기
     * 없다</b> — 학습 진도가 아니다(ADR-056). {@code side_project}와 {@code side_project_note}는 선택이라 {@link
     * #deleteProjects}에 따로 있다.
     */
    private static final List<String> PROGRESS_TABLES =
            List.of(
                    "review_answer",
                    "review_item",
                    "hint_disclosure",
                    "challenge_submission",
                    "challenge_attempt",
                    "coach_review",
                    "rubber_duck_session",
                    "learning_task",
                    "daily_plan",
                    "learning_session",
                    "skill_state_change",
                    "user_skill_state",
                    "plan_progress_snapshot",
                    "learning_plan",
                    "learning_goal",
                    "user_daily_tip",
                    "weekly_review",
                    "evidence_candidate",
                    "thinking_pattern_observation",
                    "requirement_doc",
                    "learning_event");

    /** 지우는 테이블 이름. 목록이 낡지 않았는지 대조하는 테스트가 쓴다 (ADR-056). */
    public static List<String> progressTables() {
        return PROGRESS_TABLES;
    }

    /** 사용자가 만든 문제. {@code owner_user_id}가 null인 seed 문제는 남는다(docs/19 §3.6). */
    private static final String DELETE_OWNED_CHALLENGES =
            "delete from devpilot.challenge where owner_user_id = :userId";

    private final JdbcClient jdbcClient;

    ProgressResetRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** 진도 행을 지우고 지운 행 수 합계를 돌려준다. */
    public int deleteProgress(UUID userId) {
        int deleted = 0;
        for (String table : PROGRESS_TABLES) {
            deleted +=
                    jdbcClient
                            .sql("delete from devpilot." + table + " where user_id = :userId")
                            .param("userId", userId)
                            .update();
        }
        return deleted + jdbcClient.sql(DELETE_OWNED_CHALLENGES).param("userId", userId).update();
    }

    /** 사이드 프로젝트와 그 기록. {@code includeProjects}가 true일 때만 부른다. */
    public int deleteProjects(UUID userId) {
        int notes =
                jdbcClient
                        .sql("delete from devpilot.side_project_note where user_id = :userId")
                        .param("userId", userId)
                        .update();
        int projects =
                jdbcClient
                        .sql("delete from devpilot.side_project where user_id = :userId")
                        .param("userId", userId)
                        .update();
        return notes + projects;
    }
}
