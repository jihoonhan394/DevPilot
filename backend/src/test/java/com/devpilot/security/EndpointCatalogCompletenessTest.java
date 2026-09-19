package com.devpilot.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.IntegrationTest;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** docs/09 §9.3, docs/07 §16 ST-04: 모든 {@code /api/v1/**} 매핑이 격리 catalog 또는 제외 목록에 있다. */
@IntegrationTest
class EndpointCatalogCompletenessTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void shouldCoverEveryApiMappingWithIsolationCatalog() {
        Set<String> catalog = new HashSet<>(UserOwnedEndpoints.excludedMappings());
        UserOwnedEndpoints.all()
                .forEach(
                        endpoint ->
                                catalog.add(
                                        endpoint.method().name() + " " + endpoint.pathTemplate()));

        Set<String> mapped = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/api/v1/")) {
                    continue;
                }
                for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                    mapped.add(method.name() + " " + pattern);
                }
            }
        }

        assertThat(mapped).isNotEmpty();
        assertThat(catalog).containsAll(mapped);
    }
}
