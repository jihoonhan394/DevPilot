package com.devpilot;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** health만 인증 없이 열리고 상세·다른 actuator endpoint는 노출되지 않는다 (docs/03 §8, docs/10). */
@IntegrationTest
@AutoConfigureMockMvc
class ActuatorHealthIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void shouldExposeHealthWithoutDetailsWhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void shouldNotExposeOtherActuatorEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/env").with(jwt())).andExpect(status().isNotFound());
    }
}
