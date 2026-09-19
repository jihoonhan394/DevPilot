package com.devpilot.common.web.validation;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * 서비스 계층 도메인 검사에서 쓰는 입력 형식 규칙 (docs/03 §3.1 {@code common.web.validation}, docs/05 §1.2.3). 응답
 * field code는 호출하는 서비스가 정한다({@code TIMEZONE_INVALID}, {@code URL}).
 */
public final class InputRules {

    private static final Set<String> HTTP_SCHEMES = Set.of("http", "https");

    private InputRules() {}

    /**
     * IANA region ID인가 ({@code ZoneId.getAvailableZoneIds()}). {@code +09:00} 같은 offset ID는 거부한다.
     */
    public static boolean isIanaRegionId(@Nullable String value) {
        return value != null && ZoneId.getAvailableZoneIds().contains(value);
    }

    /**
     * 저장만 하는 URL 필드 규칙 (docs/07 §5.5): {@code java.net.URI} 파싱 성공, scheme {@code http}/{@code
     * https}, host 존재. 서버는 이 URL을 요청하지 않는다.
     */
    public static boolean isHttpUrl(@Nullable String value) {
        if (value == null) {
            return false;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            return scheme != null
                    && HTTP_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))
                    && host != null
                    && !host.isBlank();
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
