package com.devpilot.testsupport;

import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 모든 DB 테스트가 공유하는 {@code postgres:16} 컨테이너 (docs/09 §2.1). 개발·운영 DB와 같은 major다. 로컬 Docker가 없으면 서버
 * Docker를 SSH 터널로 쓴다(docs/18 §3.1: DOCKER_HOST, TESTCONTAINERS_HOST_OVERRIDE).
 *
 * <p><b>컨테이너를 bean으로 두지 않는 이유</b> — 컨테이너를 {@code @Bean}(+{@code @ServiceConnection})으로 두면 Spring이
 * <b>context를 닫을 때 컨테이너도 멈춘다</b>(reuse가 켜져 있을 때만 예외인데, reuse는 개발자 PC의 {@code
 * ~/.testcontainers.properties}에 달려 있어 CI에는 없다). 그래서 context 하나가 기동에 실패하면 Spring이 그 context를 닫으며
 * 컨테이너까지 멈추고, 이후의 모든 테스트가 {@code ConnectException}으로 무너진다(2026-09-21에 실제로 겪었다 — 진짜 실패 1건이 288건으로
 * 보였다). 컨테이너는 JVM 수명 동안 static으로 살리고, context에는 접속 정보만 bean으로 준다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfig {

    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16"))
                    .withDatabaseName("devpilot")
                    .withReuse(true); // ~/.testcontainers.properties 에서 reuse를 켠 경우에만 효과

    static {
        POSTGRES.start();
    }

    @Bean
    JdbcConnectionDetails jdbcConnectionDetails() {
        return new JdbcConnectionDetails() {

            @Override
            public String getJdbcUrl() {
                return POSTGRES.getJdbcUrl();
            }

            @Override
            public String getUsername() {
                return POSTGRES.getUsername();
            }

            @Override
            public String getPassword() {
                return POSTGRES.getPassword();
            }
        };
    }

    /** 스키마 스냅샷 비교처럼 같은 서버에 DB를 더 만들 때 쓴다. */
    public static PostgreSQLContainer container() {
        return POSTGRES;
    }
}
