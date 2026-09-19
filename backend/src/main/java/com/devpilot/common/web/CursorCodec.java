package com.devpilot.common.web;

import com.devpilot.common.error.BusinessValidationException;
import com.devpilot.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code (sortKey, id)} ↔ cursor 문자열 (docs/05 §1.5). JSON {@code
 * {"v":1,"k":"<sortKey>","id":"<uuid>"}}을 base64url(패딩 없음)로 인코딩한다. {@code k}는 Instant면 ISO-8601,
 * 정수면 10진 문자열이다. 디코딩 실패, {@code v ≠ 1}, {@code k} 타입 불일치, 512자 초과는 400 {@code INVALID_CURSOR}다.
 */
@Component
public class CursorCodec {

    private static final int VERSION = 1;
    private static final int MAX_LENGTH = 512;

    private final JsonMapper jsonMapper;

    public CursorCodec(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public String encode(Instant sortKey, UUID id) {
        return encodeRaw(sortKey.toString(), id);
    }

    public String encode(long sortKey, UUID id) {
        return encodeRaw(Long.toString(sortKey), id);
    }

    /** cursor가 없으면 {@code null}. */
    public @Nullable Position<Instant> decodeInstant(@Nullable String cursor) {
        return decode(cursor, Instant::parse);
    }

    /** cursor가 없으면 {@code null}. */
    public @Nullable Position<Long> decodeLong(@Nullable String cursor) {
        return decode(cursor, Long::valueOf);
    }

    private String encodeRaw(String key, UUID id) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("v", VERSION);
        payload.put("k", key);
        payload.put("id", id.toString());
        byte[] json = jsonMapper.writeValueAsBytes(payload);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
    }

    private <K> @Nullable Position<K> decode(
            @Nullable String cursor, Function<String, K> keyParser) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        if (cursor.length() > MAX_LENGTH) {
            throw invalid();
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(cursor.getBytes(StandardCharsets.US_ASCII));
            JsonNode node = jsonMapper.readTree(json);
            if (!node.isObject()
                    || !node.path("v").isInt()
                    || node.path("v").intValue() != VERSION
                    || !node.path("k").isString()
                    || !node.path("id").isString()) {
                throw invalid();
            }
            K key = keyParser.apply(node.path("k").stringValue());
            UUID id = UUID.fromString(node.path("id").stringValue());
            return new Position<>(key, id);
        } catch (IllegalArgumentException | DateTimeException | JacksonException exception) {
            throw new BusinessValidationException(
                    ErrorCode.INVALID_CURSOR, "invalid cursor", exception);
        }
    }

    private static BusinessValidationException invalid() {
        return new BusinessValidationException(ErrorCode.INVALID_CURSOR, "invalid cursor");
    }

    /** 디코딩한 위치: 이 {@code (sortKey, id)} 다음부터 읽는다. */
    public record Position<K>(K sortKey, UUID id) {}
}
