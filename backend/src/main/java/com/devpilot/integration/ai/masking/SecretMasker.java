package com.devpilot.integration.ai.masking;

import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.UnprocessableContentException;
import com.devpilot.common.logging.AuditEvent;
import com.devpilot.common.logging.AuditLogger;
import com.devpilot.common.logging.UserRefCalculator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 사용자 자유 텍스트의 secret 마스킹 (docs/17 §7, docs/05 §1.11, BL-AIP-09). 저장·AI 전송 전에 application service의 첫
 * 단계로 부른다. 처리 순서: P0(private key) 검사 → P1~P9 치환 → P10 치환. 치환 문자열은 {@code [REDACTED:<TYPE>]}이고, 이미
 * 치환된 토큰은 {@code [}로 시작하므로 뒤 패턴에 걸리지 않는다. 마스킹된 값·원문은 로그에 남기지 않는다.
 */
@Component
public class SecretMasker {

    private static final Pattern PRIVATE_KEY =
            Pattern.compile(
                    "-----BEGIN ((RSA|EC|DSA|OPENSSH|ENCRYPTED|PGP) )?PRIVATE KEY( BLOCK)?-----");
    private static final List<Rule> RULES =
            List.of(
                    new Rule(
                            "AWS_ACCESS_KEY",
                            Pattern.compile("(?<![A-Z0-9])(?:AKIA|ASIA)[0-9A-Z]{16}(?![A-Z0-9])"),
                            "[REDACTED:AWS_ACCESS_KEY]"),
                    new Rule(
                            "GITHUB_TOKEN",
                            Pattern.compile(
                                    "(?<![A-Za-z0-9_])(?:gh[pousr]_[A-Za-z0-9]{36,255}|github_pat_[A-Za-z0-9_]{22,255})(?![A-Za-z0-9_])"),
                            "[REDACTED:GITHUB_TOKEN]"),
                    new Rule(
                            "DEEPSEEK_API_KEY",
                            Pattern.compile("(?<![A-Za-z0-9_-])sk-[0-9a-f]{32}(?![A-Za-z0-9_-])"),
                            "[REDACTED:DEEPSEEK_API_KEY]"),
                    new Rule(
                            "SUPABASE_SECRET_KEY",
                            Pattern.compile("(?<![A-Za-z0-9_])sb_secret_[A-Za-z0-9_-]{20,}"),
                            "[REDACTED:SUPABASE_SECRET_KEY]"),
                    new Rule(
                            "JWT",
                            Pattern.compile(
                                    "(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]{8,}\\.eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]*"),
                            "[REDACTED:JWT]"),
                    new Rule(
                            "SLACK_TOKEN",
                            Pattern.compile("(?<![A-Za-z0-9_])xox[abprs]-[A-Za-z0-9-]{10,}"),
                            "[REDACTED:SLACK_TOKEN]"),
                    new Rule(
                            "GOOGLE_API_KEY",
                            Pattern.compile(
                                    "(?<![A-Za-z0-9_-])AIza[0-9A-Za-z_-]{35}(?![0-9A-Za-z_-])"),
                            "[REDACTED:GOOGLE_API_KEY]"),
                    new Rule(
                            "JDBC_PASSWORD",
                            Pattern.compile(
                                    "(?i)(jdbc:[^\\s'\"]*?[?;&](?:password|pwd)=)([^&;\\s'\"]+)"),
                            "$1[REDACTED:JDBC_PASSWORD]"),
                    new Rule(
                            "URL_PASSWORD",
                            Pattern.compile(
                                    "(?i)(\\b[a-z][a-z0-9+.-]*://[^\\s/:@'\"]+:)([^\\s/@'\"]+)(@)"),
                            "$1[REDACTED:URL_PASSWORD]$3"));
    private static final Pattern GENERIC_SECRET =
            Pattern.compile(
                    "(?i)((?:password|passwd|pwd|secret|api[_-]?key|access[_-]?key|private[_-]?key"
                        + "|client[_-]?secret|auth[_-]?token|access[_-]?token|refresh[_-]?token|token)"
                        + "[\"']?\\s*[:=]\\s*)([\"']?)([^\\s\"'`,;()\\[\\]{}<>]{8,})(?=[\"'`,;)\\]}\\s]|$)");
    private static final Pattern VARIABLE_NAME = Pattern.compile("^[a-z][A-Za-z0-9_]*$");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern FIELD_REFERENCE =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+$");
    private static final Pattern REPEATED_CHAR = Pattern.compile("^(.)\\1*$");
    private static final String GENERIC_TOKEN = "[REDACTED:GENERIC_SECRET]";

    private final AuditLogger auditLogger;
    private final UserRefCalculator userRefCalculator;

    public SecretMasker(AuditLogger auditLogger, UserRefCalculator userRefCalculator) {
        this.auditLogger = auditLogger;
        this.userRefCalculator = userRefCalculator;
    }

    /** 패턴 마스킹 (docs/17 §7.2). 순수 계산. */
    public MaskingResult mask(String text) {
        if (PRIVATE_KEY.matcher(text).find()) {
            return new MaskingResult(null, 0, true);
        }
        String masked = text;
        int count = 0;
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(masked);
            StringBuilder out = new StringBuilder(masked.length());
            int replaced = 0;
            while (matcher.find()) {
                matcher.appendReplacement(out, rule.replacement());
                replaced++;
            }
            if (replaced > 0) {
                matcher.appendTail(out);
                masked = out.toString();
                count += replaced;
            }
        }
        Matcher generic = GENERIC_SECRET.matcher(masked);
        StringBuilder out = new StringBuilder(masked.length());
        int replaced = 0;
        while (generic.find()) {
            String quote = generic.group(2);
            String value = generic.group(3);
            if (excluded(value, quote.isEmpty())) {
                generic.appendReplacement(out, Matcher.quoteReplacement(generic.group()));
            } else {
                generic.appendReplacement(
                        out, Matcher.quoteReplacement(generic.group(1) + quote + GENERIC_TOKEN));
                replaced++;
            }
        }
        generic.appendTail(out);
        if (replaced > 0) {
            masked = out.toString();
            count += replaced;
        }
        return new MaskingResult(masked, count, false);
    }

    /**
     * 저장 전 마스킹 (docs/05 §1.11). private key가 있으면 감사 이벤트 {@code SECRET_BLOCKED}(개수·type 없이 type만)를
     * 남기고 422 {@code SECRET_DETECTED_BLOCKED}.
     *
     * @param source docs/07 §6.3 {@code SECRET_BLOCKED.source} 값 (예: {@code RUBBER_DUCK_TURN})
     */
    public String maskOrReject(UUID userId, String source, String text) {
        MaskingResult result = mask(text);
        String masked = result.maskedText();
        if (result.blocked() || masked == null) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("userRef", userRefCalculator.userRef(userId));
            fields.put("source", source);
            fields.put("type", "PRIVATE_KEY");
            auditLogger.log(AuditEvent.SECRET_BLOCKED, fields);
            throw new UnprocessableContentException(
                    ErrorCode.SECRET_DETECTED_BLOCKED, "private key block detected");
        }
        return masked;
    }

    /** {@link #maskOrReject}의 nullable 판. null은 그대로 돌려준다. */
    public @Nullable String maskOrRejectNullable(
            UUID userId, String source, @Nullable String text) {
        return text == null ? null : maskOrReject(userId, source, text);
    }

    /** P10 제외 규칙 (docs/17 §7.2): 변수 이름, 필드 참조(따옴표 없는 값), 한 문자 반복. */
    private static boolean excluded(String value, boolean unquoted) {
        if (REPEATED_CHAR.matcher(value).matches()) {
            return true;
        }
        if (!unquoted) {
            return false;
        }
        boolean variable = VARIABLE_NAME.matcher(value).matches() && !DIGIT.matcher(value).find();
        return variable || FIELD_REFERENCE.matcher(value).matches();
    }

    private record Rule(String type, Pattern pattern, String replacement) {}
}
