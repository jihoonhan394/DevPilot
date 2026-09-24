package com.devpilot.today.application;

import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.web.AiMeta;
import com.devpilot.common.web.AiMeta.GuardActionView;
import com.devpilot.integration.ai.AiGateway;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.AiRequest;
import com.devpilot.integration.ai.api.AiResult;
import com.devpilot.integration.ai.api.GuardContext;
import com.devpilot.integration.ai.api.PromptValue;
import com.devpilot.integration.ai.api.TruncateMode;
import com.devpilot.integration.ai.api.Truncation;
import com.devpilot.integration.ai.api.UserContentBlock;
import com.devpilot.integration.ai.api.output.LessonReexplainOutput;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.today.domain.ConfusionReason;
import com.devpilot.today.domain.Lesson;
import com.devpilot.today.domain.LessonUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * 학습 단위 설명을 다른 각도로 한 번 더 (docs/05 §21.10, docs/17 §3.12, ADR-047).
 *
 * <p><b>아무것도 저장하지 않는다.</b> 응답으로만 보여 주므로 트랜잭션이 없고 학습 이벤트도 남기지 않는다 — 이건 배움의 증거가 아니라 읽기를 돕는 일이다.
 *
 * <p><b>백지 문제와 모범 답안을 AI에 보내지 않는다.</b> 보내지 않으면 흘릴 수 없다. 코드가 새는 것은 {@code CodeLeakGuard}가 막는다.
 */
@Service
public class LessonReexplainService {

    /** 설명이 길면 뒤쪽을 남긴다 — 결론이 뒤에 오는 글이 많다 (docs/17 §3.12). */
    private static final Truncation EXPLAIN = Truncation.of(1, TruncateMode.TAIL_CHARS, 1_200);

    private final LessonRegistry lessonRegistry;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final AiGateway aiGateway;

    LessonReexplainService(
            LessonRegistry lessonRegistry,
            SkillCatalogQueryService skillCatalogQueryService,
            AiGateway aiGateway) {
        this.lessonRegistry = lessonRegistry;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.aiGateway = aiGateway;
    }

    /** 없는 key면 404. AI 실패는 docs/05 §1.9대로 옮겨 던진다 — 저장한 것이 없으므로 되돌릴 것도 없다. */
    public ReexplainView reexplain(
            UUID userId, String lessonKey, String unitKey, ConfusionReason reason) {
        Lesson lesson =
                lessonRegistry
                        .find(lessonKey)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND, "lesson not found"));
        LessonUnit unit =
                lesson.unit(unitKey)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND, "unit not found"));
        Map<String, PromptValue> variables = new LinkedHashMap<>();
        variables.put("skillName", PromptValue.of(skillName(lesson)));
        variables.put("lessonOneLine", PromptValue.of(lesson.oneLine()));
        variables.put("unitTitle", PromptValue.of(unit.title()));
        variables.put("reason", PromptValue.of(reason.name()));
        List<UserContentBlock> content =
                List.of(UserContentBlock.text("explain", unit.explain(), EXPLAIN));
        AiResult<LessonReexplainOutput> result =
                aiGateway.call(
                        AiRequest.of(
                                AiOperation.LESSON_REEXPLAIN,
                                variables,
                                content,
                                LessonReexplainOutput.class,
                                userId,
                                GuardContext.empty()));
        if (!result.succeeded()) {
            throw result.toFailureException();
        }
        LessonReexplainOutput output = result.requireValue();
        return new ReexplainView(
                output.explanation(),
                output.analogy(),
                new AiMeta(
                        result.model(),
                        result.promptVersionLabel(AiOperation.LESSON_REEXPLAIN),
                        result.guardActions().stream()
                                .map(
                                        action ->
                                                new GuardActionView(
                                                        action.guard(),
                                                        action.action(),
                                                        action.detail()))
                                .toList()));
    }

    /**
     * {@code POST /lessons/{k}/units/{u}/reexplain} 응답 (docs/05 §21.10). {@code analogy}는 null일 수
     * 있다.
     */
    public record ReexplainView(String explanation, @Nullable String analogy, AiMeta aiMeta) {}

    /** 활성 skill 이름. catalog에서 사라졌으면 code를 그대로 쓴다 — 설명을 막을 이유는 없다. */
    private String skillName(Lesson lesson) {
        SkillRef skill =
                skillCatalogQueryService
                        .findActiveByCodes(List.of(lesson.skillCode()))
                        .get(lesson.skillCode());
        return skill == null ? lesson.skillCode() : skill.name();
    }
}
