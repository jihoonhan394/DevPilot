package com.devpilot.common.error;

import com.devpilot.common.web.SensitivePathMasker;
import com.devpilot.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * RFC 9457 Problem Details 본문을 만든다 (docs/05 §1.2.1). {@code type}·{@code title}은 {@link ErrorCode},
 * {@code detail}은 {@code messages_ko.properties}, 확장 속성은 {@code code}·{@code traceId}·{@code
 * errors}다. {@code instance}는 비밀 값을 가린 요청 경로다.
 */
@Component
public class ProblemDetailFactory {

    private static final Locale MESSAGE_LOCALE = Locale.KOREAN;

    private final MessageSource messageSource;

    public ProblemDetailFactory(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public ProblemDetail create(HttpServletRequest request, ErrorCode errorCode) {
        return create(request, errorCode, Map.of(), List.of());
    }

    public ProblemDetail create(
            HttpServletRequest request, ErrorCode errorCode, List<ApiFieldError> errors) {
        return create(request, errorCode, Map.of(), errors);
    }

    public ProblemDetail create(
            HttpServletRequest request,
            ErrorCode errorCode,
            Map<String, Object> args,
            List<ApiFieldError> errors) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatusCode.valueOf(errorCode.status()), detail(errorCode, args));
        problem.setType(URI.create(errorCode.type()));
        problem.setTitle(errorCode.title());
        problem.setInstance(URI.create(SensitivePathMasker.mask(request.getRequestURI())));
        problem.setProperty("code", errorCode.name());
        problem.setProperty("traceId", TraceIdFilter.currentTraceId(request));
        problem.setProperty("errors", withMessages(errors));
        return problem;
    }

    public String detail(ErrorCode errorCode) {
        return detail(errorCode, Map.of());
    }

    /** {@code error.<CODE>} 문구의 {@code {name}} 자리표시자를 {@code args}로 채운다. */
    public String detail(ErrorCode errorCode, Map<String, Object> args) {
        String message = messageSource.getMessage(errorCode.messageKey(), null, MESSAGE_LOCALE);
        for (Map.Entry<String, Object> arg : args.entrySet()) {
            message = message.replace("{" + arg.getKey() + "}", String.valueOf(arg.getValue()));
        }
        return message;
    }

    /** 임의 문구 키. 없으면 {@code error.<CODE>} 문구. */
    public String message(String key, ErrorCode fallback) {
        String message = messageSource.getMessage(key, null, null, MESSAGE_LOCALE);
        return message != null ? message : detail(fallback);
    }

    /** {@code validation.<code>} 문구. 없으면 {@code validation.DEFAULT}. */
    public String validationMessage(String code) {
        String fallback = messageSource.getMessage("validation.DEFAULT", null, MESSAGE_LOCALE);
        String message =
                messageSource.getMessage("validation." + code, null, fallback, MESSAGE_LOCALE);
        return message != null ? message : fallback;
    }

    private List<ApiFieldError> withMessages(List<ApiFieldError> errors) {
        List<ApiFieldError> resolved = new ArrayList<>(errors.size());
        for (ApiFieldError error : errors) {
            resolved.add(
                    error.message().isBlank()
                            ? new ApiFieldError(
                                    error.field(), error.code(), validationMessage(error.code()))
                            : error);
        }
        return List.copyOf(resolved);
    }
}
