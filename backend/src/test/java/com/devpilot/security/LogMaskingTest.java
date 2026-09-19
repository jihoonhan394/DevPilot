package com.devpilot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

/**
 * docs/07 §6.1·§16 ST-23 (BL-SEC-06): 로그에 Authorization·토큰·이메일·요청 body·캘린더 토큰이 없고, 사용자는 {@code
 * userRef}(12 hex)로만 남는다. 운영과 같은 INFO 레벨로 모든 로거(root)를 잡아 확인한다.
 */
@IntegrationTest
class LogMaskingTest extends ApiTestSupport {

    private static final Pattern USER_REF = Pattern.compile("userRef=([0-9a-f]+)");

    @Test
    void shouldKeepSecretsAndPersonalDataOutOfLogs() throws Exception {
        TestUser owner = TestUser.owner();
        TestUser stranger = TestUser.stranger();
        String ownerToken = api.token(owner);
        String calendarToken = "calendarSecretToken0123456789";
        Map<String, Object> onboarding = TestApi.onboardingRequest();
        Map<String, Object> sideProject = TestApi.sideProjectRequest("로그비밀프로젝트");
        sideProject.put("description", "로그에 남으면 안 되는 설명 문장");
        onboarding.put("sideProject", sideProject);

        String logs;
        try (RootLogCapture capture = RootLogCapture.start()) {
            api.onboard(owner, onboarding);
            api.get(stranger, "/api/v1/me");
            mockMvc.perform(
                    get("/api/v1/calendar/" + calendarToken + ".ics")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken));
            mockMvc.perform(
                    get("/api/v1/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer broken.token.value"));
            logs = capture.text();
        }

        assertThat(logs).isNotBlank();
        assertThat(logs)
                .doesNotContain(ownerToken)
                .doesNotContain("Bearer ")
                .doesNotContain("broken.token.value")
                .doesNotContain(owner.email())
                .doesNotContain(stranger.email())
                .doesNotContain(owner.sub().toString())
                .doesNotContain(stranger.sub().toString())
                .doesNotContain(calendarToken)
                .doesNotContain("로그비밀프로젝트")
                .doesNotContain("로그에 남으면 안 되는 설명 문장");
        UUID ownerId = userId(owner);
        assertThat(logs).doesNotContain(ownerId.toString());
        Matcher matcher = USER_REF.matcher(logs);
        int refs = 0;
        while (matcher.find()) {
            assertThat(matcher.group(1)).hasSize(12);
            refs++;
        }
        assertThat(refs).isPositive();
    }

    @Test
    void shouldMaskCalendarTokenInProblemInstance() throws Exception {
        String calendarToken = "anotherCalendarToken987";

        mockMvc.perform(get("/api/v1/calendar/" + calendarToken + ".ics"))
                .andExpect(jsonPath("$.instance").value("/api/v1/calendar/****.ics"));
    }

    /** root 로거 전체 수집. 끝나면 원래 레벨로 되돌린다. */
    private static final class RootLogCapture implements AutoCloseable {

        private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        private final Level previousLevel = root.getLevel();
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        static RootLogCapture start() {
            RootLogCapture capture = new RootLogCapture();
            capture.appender.start();
            capture.root.addAppender(capture.appender);
            capture.root.setLevel(Level.INFO);
            return capture;
        }

        String text() {
            StringBuilder text = new StringBuilder();
            for (ILoggingEvent event : List.copyOf(appender.list)) {
                text.append(event.getLoggerName()).append(' ').append(event.getFormattedMessage());
                if (event.getKeyValuePairs() != null) {
                    event.getKeyValuePairs()
                            .forEach(
                                    pair ->
                                            text.append(' ')
                                                    .append(pair.key)
                                                    .append('=')
                                                    .append(pair.value));
                }
                event.getMDCPropertyMap()
                        .forEach(
                                (key, value) ->
                                        text.append(' ').append(key).append('=').append(value));
                for (IThrowableProxy proxy = event.getThrowableProxy();
                        proxy != null;
                        proxy = proxy.getCause()) {
                    text.append(' ').append(proxy.getMessage());
                }
                text.append('\n');
            }
            return text.toString();
        }

        @Override
        public void close() {
            root.detachAppender(appender);
            root.setLevel(previousLevel);
            appender.stop();
        }
    }
}
