package com.devpilot.common.config;

import com.devpilot.common.security.CurrentUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 문서 공통 규약 (docs/05 §18): {@code bearerAuth} 전역 적용, 모든 operation의 {@code default} 응답 =
 * {@code application/problem+json} {@code ProblemDetail}, 응답 schema의 필드는 모두 required, 서버 주소 고정(스냅샷
 * 안정성).
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";
    private static final String PROBLEM_DETAIL = "ProblemDetail";
    private static final String FIELD_ERROR = "FieldError";
    private static final String PROBLEM_JSON = "application/problem+json";
    private static final Set<String> RESPONSE_RECORDS = Set.of("SkillRef", "AxisLevels");

    static {
        // 컨트롤러 파라미터 CurrentUser는 UserContextFilter가 채운다. 요청 파라미터로 문서화하지 않는다
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(CurrentUser.class);
    }

    @Bean
    public OpenAPI devPilotOpenApi() {
        return new OpenAPI()
                .info(new Info().title("DevPilot API").version("v1"))
                .servers(List.of(new Server().url("/")))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        BEARER_AUTH,
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    /** 모든 operation에 default 오류 응답을 붙이고 응답 schema 필드를 required로 표시한다. */
    @Bean
    public OpenApiCustomizer devPilotOpenApiCustomizer() {
        return openApi -> {
            addDefaultProblemResponses(openApi);
            addCommonSchemas(openApi);
            markResponseFieldsRequired(openApi);
        };
    }

    private static void addDefaultProblemResponses(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }
        for (PathItem pathItem : openApi.getPaths().values()) {
            for (Operation operation : pathItem.readOperations()) {
                operation.getResponses().addApiResponse("default", problemResponse());
            }
        }
    }

    /** springdoc이 components.schemas를 다시 만들므로 공통 schema는 customizer에서 넣는다. */
    private static void addCommonSchemas(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        openApi.getComponents().addSchemas(FIELD_ERROR, fieldErrorSchema());
        openApi.getComponents().addSchemas(PROBLEM_DETAIL, problemDetailSchema());
    }

    /**
     * 응답 필드는 null 포함 항상 존재한다 (docs/05 §18 nullable). 응답 schema = 이름이 {@code Response}·{@code View}로
     * 끝나거나 {@code CursorPage}로 시작하는 것, 그리고 공통 응답 record {@code SkillRef}·{@code AxisLevels}.
     */
    private static void markResponseFieldsRequired(OpenAPI openApi) {
        for (String name : openApi.getComponents().getSchemas().keySet()) {
            Schema<?> schema = openApi.getComponents().getSchemas().get(name);
            if (isResponseSchema(name) && schema.getProperties() != null) {
                schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
            }
        }
    }

    private static boolean isResponseSchema(String name) {
        return name.endsWith("Response")
                || name.endsWith("View")
                || name.startsWith("CursorPage")
                || RESPONSE_RECORDS.contains(name);
    }

    private static ApiResponse problemResponse() {
        return new ApiResponse()
                .description("Problem Details (docs/05 §1.2)")
                .content(
                        new Content()
                                .addMediaType(
                                        PROBLEM_JSON,
                                        new MediaType()
                                                .schema(
                                                        new Schema<>()
                                                                .$ref(
                                                                        "#/components/schemas/"
                                                                                + PROBLEM_DETAIL))));
    }

    private static Schema<?> fieldErrorSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("field", new StringSchema());
        schema.addProperty("code", new StringSchema());
        schema.addProperty("message", new StringSchema());
        schema.setRequired(List.of("field", "code", "message"));
        return schema;
    }

    private static Schema<?> problemDetailSchema() {
        ArraySchema errors = new ArraySchema();
        errors.setItems(new Schema<>().$ref("#/components/schemas/" + FIELD_ERROR));
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("type", new StringSchema());
        schema.addProperty("title", new StringSchema());
        schema.addProperty("status", new IntegerSchema());
        schema.addProperty("detail", new StringSchema());
        schema.addProperty("instance", new StringSchema());
        schema.addProperty("code", new StringSchema());
        schema.addProperty("traceId", new StringSchema());
        schema.addProperty("errors", errors);
        schema.setRequired(
                List.of(
                        "type",
                        "title",
                        "status",
                        "detail",
                        "instance",
                        "code",
                        "traceId",
                        "errors"));
        return schema;
    }
}
