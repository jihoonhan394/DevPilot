package com.devpilot.today.presentation;

import com.devpilot.today.application.CuratedReadingView;
import com.devpilot.today.application.ReadingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 코드 읽기 과제 조회 (docs/05 §19.7, BL-TDY-16·BL-CNT-15). 인증은 필요하지만 사용자 소유 리소스가 아니다. 코드 본문은 반환하지 않고 서버는
 * 저장소를 요청하지 않는다(RC-4).
 */
@RestController
@RequestMapping("/api/v1/readings")
@Tag(name = "today")
public class ReadingController {

    private final ReadingQueryService readingQueryService;

    public ReadingController(ReadingQueryService readingQueryService) {
        this.readingQueryService = readingQueryService;
    }

    @GetMapping("/{readingKey}")
    @Operation(operationId = "todayGetReading")
    public CuratedReadingView get(
            @PathVariable @Pattern(regexp = "^[A-Z0-9][A-Z0-9_.]{2,149}$") String readingKey) {
        return readingQueryService.get(readingKey);
    }
}
