package com.devpilot.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.UnitTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@UnitTest
class DevTokenServiceTest {

    /** 기대값은 Python {@code uuid.uuid5(uuid.NAMESPACE_DNS, email)}로 계산했다. */
    @Test
    void shouldDeriveUuidV5FromEmailWhenIssuingSubject() {
        assertThat(DevTokenService.subjectFor("owner@devpilot.test"))
                .isEqualTo(UUID.fromString("159725c9-9f1f-5187-844e-f474bd7e29b1"));
        assertThat(DevTokenService.subjectFor("invited@devpilot.test"))
                .isEqualTo(UUID.fromString("9443e7fd-a827-537c-8198-54f70d8d3278"));
    }

    @Test
    void shouldProduceVersion5AndRfcVariant() {
        UUID subject = DevTokenService.subjectFor("someone@example.invalid");

        assertThat(subject.version()).isEqualTo(5);
        assertThat(subject.variant()).isEqualTo(2);
    }
}
