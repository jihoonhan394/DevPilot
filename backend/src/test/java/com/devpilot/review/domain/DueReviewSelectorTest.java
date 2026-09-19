package com.devpilot.review.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.review.domain.DueReviewSelector.Candidate;
import com.devpilot.review.domain.DueReviewSelector.Selection;
import com.devpilot.skill.domain.Priority;
import com.devpilot.testsupport.UnitTest;
import com.devpilot.testsupport.VectorLoader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * docs/06 §6.5 due 선택 ({@code 06-06-due-selection.yaml}, 4행)과 §6.6 RV-INTERLEAVE vector ({@code
 * 06-06-interleave.yaml}, 3행), AC-05 S5, AC-17 S4, AC-29 S1·S2, docs/09 §5.3.
 */
@UnitTest
class DueReviewSelectorTest {

    private static final String SELECTION_FILE = "06-06-due-selection.yaml";
    private static final String INTERLEAVE_FILE = "06-06-interleave.yaml";

    private final DueReviewSelector selector = new DueReviewSelector();

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("selectionVectors")
    void shouldMatchVectorWhenDueReviewsAreSelected(
            String id,
            List<Candidate> cards,
            int cap,
            int expectedTotal,
            List<UUID> expectedOrder) {
        Instant boundary =
                Instant.parse(
                        (String) VectorLoader.loadYaml(SELECTION_FILE).get("nextPlanDayStart"));

        Selection selection = selector.select(cards, boundary, cap);

        assertThat(selection.totalDue()).as(id).isEqualTo(expectedTotal);
        assertThat(selection.ordered().stream().map(Candidate::id).toList())
                .as(id)
                .isEqualTo(expectedOrder);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("interleaveVectors")
    void shouldMatchVectorWhenCardsAreInterleaved(
            String id, List<String[]> input, List<String> expected) {
        List<String[]> before = List.copyOf(input);

        List<String[]> result = DueReviewSelector.interleave(input, card -> card[1]);

        assertThat(result.stream().map(card -> card[0]).toList()).as(id).isEqualTo(expected);
        assertThat(input).as(id + " input is not modified").isEqualTo(before);
        assertThat(result).as(id + " same card set").containsExactlyInAnyOrderElementsOf(input);
    }

    @Test
    void shouldReturnSameOrderWhenRunRepeatedly() {
        List<String[]> input = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            input.add(new String[] {"R" + i, i <= 5 ? "A" : "B"});
        }
        List<String> first =
                DueReviewSelector.interleave(input, card -> card[1]).stream()
                        .map(card -> card[0])
                        .toList();

        for (int run = 0; run < 100; run++) {
            assertThat(
                            DueReviewSelector.interleave(input, card -> card[1]).stream()
                                    .map(card -> card[0])
                                    .toList())
                    .isEqualTo(first);
        }
    }

    @Test
    void shouldApplyCapBeforeInterleaving() {
        List<String[]> input = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            input.add(new String[] {"R" + i, i <= 5 ? "A" : "B"});
        }

        List<String[]> capped = DueReviewSelector.interleave(input.subList(0, 5), card -> card[1]);

        assertThat(capped.stream().map(card -> card[0]).toList())
                .containsExactly("R1", "R2", "R3", "R4", "R5");
    }

    @Test
    void shouldComputeOverdueDaysFromPlanDate() {
        LocalDate today = LocalDate.parse("2026-10-12");

        assertThat(DueReviewSelector.overdueDays(LocalDate.parse("2026-10-10"), today))
                .isEqualTo(2);
        assertThat(DueReviewSelector.overdueDays(LocalDate.parse("2026-10-13"), today)).isZero();
    }

    static Stream<Arguments> selectionVectors() {
        LocalDate today =
                LocalDate.parse((String) VectorLoader.loadYaml(SELECTION_FILE).get("today"));
        return VectorLoader.yamlRows(SELECTION_FILE).stream()
                .map(
                        row ->
                                Arguments.of(
                                        row.get("id"),
                                        cards(row, today),
                                        row.get("cap"),
                                        row.get("expectedTotal"),
                                        ((List<?>) row.get("expected"))
                                                .stream()
                                                        .map(value -> id((Integer) value))
                                                        .toList()));
    }

    static Stream<Arguments> interleaveVectors() {
        return VectorLoader.yamlRows(INTERLEAVE_FILE).stream()
                .map(
                        row ->
                                Arguments.of(
                                        row.get("id"),
                                        ((List<?>) row.get("input"))
                                                .stream()
                                                        .map(value -> ((String) value).split(":"))
                                                        .toList(),
                                        row.get("expected")));
    }

    private static List<Candidate> cards(Map<String, Object> row, LocalDate today) {
        List<Candidate> cards = new ArrayList<>();
        for (Object value : (List<?>) row.get("cards")) {
            Map<?, ?> card = (Map<?, ?>) value;
            String priority = (String) card.get("priority");
            cards.add(
                    new Candidate(
                            id((Integer) card.get("id")),
                            UUID.nameUUIDFromBytes(
                                    ((String) card.get("skill")).getBytes(StandardCharsets.UTF_8)),
                            Instant.parse((String) card.get("dueAt")),
                            DueReviewSelector.overdueDays(
                                    LocalDate.parse((String) card.get("dueDate")), today),
                            priority == null ? null : Priority.valueOf(priority),
                            (Integer) card.get("failures")));
        }
        return cards;
    }

    private static UUID id(int value) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", value));
    }
}
