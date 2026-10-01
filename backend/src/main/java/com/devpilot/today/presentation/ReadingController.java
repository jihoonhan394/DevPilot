package com.devpilot.today.presentation;

import com.devpilot.common.security.CurrentUser;
import com.devpilot.today.application.ReadingQueryService;
import com.devpilot.today.application.ReadingView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 읽기 자료 조회 (docs/05 §19.7, BL-TDY-16·BL-CNT-15): 코드 읽기({@code kind = CODE})와 개념 읽기({@code kind =
 * CONCEPT})를 한 endpoint가 돌려준다. 인증은 필요하지만 사용자 소유 리소스가 아니다. 코드 본문도 문서 본문도 반환하지 않고 서버는 저장소·문서 URL을
 * 요청하지 않는다(RC-4, docs/07 §5.5).
 */
@RestController
@RequestMapping("/api/v1/readings")
@Tag(name = "today")
public class ReadingController {

    private final ReadingQueryService readingQueryService;

    public ReadingController(ReadingQueryService readingQueryService) {
        this.readingQueryService = readingQueryService;
    }

    /** 내가 받은 적이 있는 읽기 (docs/05 §19.14). 최근 순, 본문은 없다. */
    @GetMapping
    @Operation(operationId = "todayListReadings")
    public ReadingHistoryResponse list(
            CurrentUser currentUser,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return new ReadingHistoryResponse(readingQueryService.history(currentUser.userId(), limit));
    }

    @GetMapping("/{readingKey}")
    @Operation(operationId = "todayGetReading")
    public ReadingView get(
            @PathVariable @Pattern(regexp = "^[A-Z0-9][A-Z0-9_.]{2,149}$") String readingKey) {
        return readingQueryService.get(readingKey);
    }
}
