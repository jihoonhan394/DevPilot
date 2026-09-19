package com.devpilot.common.web;

import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.security.UserContextFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 온보딩 전 사용자의 요청을 409 {@code ONBOARDING_REQUIRED}로 막는다 (docs/05 §1.4.4, docs/07 §4.7). 컨트롤러 인자 해석 전에
 * 실행되므로 IK·body 검사보다 먼저다(docs/05 §1.4.3 6단계). 인증되지 않은 요청과 컨트롤러가 아닌 handler(없는 경로)는 건드리지 않는다.
 */
@Component
public class OnboardingRequiredInterceptor implements HandlerInterceptor {

    /** 온보딩 전에도 허용하는 {@code (method, path)} (docs/05 §1.4.4). */
    private static final Set<String> ALLOWED_BEFORE_ONBOARDING =
            Set.of(
                    "GET /api/v1/me",
                    "PATCH /api/v1/me",
                    "DELETE /api/v1/me",
                    "GET /api/v1/me/export",
                    "POST /api/v1/onboarding",
                    "GET /api/v1/skills/tree");

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        Object attribute = request.getAttribute(UserContextFilter.CURRENT_USER_ATTRIBUTE);
        if (!(attribute instanceof CurrentUser currentUser) || currentUser.onboardingCompleted()) {
            return true;
        }
        if (ALLOWED_BEFORE_ONBOARDING.contains(
                request.getMethod() + " " + request.getRequestURI())) {
            return true;
        }
        throw new ConflictException(
                ErrorCode.ONBOARDING_REQUIRED, "onboarding is required before this request");
    }
}
