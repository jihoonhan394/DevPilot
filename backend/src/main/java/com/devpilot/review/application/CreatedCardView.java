package com.devpilot.review.application;

import com.devpilot.review.domain.ReviewType;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 용어에서 만든 복습 카드 한 장 (docs/05 §20.1).
 *
 * @param dueDate {@code due_at}의 plan-day. 시각이 아니라 날짜로 보여 준다
 */
public record CreatedCardView(
        UUID reviewItemId, String conceptKey, ReviewType reviewType, LocalDate dueDate) {}
