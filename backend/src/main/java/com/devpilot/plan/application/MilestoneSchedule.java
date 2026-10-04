package com.devpilot.plan.application;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 목표일에 맞춘 milestone 하나의 제안 날짜 (docs/05 §7.7, ADR-067).
 *
 * @param id 기존 milestone이면 그 id, 화면에서 새로 더한 것이면 null
 * @param changed 지금 날짜와 다른가. false면 화면이 바뀐 것만 강조할 수 있다 — 전부 같으면 제안 자체를 숨긴다
 */
public record MilestoneSchedule(
        @Nullable UUID id,
        int sortOrder,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        boolean changed) {}
