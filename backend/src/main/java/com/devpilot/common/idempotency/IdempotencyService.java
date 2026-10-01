package com.devpilot.common.idempotency;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.error.ApiFieldError;
import com.devpilot.common.error.BusinessValidationException;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.FieldErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 인증 {@code POST}의 멱등 처리 (docs/03 §5.4, docs/05 §1.7, AC-23).
 *
 * <ol>
 *   <li>{@code idempotency_record}를 {@code ON CONFLICT DO NOTHING}으로 선삽입한다(별도 짧은 트랜잭션, TX-4).
 *   <li>삽입됨 → action 실행 → 2xx 응답의 status·body를 저장 → 반환. action이 예외로 끝나면 record를 지워 같은 키로 다시 시도할 수
 *       있게 한다.
 *   <li>이미 있음 → hash가 다르면 422 {@code IDEMPOTENCY_KEY_REUSED}, 응답이 저장돼 있으면 재생({@code
 *       Idempotent-Replayed: true}), 아니면 409 {@code IDEMPOTENCY_IN_PROGRESS}. 만료된 record는 지우고 새
 *       요청으로 처리한다.
 * </ol>
 *
 * {@code requestHash = SHA-256(method + " " + path + "\n" + canonical JSON body)} — canonical은 키
 * 사전순, 공백 없음.
 */
@Service
public class IdempotencyService {

    /** 요청 헤더 이름 (docs/05 §1.7). */
    public static final String HEADER = "Idempotency-Key";

    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{8,100}$");
    private static final int MAX_CLAIM_ATTEMPTS = 3;
    private static final int SUCCESS_FAMILY = 2;

    private static final String INSERT =
            """
            insert into devpilot.idempotency_record
                (user_id, idempotency_key, request_method, request_path, request_hash, created_at,
                 expires_at)
            values (:userId, :key, :method, :path, :hash, :createdAt, :expiresAt)
            on conflict (user_id, idempotency_key) do nothing
            """;
    private static final String SELECT =
            """
            select request_hash, response_status, response_body::text as response_body, expires_at
              from devpilot.idempotency_record
             where user_id = :userId and idempotency_key = :key
            """;
    private static final String SAVE_RESPONSE =
            """
            update devpilot.idempotency_record
               set response_status = :status, response_body = cast(:body as jsonb)
             where user_id = :userId and idempotency_key = :key
            """;
    private static final String DELETE =
            "delete from devpilot.idempotency_record where user_id = :userId and idempotency_key"
                    + " = :key";
    private static final String DELETE_EXPIRED =
            "delete from devpilot.idempotency_record where user_id = :userId and idempotency_key"
                    + " = :key and expires_at <= :now";
    private static final String DELETE_ALL_EXPIRED =
            "delete from devpilot.idempotency_record where expires_at < :now";

    private final JdbcClient jdbcClient;
    private final TransactionTemplate requiresNew;
    private final JsonMapper jsonMapper;
    private final JsonMapper canonicalMapper;
    private final Clock clock;
    private final Duration ttl;

    public IdempotencyService(
            JdbcClient jdbcClient,
            PlatformTransactionManager transactionManager,
            JsonMapper jsonMapper,
            Clock clock,
            DevPilotProperties properties) {
        this.jdbcClient = jdbcClient;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.jsonMapper = jsonMapper;
        this.canonicalMapper =
                jsonMapper
                        .rebuild()
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                        .disable(SerializationFeature.INDENT_OUTPUT)
                        .build();
        this.clock = clock;
        this.ttl = properties.privacy().idempotencyTtl();
    }

