import 'package:devpilot_app/app/install_card.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/storage/key_value_store.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/time/session_time_rules.dart';
import 'package:devpilot_app/features/plan/data/plan_models.dart';
import 'package:devpilot_app/features/plan/domain/budget_display.dart';
import 'package:devpilot_app/features/plan/domain/replan_suggestion_selection.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/domain/review_card_progress.dart';
import 'package:devpilot_app/features/today/domain/today_rules.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/learning_fixtures.dart';

/// Pure client rules of S2 screens (docs/02 SCR-TODAY, SCR-REVIEW-*, SCR-PLAN, SCR-REPLAN, §8.2).
void main() {
  group('TodayRules.defaultMinutes', () {
    const saturday = LocalDate(2026, 9, 19);
    const monday = LocalDate(2026, 9, 21);

    test('shouldUseWeekendTimeOnWeekendAndWeekdayTimeOtherwise', () {
      expect(
        TodayRules.defaultMinutes(today: saturday, weekdayMinutes: 45, weekendMinutes: 240),
        240,
      );
      expect(TodayRules.defaultMinutes(today: monday, weekdayMinutes: 45, weekendMinutes: 240), 45);
    });

    test('shouldFallBackTo30ForZeroAndStayWithinGenerateLimits', () {
      expect(TodayRules.defaultMinutes(today: monday, weekdayMinutes: 0, weekendMinutes: 0), 30);
      expect(TodayRules.defaultMinutes(today: monday, weekdayMinutes: 3, weekendMinutes: 0), 5);
      expect(TodayRules.isValidMinutes(4), isFalse);
      expect(TodayRules.isValidMinutes(720), isTrue);
      expect(TodayRules.isValidMinutes(721), isFalse);
    });
  });

  group('SessionTimeRules (docs/05 §9.2: ⌈elapsed × 1.5⌉, at most 720)', () {
    final start = DateTime.utc(2026, 9, 19, 3);

    test('shouldDefaultToRoundedElapsedMinutesUnderTheLimit', () {
      final later = start.add(const Duration(minutes: 35, seconds: 29));
      expect(SessionTimeRules.defaultActualMinutes(start, later), 35);
      expect(SessionTimeRules.maxActualMinutes(start, later), 54);
      expect(SessionTimeRules.elapsedMinutes(start, later), 35);
    });

    test('shouldKeepDefaultAtLeastOneButNeverAboveTheLimit', () {
      expect(
        SessionTimeRules.defaultActualMinutes(start, start.add(const Duration(seconds: 20))),
        1,
      );
      expect(SessionTimeRules.maxActualMinutes(start, start.add(const Duration(seconds: 20))), 1);
      expect(SessionTimeRules.defaultActualMinutes(start, start), 0);
      expect(SessionTimeRules.maxActualMinutes(start, start.add(const Duration(hours: 10))), 720);
      // A device clock behind the server start does not go negative.
      expect(SessionTimeRules.elapsedMinutes(start, start.subtract(const Duration(minutes: 1))), 0);
    });
  });

  group('Review card', () {
    test('shouldEstimateHalfAgainTheCardCountRoundedUp', () {
      expect(estimatedReviewMinutes(6), 9);
      expect(estimatedReviewMinutes(3), 5);
      expect(estimatedReviewMinutes(1), 2);
      expect(estimatedReviewMinutes(0), 0);
    });

    test('shouldMoveHintLevelOnlyBeforeRevealing', () {
      final card = ReviewCardProgress(
        item: testDueItem(id: 'r1'),
        shownAt: testNow,
      );
      expect(card.hintLevel, HintLevel.selfExplain);
      expect(card.hintText, isNull);

      final hinted = card.withHint();
      expect(hinted.hintLevel, HintLevel.conceptHint);
      expect(hinted.hintText, '프록시 기반 AOP 언급');
      expect(hinted.canShowHint, isFalse);

      final revealed = hinted.withRevealed();
      expect(revealed.withAnswerShownFirst().hintLevel, HintLevel.conceptHint);
      expect(card.withAnswerShownFirst().hintLevel, HintLevel.fullExample);
      expect(card.withAnswerShownFirst().revealed, isTrue);
    });

    test('shouldHideHintWithoutRubricAndBuildRequest', () {
      final card = ReviewCardProgress(
        item: testDueItem(id: 'r1', rubric: const []),
        shownAt: testNow,
      );
      expect(card.canShowHint, isFalse);
      expect(card.withHint().hintLevel, HintLevel.selfExplain);

      final request = card
          .withAnswer('  ')
          .request(ReviewRating.good, testNow.add(const Duration(seconds: 74)));
      expect(request.toJson(), {
        'answerText': null,
        'selfRating': 'GOOD',
        'hintLevel': 'SELF_EXPLAIN',
        'responseSeconds': 74,
        'wasVariant': false,
        'evaluate': false,
      });
      final late = card.request(ReviewRating.hard, testNow.add(const Duration(days: 2)));
      expect(late.responseSeconds, 86400);
    });
  });

  group('ReplanSuggestionSelection', () {
    const raise = TargetChangeInput(
      skillCode: 'SPRING.TRANSACTION',
      axis: SkillAxis.explanation,
      newTarget: 4,
    );
    const reduce = TargetChangeInput(
      skillCode: 'DATABASE.EXECUTION_PLAN',
      axis: SkillAxis.implementation,
      newTarget: 3,
    );

    test('shouldToggleAndApplyCheckedSuggestionsInCheckOrder', () {
      final selection = ReplanSuggestionSelection.empty
          .toggleDeferral('B')
          .toggleDeferral('A')
          .toggleReduction(reduce)
          .toggleRestoration('C')
          .toggleRaise(raise)
          .toggleDeferral('B');
      const request = ReplanRequest(reason: '', version: 3, milestones: []);

      final applied = selection.applyTo(request).toJson();

      expect(applied['acceptedDeferrals'], ['A']);
      expect(applied['acceptedTargetReductions'], [
        {'skillCode': 'DATABASE.EXECUTION_PLAN', 'axis': 'IMPLEMENTATION', 'newTarget': 3},
      ]);
      expect(applied['restoredDeferrals'], ['C']);
      expect(applied['acceptedTargetRaises'], [
        {'skillCode': 'SPRING.TRANSACTION', 'axis': 'EXPLANATION', 'newTarget': 4},
      ]);
      expect(selection.isEmpty, isFalse);
      expect(ReplanSuggestionSelection.empty.isEmpty, isTrue);
    });

    test('shouldFindTheRowOfAServerFieldError', () {
      final selection = ReplanSuggestionSelection.empty.toggleRaise(raise).toggleDeferral('A');

      expect(
        selection.identityOfField('acceptedTargetRaises[0].newTarget'),
        SuggestionIdentity.raise(raise),
      );
      expect(selection.identityOfField('acceptedDeferrals[0]'), SuggestionIdentity.defer('A'));
      expect(selection.identityOfField('acceptedDeferrals[4]'), isNull);
      expect(selection.identityOfField('milestones[0].title'), isNull);
    });
  });

  test('BudgetDisplay rounds hours half up and floors percent', () {
    expect(BudgetDisplay.hours(4914), 82);
    expect(BudgetDisplay.hours(89), 1);
    expect(BudgetDisplay.hours(29), 0);
    expect(BudgetDisplay.percent(11950), 119);
    expect(BudgetDisplay.percent(6800), 68);
  });

  group('InstallCardPolicy (docs/02 §8.2)', () {
    const first = LocalDate(2026, 9, 19);

    test('shouldShowFromSecondPlanDayOnly', () {
      final policy = InstallCardPolicy(MemoryKeyValueStore());
      expect(policy.shouldShow(first), isFalse);
      expect(policy.shouldShow(first), isFalse);
      expect(policy.shouldShow(first.addDays(1)), isTrue);
    });

    test('shouldStayHiddenFor30DaysAfterDismiss', () {
      final store = MemoryKeyValueStore({InstallCardPolicy.firstVisitKey: '2026-09-01'});
      final policy = InstallCardPolicy(store)..dismiss(first);
      expect(policy.shouldShow(first.addDays(29)), isFalse);
      expect(policy.shouldShow(first.addDays(30)), isTrue);
    });
  });
}
