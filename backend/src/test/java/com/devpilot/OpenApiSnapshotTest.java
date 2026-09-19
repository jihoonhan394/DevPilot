package com.devpilot;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.IntegrationTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * OpenAPI 스냅샷 생성 (docs/18 §4.3, DEC-12). {@code build/openapi/openapi.yaml}을 쓰고 {@code
 * openApiCheck}가 {@code docs/api/openapi.yaml}과 비교한다. test profile(auth-mode = supabase)이므로
 * devtoken 전용 endpoint는 스냅샷에 없다 (docs/05 §18).
 */
@IntegrationTest
@AutoConfigureMockMvc
class OpenApiSnapshotTest {

    private static final UUID OWNER_SUB = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired private MockMvc mockMvc;

    @Test
    void shouldWriteOpenApiYamlWhenDocsAreRequested() throws Exception {
        String yaml =
                mockMvc.perform(
                                get("/v3/api-docs.yaml")
                                        .with(
                                                jwt().jwt(
                                                                token ->
                                                                        token.subject(
                                                                                        OWNER_SUB
                                                                                                .toString())
                                                                                .claim(
                                                                                        "email",
                                                                                        "owner@devpilot.test"))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        write(yaml);
    }

    private static void write(String yaml) throws IOException {
        Path output =
                Path.of(
                        System.getProperty(
                                "devpilot.openapi.output", "build/openapi/openapi.yaml"));
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, yaml, StandardCharsets.UTF_8);
    }
}
