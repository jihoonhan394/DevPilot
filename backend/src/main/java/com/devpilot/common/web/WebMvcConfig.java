package com.devpilot.common.web;

import com.devpilot.common.security.CurrentUserArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** MVC 공통 설정: 온보딩 가드와 {@code CurrentUser} 인자 주입 (docs/03 §3.1, §4.1). */
@Configuration(proxyBeanMethods = false)
public class WebMvcConfig implements WebMvcConfigurer {

    private final OnboardingRequiredInterceptor onboardingRequiredInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    public WebMvcConfig(
            OnboardingRequiredInterceptor onboardingRequiredInterceptor,
            CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.onboardingRequiredInterceptor = onboardingRequiredInterceptor;
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(onboardingRequiredInterceptor).addPathPatterns("/api/v1/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
