package com.devpilot.today.domain;

import com.devpilot.common.domain.TipLevel;
import com.devpilot.common.domain.TipSeries;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 오늘의 팁 한 건 (docs/19 §3.9). 테이블이 없다 — {@code content/tips/*.yaml}을 기동 시 읽어 메모리에 둔다(ADR-041).
 *
 * <p>실무에서 자주 보는 <b>증상 하나</b>와 그 원인을 짧게 읽는 것이다. 그래서 {@code symptom}이 둘이면 팁을 둘로 나눈다.
 *
 * @param example 증상을 보여 주는 최소한의 코드. 없을 수 있다
 * @param experiment 5분 안에 재현하는 방법. {@code sourceUrl}과 <b>둘 중 하나는 반드시</b> 있다(CV-91)
 * @param sourceUrl 공식 문서. 서버는 열어 보지 않고 호스트만 검사한다(CV-92)
 * @param retired 은퇴한 팁. 제안되지 않지만 이미 받은 사람은 그대로 조회된다(docs/19 §8.2)
 */
public record DailyTip(
        String key,
        TipSeries series,
        TipLevel level,
        List<String> skillCodes,
        String title,
        String symptom,
        String cause,
        @Nullable String example,
        String whereToLook,
        @Nullable String experiment,
        @Nullable String sourceUrl,
        int estimatedMinutes,
        boolean retired) {

    public DailyTip {
        skillCodes = List.copyOf(skillCodes);
    }
}
