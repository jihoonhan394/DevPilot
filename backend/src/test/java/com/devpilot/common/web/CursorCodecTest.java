package com.devpilot.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devpilot.common.error.BusinessValidationException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.testsupport.UnitTest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

/** docs/05 §1.6: 불투명 cursor {@code base64url({"v":1,"k":…,"id":…})}, 잘못된 값은 400 INVALID_CURSOR. */
@UnitTest
class CursorCodecTest {

    private static final UUID ID = UUID.fromString("0b5a2f0e-3c1d-4e7a-9f00-1a2b3c4d5e6f");

    private final CursorCodec codec = new CursorCodec(JsonMapper.builder().build());

    @Test
    void shouldRoundTripWhenSortKeyIsInstant() {
        Instant sortKey = Instant.parse("2026-10-05T10:00:00.123456Z");

        CursorCodec.Position<Instant> position = codec.decodeInstant(codec.encode(sortKey, ID));

        assertThat(position).isEqualTo(new CursorCodec.Position<>(sortKey, ID));
    }

    @Test
    void shouldRoundTripWhenSortKeyIsLong() {
        CursorCodec.Position<Long> position = codec.decodeLong(codec.encode(7L, ID));

        assertThat(position).isEqualTo(new CursorCodec.Position<>(7L, ID));
    }

    @Test
    void shouldUseUrlSafeAlphabetWithoutPadding() {
        assertThat(codec.encode(Instant.parse("2026-10-05T10:00:00Z"), ID))
                .matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    void shouldReturnNullWhenCursorIsAbsent() {
        assertThat(codec.decodeInstant(null)).isNull();
        assertThat(codec.decodeInstant("")).isNull();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
            strings = {
                "not-base64!",
                "e30",
                "eyJ2IjoyLCJrIjoiMjAyNi0xMC0wNVQxMDowMDowMFoiLCJpZCI6IjBiNWEyZjBlLTNjMWQtNGU3YS05ZjAwLTFhMmIzYzRkNWU2ZiJ9",
                "eyJ2IjoxLCJrIjoieWVzdGVyZGF5IiwiaWQiOiIwYjVhMmYwZS0zYzFkLTRlN2EtOWYwMC0xYTJiM2M0ZDVlNmYifQ",
                "eyJ2IjoxLCJrIjoiMjAyNi0xMC0wNVQxMDowMDowMFoiLCJpZCI6Im5vdC1hLXV1aWQifQ",
                "W10",
                "eyJ2IjoiMSIsImsiOiIyMDI2LTEwLTA1VDEwOjAwOjAwWiIsImlkIjoiMGI1YTJmMGUtM2MxZC00ZTdhLTlmMDAtMWEyYjNjNGQ1ZTZmIn0"
            })
    void shouldRejectWithInvalidCursorWhenPayloadIsMalformed(String cursor) {
        assertThatThrownBy(() -> codec.decodeInstant(cursor))
                .isInstanceOfSatisfying(
                        BusinessValidationException.class,
                        exception ->
                                assertThat(exception.errorCode())
                                        .isEqualTo(ErrorCode.INVALID_CURSOR));
    }

    @Test
    void shouldRejectWhenCursorIsTooLong() {
        String longCursor =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString("x".repeat(2_000).getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> codec.decodeInstant(longCursor))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void shouldRejectWhenLongCursorCarriesInstantKey() {
        String instantCursor = codec.encode(Instant.parse("2026-10-05T10:00:00Z"), ID);

        assertThatThrownBy(() -> codec.decodeLong(instantCursor))
                .isInstanceOf(BusinessValidationException.class);
    }
}
