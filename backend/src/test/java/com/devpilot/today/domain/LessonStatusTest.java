package com.devpilot.today.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.UnitTest;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 노트 진행 상태 (docs/05 §21.9). 푼 단위 수만으로 정해지고 목록 정렬 순서를 준다. */
@UnitTest
class LessonStatusTest {

    @ParameterizedTest(name = "[{index}] {0}/{1} -> {2}")
    @CsvSource({
        "0, 4, NOT_STARTED",
        "1, 4, IN_PROGRESS",
        "3, 4, IN_PROGRESS",
        "4, 4, DONE",
        // 같은 단위를 여러 번 풀어 센 값이 커져도 끝난 것은 끝난 것이다.
        "5, 4, DONE",
        // 단위가 없는 노트는 시작할 것도 없다.
        "0, 0, NOT_STARTED",
        // 음수는 들어올 수 없지만 들어와도 시작 전으로 본다.
        "-1, 4, NOT_STARTED",
    })
    void shouldDeriveStatusFromSolvedCount(int solved, int total, LessonStatus expected) {
        assertThat(LessonStatus.of(solved, total)).isEqualTo(expected);
    }

    /** 목록은 이어서 할 것이 맨 위다 (docs/05 §21.9 1번). */
    @Test
    void shouldRankUnfinishedNotesFirstAndFinishedLast() {
        List<LessonStatus> sorted =
                java.util.stream.Stream.of(
                                LessonStatus.DONE,
                                LessonStatus.NOT_STARTED,
                                LessonStatus.IN_PROGRESS)
                        .sorted(Comparator.comparingInt(LessonStatus::listRank))
                        .toList();

        assertThat(sorted)
                .containsExactly(
                        LessonStatus.IN_PROGRESS, LessonStatus.NOT_STARTED, LessonStatus.DONE);
    }

    @Test
    void shouldGiveEveryStatusItsOwnRank() {
        assertThat(LessonStatus.values())
                .extracting(LessonStatus::listRank)
                .doesNotHaveDuplicates();
    }
}
