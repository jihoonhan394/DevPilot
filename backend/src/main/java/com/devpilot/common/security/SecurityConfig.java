package com.devpilot.common.security;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.ProblemResponseWriter;
import com.devpilot.common.logging.UserRefCalculator;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 필터 체인 (docs/03 §4.1, §4.2). stateless JWT, CSRF 비활성, 인증 실패는 401 {@code
 * AUTHENTICATION_REQUIRED} Problem Details. JWT decoder는 auth-mode가 고른다: devtoken은 {@link
 * DevTokenConfig}, supabase(Later)는 Spring Boot의 JWKS 기반 자동 설정.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String[] API_DOCS_PATHS = {
        "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui/**", "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            DevPilotProperties properties,
            Environment environment,
            ProblemResponseWriter problemResponseWriter,
            AuthenticatedUserResolver authenticatedUserResolver,
            UserRefCalculator userRefCalculator) {
        boolean devtoken = properties.security().authMode() == DevPilotProperties.AuthMode.DEVTOKEN;
        boolean local = environment.acceptsProfiles(Profiles.of("local"));
        AuthenticationEntryPoint entryPoint =
                (request, response, exception) ->
                        problemResponseWriter.write(
                                request, response, ErrorCode.AUTHENTICATION_REQUIRED);
        AccessDeniedHandler accessDeniedHandler =
                (request, response, exception) ->
                        problemResponseWriter.write(request, response, ErrorCode.FORBIDDEN);

        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        authorize -> {
                            authorize.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                            authorize
                                    .requestMatchers(
                                            HttpMethod.GET,
                                            "/actuator/health",
                                            "/actuator/health/**")
                                    .permitAll();
                            if (devtoken) {
                                authorize
                                        .requestMatchers(HttpMethod.POST, "/api/v1/dev/token")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/v1/dev/jwks.json")
                                        .permitAll();
                            }
                            if (local) {
                                authorize.requestMatchers(API_DOCS_PATHS).permitAll();
                            }
                            authorize.anyRequest().authenticated();
                        })
                .oauth2ResourceServer(
                        resourceServer ->
                                resourceServer
                                        .jwt(Customizer.withDefaults())
                                        .authenticationEntryPoint(entryPoint)
                                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(entryPoint)
                                        .accessDeniedHandler(accessDeniedHandler));

        // 3-4 UserContextFilter: JWT 인증 직후 (docs/03 §4.1). RateLimitFilter(3-3)는 S2(BL-SEC-11)에 이
        // 앞에 들어온다
        http.addFilterAfter(
                new UserContextFilter(
                        authenticatedUserResolver, problemResponseWriter, userRefCalculator),
                BearerTokenAuthenticationFilter.class);

        List<String> origins = properties.web().corsAllowedOrigins();
        if (origins.isEmpty()) {
            http.cors(AbstractHttpConfigurer::disable);
        } else {
            http.cors(cors -> cors.configurationSource(corsConfigurationSource(origins)));
        }
        return http.build();
    }

    /** local profile 전용 (운영은 same-origin, docs/05 §1.1, docs/07 §9.1). */
    private static CorsConfigurationSource corsConfigurationSource(List<String> origins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Trace-Id"));
        configuration.setExposedHeaders(
                List.of("X-Trace-Id", "Retry-After", "Idempotent-Replayed"));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
