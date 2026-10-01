package com.devpilot.today.presentation;

import com.devpilot.common.idempotency.IdempotencyService;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.today.application.LessonListView;
import com.devpilot.today.application.LessonQueryService;
import com.devpilot.today.application.LessonQueryService.AnswerResult;
import com.devpilot.today.application.LessonQueryService.CompleteResult;
import com.devpilot.today.application.LessonQueryService.FinishResult;
import com.devpilot.today.application.LessonQueryService.PredictResult;
import com.devpilot.today.application.LessonReexplainService;
import com.devpilot.today.application.LessonReexplainService.ReexplainView;
import com.devpilot.today.application.LessonView;
import com.devpilot.today.domain.ConfusionReason;
import com.devpilot.today.domain.HelpLevel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개념 노트 (docs/05 §21, BL-LSN-02~04). 가르치는 화면이 쓰는 endpoint다.
 *
 * <p>본문은 콘텐츠이므로 모든 사용자가 같은 것을 본다 — 소유권 검사가 없다. 사용자별인 것은 단위 진행(`progress`)과 마침 기록뿐이다. <b>AI를 부르지
 * 않고</b>, 백지 문제의 답은 서버로 받지 않는다(docs/05 §21.6).
 */
@RestController
@RequestMapping("/api/v1/lessons")
@Tag(name = "today")
public class LessonController {

    /** docs/05 §21: `LESSON.<주제>[.<하위>].<번호>` */
    private static final String LESSON_KEY =
            "^LESSON\\.[A-Z][A-Z0-9_]*(\\.[A-Z][A-Z0-9_]*)*\\.[0-9]{3}$";

    /** docs/05 §21: `<노트 key>.U<n>` */
    private static final String UNIT_KEY =
            "^LESSON\\.[A-Z][A-Z0-9_]*(\\.[A-Z][A-Z0-9_]*)*\\.[0-9]{3}\\.U[0-9]{1,2}$";

    private final LessonQueryService lessonQueryService;
    private final LessonReexplainService lessonReexplainService;
    private final IdempotencyService idempotencyService;
    private final Clock clock;

    public LessonController(
            LessonQueryService lessonQueryService,
            LessonReexplainService lessonReexplainService,
            IdempotencyService idempotencyService,
            Clock clock) {
        this.lessonQueryService = lessonQueryService;
        this.lessonReexplainService = lessonReexplainService;
        this.idempotencyService = idempotencyService;
        this.clock = clock;
    }

    /** 노트 목록 (docs/05 §21.9). 앱을 열었을 때 이어서 할 노트가 맨 위에 온다. */
    @GetMapping
    @Operation(operationId = "lessonList")
    public LessonListView list(CurrentUser currentUser) {
        return lessonQueryService.list(currentUser.userId());
    }

    /**
     * 설명을 다른 각도로 한 번 더 (docs/05 §21.10, ADR-047). 상태를 바꾸지 않으므로 {@code Idempotency-Key}가 없고, 저장하는 것도
     * 없다.
     */
    @PostMapping("/{lessonKey}/units/{unitKey}/reexplain")
    @Operation(operationId = "lessonReexplain")
    public ReexplainView reexplain(
            CurrentUser currentUser,
            @PathVariable @Pattern(regexp = LESSON_KEY) @Size(max = 140) String lessonKey,
            @PathVariable @Pattern(regexp = UNIT_KEY) @Size(max = 140) String unitKey,
            @Valid @RequestBody ReexplainRequest request) {
        return lessonReexplainService.reexplain(
                currentUser.userId(), lessonKey, unitKey, request.reason());
    }

    /** docs/05 §21.10. 자유 입력을 받지 않는다 — 고정 선택지 하나뿐이다(ADR-047). */
    public record ReexplainRequest(@NotNull ConfusionReason reason) {}

