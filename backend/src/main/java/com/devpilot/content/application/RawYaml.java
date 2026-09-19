package com.devpilot.content.application;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * 검증 전 YAML 값({@code Map}·{@code List}·스칼라) 판별 규칙. 기준 검증기 {@code
 * content/tools/validate_content.py}의 {@code check_keys}·{@code is_int}·{@code str_len_ok}와 같은 결과를
 * 낸다.
 */
final class RawYaml {

    private RawYaml() {}

    /** {@code bool}이 아닌 정수 (Python {@code is_int}). */
    static boolean isInt(@Nullable Object value) {
        return value instanceof Integer || value instanceof Long || value instanceof BigInteger;
    }

    static long longValue(Object value) {
        return ((Number) value).longValue();
    }

    /** 공백을 뺀 길이 ≥ lo, 전체 길이 ≤ hi (code point 기준, Python {@code len}). */
    static boolean strLenOk(@Nullable Object value, int lo, int hi) {
        if (!(value instanceof String text)) {
            return false;
        }
        String stripped = text.strip();
        return lo <= stripped.codePointCount(0, stripped.length())
                && text.codePointCount(0, text.length()) <= hi;
    }

    static int codePoints(String text) {
        return text.codePointCount(0, text.length());
    }

    /** 매핑이면 그대로, 아니면 빈 map (Python {@code x or {}}). 매핑인지는 {@link #isMap(Object)}로 확인한다. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(@Nullable Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    static boolean isMap(@Nullable Object value) {
        return value instanceof Map<?, ?>;
    }

    /** 목록이 아니면 빈 목록 (Python {@code x or []}). */
    static List<?> asList(@Nullable Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    static boolean truthy(@Nullable Object value) {
        if (value == null || Boolean.FALSE.equals(value)) {
            return false;
        }
        if (value instanceof String text) {
            return !text.isEmpty();
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return !(value instanceof Number number) || number.longValue() != 0;
    }

    /** CV-03: 매핑이고, 허용 필드만 있고, 필수 필드가 null이 아니다. 실패하면 오류를 남기고 {@code false}. */
    static boolean checkKeys(
            @Nullable Object value,
            Set<String> allowed,
            Set<String> required,
            ValidationContext context,
            String where) {
        if (!(value instanceof Map<?, ?> map)) {
            context.error(
                    "CV-03",
                    where,
                    "expected mapping, got "
                            + (value == null ? "null" : value.getClass().getSimpleName()));
            return false;
        }
        boolean ok = true;
        for (Object key : map.keySet()) {
            if (!allowed.contains(String.valueOf(key))) {
                context.error("CV-03", where, "unknown field '" + key + "'");
                ok = false;
            }
        }
        for (String key : new TreeSet<>(required)) {
            if (map.get(key) == null) {
                context.error("CV-03", where, "missing required field '" + key + "'");
                ok = false;
            }
        }
        return ok;
    }

    /** {@code importance}: 0.00~1.00, 소수 둘째 자리까지 (CV-22). 원문 문자열 또는 정수. */
    static @Nullable BigDecimal decimal(@Nullable Object value) {
        if (isInt(value)) {
            return BigDecimal.valueOf(longValue(value));
        }
        if (value instanceof String text) {
            try {
                return new BigDecimal(text.strip());
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }
}
