package com.devpilot.skill.domain;

import com.devpilot.learning.domain.HintLevel;
import com.devpilot.learning.domain.LearningEventType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * skill 레벨 규칙의 입력 이벤트 (docs/06 §7.1: payload만 본다). payload 키는 docs/04 §6 표다. jsonb에서 온 값이므로 숫자는
 * {@link Number}, enum은 문자열이다.
 */
public record RuleEvent(
        UUID id,
        LearningEventType eventType,
        LocalDate planDate,
        Instant occurredAt,
        Map<String, Object> payload) {

    /** 등급 순서 (docs/06 §6.1): {@code AGAIN(0) < HARD(1) < GOOD(2) < EASY(3)}. */
    private static final List<String> RATING_ORDER = List.of("AGAIN", "HARD", "GOOD", "EASY");

    public RuleEvent {
        payload = Map.copyOf(payload);
    }

    /** 문자열 payload 값. 없으면 null. */
    public @Nullable String text(String key) {
        Object value = payload.get(key);
        return value instanceof String string ? string : null;
    }

    /** 정수 payload 값. 없거나 숫자가 아니면 null. */
    public @Nullable Integer number(String key) {
        Object value = payload.get(key);
        return value instanceof Number num ? num.intValue() : null;
    }

    /** boolean payload 값. 없으면 false. */
    public boolean flag(String key) {
        return Boolean.TRUE.equals(payload.get(key));
    }

    /** {@code payload[key]}가 이 값들 중 하나인가. */
    public boolean textIn(String key, String... values) {
        String actual = text(key);
        if (actual == null) {
            return false;
        }
        for (String value : values) {
            if (value.equals(actual)) {
                return true;
            }
        }
        return false;
    }

    /** {@code HintLevel} payload 값. 없으면 null. */
    public @Nullable HintLevel hintLevel(String key) {
        String value = text(key);
        return value == null ? null : HintLevel.valueOf(value);
    }

    /** {@code hintLevel}(또는 {@code maxHintLevel})이 이 단계 이하인가. 값이 없으면 false. */
    public boolean hintAtMost(String key, HintLevel limit) {
        HintLevel level = hintLevel(key);
        return level != null && level.ordinal() <= limit.ordinal();
    }

    /** 등급이 이 등급 이상인가 (docs/06 §6.1 순서). */
    public boolean ratingAtLeast(String key, String minimum) {
        String value = text(key);
        if (value == null) {
            return false;
        }
        int actual = RATING_ORDER.indexOf(value);
        int floor = RATING_ORDER.indexOf(minimum);
        return actual >= 0 && floor >= 0 && actual >= floor;
    }
}
