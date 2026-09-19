package com.devpilot.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * MVC 밖(서블릿 필터, Spring Security entry point)에서 Problem Details 응답을 쓴다. 본문 형식은
 * GlobalExceptionHandler와 같다 (docs/05 §1.2.1).
 */
@Component
public class ProblemResponseWriter {

    private final ProblemDetailFactory problemDetailFactory;
    private final JsonMapper jsonMapper;

    public ProblemResponseWriter(ProblemDetailFactory problemDetailFactory, JsonMapper jsonMapper) {
        this.problemDetailFactory = problemDetailFactory;
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        ProblemDetail problem = problemDetailFactory.create(request, errorCode);
        response.setStatus(errorCode.status());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (errorCode == ErrorCode.AUTHENTICATION_REQUIRED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        jsonMapper.writeValue(response.getOutputStream(), toBody(problem));
    }

    /** {@code properties}를 최상위로 펼친 순서 고정 본문 (MVC 변환기의 ProblemDetail mixin과 같은 모양). */
    static Map<String, Object> toBody(ProblemDetail problem) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", String.valueOf(problem.getType()));
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        body.put("instance", String.valueOf(problem.getInstance()));
        Map<String, Object> properties = problem.getProperties();
        if (properties != null) {
            body.putAll(properties);
        }
        return body;
    }
}
