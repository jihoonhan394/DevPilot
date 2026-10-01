package com.devpilot.evidence.application;

import com.devpilot.project.application.SideProjectNoteQueryService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지표 입력 모으기 (docs/06 §12). 계산은 {@code evidence.domain.MetricsCalculator}가 하고, 여기서는 모듈마다 흩어진 값을 읽어
 * 온다.
 *
 * <p>지금은 {@code projectNoteCount} 하나뿐이다 — {@code dashboard}가 {@code project}에 직접 의존하지 않고 이 경로로
 * 읽는다(docs/03 §2.2, docs/05 §13.1). 나머지 지표를 모아 {@code MetricsInput}을 채우고 {@code
 * weekly_review.metrics_json}에 넣는 일은 S5(BL-EVD-01~04)다.
 */
@Service
@Transactional(readOnly = true)
public class LearningMetricsQueryService {

    private final SideProjectNoteQueryService noteQueryService;

    public LearningMetricsQueryService(SideProjectNoteQueryService noteQueryService) {
        this.noteQueryService = noteQueryService;
    }

    /**
     * 그 기간에 적은 프로젝트 기록 수 (docs/06 §12 {@code projectNoteCount}).
     *
     * @param from 포함, {@code occurredOn} 기준
     * @param to 포함
     */
    public int projectNoteCount(UUID userId, LocalDate from, LocalDate to) {
        return noteQueryService.countBetween(userId, from, to);
    }
}
