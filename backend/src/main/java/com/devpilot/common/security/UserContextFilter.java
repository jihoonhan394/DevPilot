package com.devpilot.common.security;

import com.devpilot.common.error.DevPilotException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.ProblemResponseWriter;
import com.devpilot.common.logging.UserRefCalculator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 인증 다음, 컨트롤러 전 (docs/03 §4.1 3-4, docs/05 §1.4.3 5단계).
 *
 * <ol>
 *   <li>{@code sub}이 UUID가 아니면 401 {@code AUTHENTICATION_REQUIRED}(두 auth-mode 공통, docs/09 §6.2
 *       J-16·J-17).
 *   <li>{@link AuthenticatedUserResolver}로 사용자 조회·JIT 생성·allowlist 재확인 → 거부 시 403 {@code
 *       USER_NOT_ALLOWED}.
 *   <li>{@code DELETION_REQUESTED} 사용자는 {@code GET /me}, {@code DELETE /me} 외 403 {@code
 *       FORBIDDEN}.
 *   <li>{@link CurrentUser}를 요청 속성에, {@code userRef}를 MDC에 둔다(요청 끝에 제거).
 * </ol>
 *
 * Spring bean이 아니다 — {@code SecurityConfig}가 만들어 security chain에만 넣는다(서블릿 필터로 두 번 등록되지 않게).
 */
public class UserContextFilter extends OncePerRequestFilter {

    /** {@link CurrentUser} 요청 속성 이름. */
    public static final String CURRENT_USER_ATTRIBUTE = CurrentUser.class.getName();

    /** MDC 키 (docs/03 §8). */
    public static final String USER_REF_MDC_KEY = "userRef";

    private static final String ME_PATH = "/api/v1/me";
    private static final String API_PREFIX = "/api/";

    private final AuthenticatedUserResolver resolver;
    private final ProblemResponseWriter problemResponseWriter;
    private final UserRefCalculator userRefCalculator;

    public UserContextFilter(
            AuthenticatedUserResolver resolver,
            ProblemResponseWriter problemResponseWriter,
            UserRefCalculator userRefCalculator) {
        this.resolver = resolver;
        this.problemResponseWriter = problemResponseWriter;
        this.userRefCalculator = userRefCalculator;
    }

    /** 사용자 context는 API 요청에만 필요하다. actuator 등은 건너뛴다(없는 경로는 404 그대로). */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(API_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            filterChain.doFilter(request, response);
            return;
        }
        Jwt jwt = jwtAuthentication.getToken();
        if (!isCanonicalUuid(jwt.getSubject())) {
            problemResponseWriter.write(request, response, ErrorCode.AUTHENTICATION_REQUIRED);
            return;
        }
        CurrentUser currentUser;
        try {
            currentUser = resolver.resolve(jwt);
        } catch (DevPilotException exception) {
            problemResponseWriter.write(request, response, exception.errorCode());
            return;
        }
        if (currentUser.deletionRequested() && !isAllowedWhileDeletionRequested(request)) {
            problemResponseWriter.write(request, response, ErrorCode.FORBIDDEN);
            return;
        }
        request.setAttribute(CURRENT_USER_ATTRIBUTE, currentUser);
        MDC.put(USER_REF_MDC_KEY, userRefCalculator.userRef(currentUser.userId()));
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(USER_REF_MDC_KEY);
        }
    }

    /** {@code GET /me}, {@code DELETE /me}만 허용 (docs/05 §1.4.2). */
    private static boolean isAllowedWhileDeletionRequested(HttpServletRequest request) {
        String method = request.getMethod();
        return ME_PATH.equals(request.getRequestURI())
                && ("GET".equals(method) || "DELETE".equals(method));
    }

    private static boolean isCanonicalUuid(@Nullable String subject) {
        if (subject == null) {
            return false;
        }
        try {
            return UUID.fromString(subject).toString().equals(subject.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