    @GetMapping("/{lessonKey}")
    @Operation(operationId = "lessonGet")
    public LessonView get(
            CurrentUser currentUser,
            @PathVariable @Pattern(regexp = LESSON_KEY) @Size(max = 140) String lessonKey) {
        return lessonQueryService.get(currentUser.userId(), lessonKey);
    }

    @PostMapping("/{lessonKey}/units/{unitKey}/predict")
    @Operation(operationId = "lessonCheckPredict")
    public PredictResult predict(
            @PathVariable @Pattern(regexp = LESSON_KEY) @Size(max = 140) String lessonKey,
            @PathVariable @Pattern(regexp = UNIT_KEY) @Size(max = 140) String unitKey,
            @Valid @RequestBody PredictRequest request) {
        return lessonQueryService.checkPredict(lessonKey, unitKey, request.answer());
    }

    @PostMapping("/{lessonKey}/units/{unitKey}/complete")
    @Operation(operationId = "lessonCheckComplete")
    public CompleteResult complete(
            @PathVariable @Pattern(regexp = LESSON_KEY) @Size(max = 140) String lessonKey,
            @PathVariable @Pattern(regexp = UNIT_KEY) @Size(max = 140) String unitKey,
            @Valid @RequestBody CompleteRequest request) {
        int blanks = lessonQueryService.blanks(lessonKey, unitKey);
        if (request.answers().size() != blanks) {
            throw LessonRequests.answersSizeMismatch(blanks);
        }
        return lessonQueryService.checkComplete(lessonKey, unitKey, request.answers());
    }

    /** 모범 답안. 상태를 바꾸지 않으므로 GET이다 (docs/05 §21.6). */
    @GetMapping("/{lessonKey}/units/{unitKey}/answer")
    @Operation(operationId = "lessonGetAnswer")
    public AnswerResult answer(
            @PathVariable @Pattern(regexp = LESSON_KEY) @Size(max = 140) String lessonKey,
            @PathVariable @Pattern(regexp = UNIT_KEY) @Size(max = 140) String unitKey) {
        return lessonQueryService.answer(lessonKey, unitKey);
    }

    @PostMapping("/{lessonKey}/units/{unitKey}/finish")
    @Operation(operationId = "lessonFinishUnit")
    public ResponseEntity<FinishResult> finish(
            CurrentUser currentUser,
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @PathVariable @Pattern(regexp = LESSON_KEY) @Size(max = 140) String lessonKey,
            @PathVariable @Pattern(regexp = UNIT_KEY) @Size(max = 140) String unitKey,
            @Valid @RequestBody FinishRequest request,
            HttpServletRequest httpRequest) {
        Integer met = request.selfChecksMet();
        if (met != null && met > lessonQueryService.selfCheckCount(lessonKey, unitKey)) {
            throw LessonRequests.selfChecksTooMany(
                    lessonQueryService.selfCheckCount(lessonKey, unitKey));
        }
        return idempotencyService.execute(
                currentUser.userId(),
                idempotencyKey,
                httpRequest,
                request,
                FinishResult.class,
                () ->
                        ResponseEntity.ok(
                                lessonQueryService.finish(
                                        currentUser.userId(),
                                        lessonKey,
                                        unitKey,
                                        request.helpLevel(),
                                        met,
                                        PlanDayCalculator.planDate(
                                                clock.instant(),
                                                currentUser.zoneId(),
                                                currentUser.dayStartHour()))));
    }

    /** 출력 예측 답 (docs/05 §21.4). */
    public record PredictRequest(@NotBlank @Size(max = 200) String answer) {}

    /** 빈칸 답 (docs/05 §21.5). 개수는 그 단위의 빈칸 수와 같아야 한다. */
    public record CompleteRequest(
            @NotEmpty @Size(max = 4) List<@NotNull @Size(max = 200) String> answers) {}

    /** 단위를 마쳤다 (docs/05 §21.7). 사용자가 쓴 답은 보내지 않는다. */
    public record FinishRequest(
            @NotNull HelpLevel helpLevel, @PositiveOrZero @Nullable Integer selfChecksMet) {}
}
