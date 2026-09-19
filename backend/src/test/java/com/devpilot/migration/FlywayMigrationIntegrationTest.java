package com.devpilot.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.IntegrationTest;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * docs/09 §8.1: 빈 {@code postgres:16} DB에 Flyway 전체 적용 → {@code ddl-auto=validate}로 context 기동 성공(이
 * 테스트가 실행된다는 것 자체). 적용된 버전 목록이 {@code db/migration} 파일 목록과 같은지 확인한다.
 */
@IntegrationTest
class FlywayMigrationIntegrationTest {

    private static final Pattern VERSION = Pattern.compile("^V(\\d+)__.+\\.sql$");

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void shouldApplyEveryMigrationFileInOrder() throws IOException {
        List<String> applied =
                jdbcTemplate.queryForList(
                        "select version from devpilot.flyway_schema_history"
                                + " where success and version is not null order by installed_rank",
                        String.class);

        assertThat(applied).isEqualTo(migrationFileVersions());
    }

    private static List<String> migrationFileVersions() throws IOException {
        Resource[] resources =
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath:db/migration/V*.sql");
        return Arrays.stream(resources)
                .map(Resource::getFilename)
                .map(
                        name -> {
                            Matcher matcher = VERSION.matcher(String.valueOf(name));
                            assertThat(matcher.matches()).as(name).isTrue();
                            return matcher.group(1);
                        })
                .sorted(Comparator.comparingInt(Integer::parseInt))
                .toList();
    }
}
