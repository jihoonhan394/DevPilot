package com.devpilot.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 모든 DB 테스트가 공유하는 {@code postgres:16} 컨테이너 (docs/09 §2.1). 개발·운영 DB와 같은 major다. 로컬 Docker가 없으면 서버
 * Docker를 SSH 터널로 쓴다(docs/18 §3.1: DOCKER_HOST, TESTCONTAINERS_HOST_OVERRIDE).
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
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return POSTGRES;
    }

    /** 스키마 스냅샷 비교처럼 같은 서버에 DB를 더 만들 때 쓴다. */
    public static PostgreSQLContainer container() {
        return POSTGRES;
    }
}
