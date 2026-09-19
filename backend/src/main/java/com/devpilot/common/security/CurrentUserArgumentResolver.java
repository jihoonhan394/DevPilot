package com.devpilot.common.security;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 컨트롤러 파라미터 {@code CurrentUser currentUser} 주입 (docs/03 §3.1). 값은 {@link UserContextFilter}가 요청 속성에
 * 둔다. 인증된 요청에만 쓰는 파라미터이므로 값이 없으면 설정 오류다.
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            @Nullable ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory) {
        Object value =
                webRequest.getAttribute(
                        UserContextFilter.CURRENT_USER_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (value instanceof CurrentUser currentUser) {
            return currentUser;
        }
        throw new IllegalStateException("CurrentUser is not available for this request");
    }
}