    /**
     * 컨트롤러용 진입점. method·path(query 제외)·body로 request hash를 만들고 재생 헤더까지 붙인 응답을 돌려준다.
     *
     * @param responseType 재생 시 저장된 body를 되살릴 타입(= action 응답 body 타입)
     */
    public <T> ResponseEntity<T> execute(
            UUID userId,
            String key,
            HttpServletRequest request,
            @Nullable Object body,
            Class<T> responseType,
            Supplier<ResponseEntity<T>> action) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return execute(
                        userId,
                        key,
                        method,
                        path,
                        requestHash(method, path, body),
                        responseType,
                        action)
                .toResponseEntity();
    }

    /** docs/03 §3.1 시그니처. */
    public <T> IdempotentResult<T> execute(
            UUID userId,
            String key,
            String method,
            String path,
            String requestHash,
            Class<T> responseType,
            Supplier<ResponseEntity<T>> action) {
        requireKeyFormat(key);
        Optional<StoredRecord> existing = claim(userId, key, method, path, requestHash);
        if (existing.isPresent()) {
            return replayOrReject(existing.get(), requestHash, responseType);
        }
        boolean completed = false;
        try {
            ResponseEntity<T> response = action.get();
            if (response.getStatusCode().value() / 100 == SUCCESS_FAMILY) {
                saveResponse(userId, key, response);
                completed = true;
            }
            return new IdempotentResult<>(response, false);
        } finally {
            if (!completed) {
                // 실패 응답은 저장하지 않는다 — 같은 키로 다시 시도할 수 있게 record를 지운다 (docs/05 §1.7)
                requiresNew.executeWithoutResult(
                        status ->
                                jdbcClient
                                        .sql(DELETE)
                                        .param("userId", userId)
                                        .param("key", key)
                                        .update());
            }
        }
    }

    /**
     * 만료된 기록 전부 삭제 (docs/04 §8, BL-FND-24 {@code RetentionCleanupJob}). 자기 트랜잭션이다.
     *
     * @return 지운 행 수
     */
    public int deleteExpired(Instant now) {
        Integer deleted =
                requiresNew.execute(
                        status -> jdbcClient.sql(DELETE_ALL_EXPIRED).param("now", now).update());
        return deleted == null ? 0 : deleted;
    }

    /**
     * {@code SHA-256(method + " " + path + "\n" + canonical JSON body)}, 소문자 hex. body가 없으면 빈 문자열.
     */
    public String requestHash(String method, String path, @Nullable Object body) {
        String canonical =
                body == null
                        ? ""
                        : canonicalMapper.writeValueAsString(
                                jsonMapper.convertValue(body, Object.class));
        String material = method + " " + path + "\n" + canonical;
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static void requireKeyFormat(String key) {
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new BusinessValidationException(
                    "invalid Idempotency-Key format",
                    List.of(ApiFieldError.of(HEADER, FieldErrorCodes.PATTERN)));
        }
    }

    /** record를 선삽입한다. 이미 있으면(만료 제외) 그 record를 돌려준다. */
    private Optional<StoredRecord> claim(
            UUID userId, String key, String method, String path, String requestHash) {
        for (int attempt = 0; attempt < MAX_CLAIM_ATTEMPTS; attempt++) {
            Instant now = clock.instant();
            Integer inserted =
                    requiresNew.execute(
                            status ->
                                    jdbcClient
                                            .sql(INSERT)
                                            .param("userId", userId)
                                            .param("key", key)
                                            .param("method", method)
                                            .param("path", path)
                                            .param("hash", requestHash)
                                            .param("createdAt", utc(now))
                                            .param("expiresAt", utc(now.plus(ttl)))
                                            .update());
            if (inserted != null && inserted == 1) {
                return Optional.empty();
            }
            Optional<StoredRecord> stored = requiresNew.execute(status -> find(userId, key));
            if (stored != null && stored.isPresent()) {
                if (stored.get().expiresAt().isAfter(now)) {
                    return stored;
                }
                requiresNew.executeWithoutResult(
                        status ->
                                jdbcClient
                                        .sql(DELETE_EXPIRED)
                                        .param("userId", userId)
                                        .param("key", key)
                                        .param("now", utc(now))
                                        .update());
            }
        }
        throw new ConflictException(
                ErrorCode.IDEMPOTENCY_IN_PROGRESS, "could not claim the idempotency key");
    }

    private Optional<StoredRecord> find(UUID userId, String key) {
        return jdbcClient
                .sql(SELECT)
                .param("userId", userId)
                .param("key", key)
                .query(
                        (resultSet, rowNumber) ->
                                new StoredRecord(
                                        resultSet.getString("request_hash"),
                                        (Integer) resultSet.getObject("response_status"),
                                        resultSet.getString("response_body"),
                                        resultSet
                                                .getObject("expires_at", OffsetDateTime.class)
                                                .toInstant()))
                .optional();
    }

    private <T> IdempotentResult<T> replayOrReject(
            StoredRecord stored, String requestHash, Class<T> responseType) {
        if (!stored.requestHash().equals(requestHash)) {
            throw new ConflictException(
                    ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "idempotency key was used with a different request");
        }
        Integer status = stored.responseStatus();
        if (status == null) {
            throw new ConflictException(
                    ErrorCode.IDEMPOTENCY_IN_PROGRESS, "the same request is still in progress");
        }
        String body = stored.responseBody();
        T value = body == null ? null : jsonMapper.readValue(body, responseType);
        return new IdempotentResult<>(
                ResponseEntity.status(HttpStatusCode.valueOf(status)).body(value), true);
    }

    private <T> void saveResponse(UUID userId, String key, ResponseEntity<T> response) {
        T body = response.getBody();
        String json = body == null ? null : jsonMapper.writeValueAsString(body);
        requiresNew.executeWithoutResult(
                status ->
                        jdbcClient
                                .sql(SAVE_RESPONSE)
                                .param("status", response.getStatusCode().value())
                                .param("body", json)
                                .param("userId", userId)
                                .param("key", key)
                                .update());
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record StoredRecord(
            String requestHash,
            @Nullable Integer responseStatus,
            @Nullable String responseBody,
            Instant expiresAt) {}
}
