package com.devpilot.today.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.UnitTest;
import com.devpilot.today.domain.CuratedReading;
import com.devpilot.today.domain.CuratedRepo;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * docs/19 §3.8·§8.2 (BL-CNT-15): 등록은 key ASC로 하고, 은퇴한 reading도 조회된다. planner 후보({@code active()})에는
 * 은퇴한 것이 없다.
 */
@UnitTest
class CuratedReadingRegistryTest {

    private static final CuratedRepo REPO =
            new CuratedRepo(
                    "testrepo",
                    "Test Repository",
                    "https://repo.example.invalid/testrepo",
                    "",
                    "UNSPECIFIED",
                    null,
                    "Java 25",
                    "테스트용 저장소다.",
                    "git clone https://repo.example.invalid/testrepo.git",
                    "0123456789abcdef0123456789abcdef01234567");

    private final CuratedReadingRegistry registry = new CuratedReadingRegistry();

    @Test
    void shouldBeEmptyBeforeRegistration() {
        assertThat(registry.all()).isEmpty();
        assertThat(registry.active()).isEmpty();
        assertThat(registry.find("READ.TESTREPO.A.001")).isEmpty();
    }

    @Test
    void shouldKeepRetiredReadingResolvableButOutOfCandidates() {
        registry.register(
                List.of(
                        reading("READ.TESTREPO.B.001", false),
                        reading("READ.TESTREPO.A.001", true)));

        assertThat(registry.all())
                .extracting(CuratedReading::key)
                .containsExactly("READ.TESTREPO.A.001", "READ.TESTREPO.B.001");
        assertThat(registry.active())
                .extracting(CuratedReading::key)
                .containsExactly("READ.TESTREPO.B.001");
        assertThat(registry.find("READ.TESTREPO.A.001"))
                .get()
                .extracting(CuratedReading::retired)
                .isEqualTo(true);
    }

    @Test
    void shouldReplaceEverythingOnRegistration() {
        registry.register(List.of(reading("READ.TESTREPO.A.001", false)));
        registry.register(List.of(reading("READ.TESTREPO.B.001", false)));

        assertThat(registry.find("READ.TESTREPO.A.001")).isEmpty();
        assertThat(registry.all()).hasSize(1);
    }

    private static CuratedReading reading(String key, boolean retired) {
        return new CuratedReading(
                key,
                REPO,
                "src/main/java/example/OrderService.java",
                10,
                60,
                List.of("SPRING.TRANSACTION"),
                15,
                "이 서비스의 트랜잭션 경계가 어디에 생기는지 찾고 이유를 설명해 보세요.",
                List.of("트랜잭션이 시작되는 메서드"),
                retired);
    }
}
