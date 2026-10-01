package com.devpilot.today.application;

import com.devpilot.today.domain.ReadingKind;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * {@code GET /readings}의 한 줄 (docs/05 §19.14). 본문은 없다 — 코드도 문서도 서버가 가져오지 않는다(docs/07 §5.5).
 *
 * @param title 코드 읽기는 물음, 개념 읽기는 문서 제목
 * @param source 코드 읽기는 {@code 저장소 경로}, 개념 읽기는 발행처
 * @param lastPlanDate 이 읽기가 마지막으로 오늘 할 일에 들어온 날
 * @param completed 한 번이라도 끝낸 적이 있는가
 * @param retired 은퇴한 단위여도 목록에는 남는다 — 이미 읽은 것을 숨길 이유가 없다
 */
public record ReadingHistoryView(
        String readingKey,
        ReadingKind kind,
        String title,
        @Nullable String source,
        @Nullable Integer estimatedMinutes,
        LocalDate lastPlanDate,
        boolean completed,
        boolean retired) {}
