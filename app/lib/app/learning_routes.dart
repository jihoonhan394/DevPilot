import 'package:devpilot_app/app/not_found_screen.dart';
import 'package:devpilot_app/app/route_helpers.dart';
import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/features/lesson/data/lesson_repository.dart';
import 'package:devpilot_app/features/lesson/presentation/lesson_list_screen.dart';
import 'package:devpilot_app/features/lesson/presentation/lesson_screen.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/data/review_item_models.dart';
import 'package:devpilot_app/features/review/presentation/review_item_edit_controller.dart';
import 'package:devpilot_app/features/review/presentation/review_item_edit_screen.dart';
import 'package:devpilot_app/features/review/presentation/review_item_reopen.dart';
import 'package:devpilot_app/features/review/presentation/review_items_screen.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/duck_history_screen.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_input_guard.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_screen.dart';
import 'package:devpilot_app/features/term/data/term_repository.dart';
import 'package:devpilot_app/features/term/presentation/term_detail_screen.dart';
import 'package:devpilot_app/features/term/presentation/terms_list_screen.dart';
import 'package:devpilot_app/features/tip/data/tip_repository.dart';
import 'package:devpilot_app/features/tip/presentation/tip_detail_screen.dart';
import 'package:devpilot_app/features/tip/presentation/tips_list_screen.dart';
import 'package:devpilot_app/features/today/data/reading_repository.dart';
import 'package:devpilot_app/features/today/presentation/diagnostics_screen.dart';
import 'package:devpilot_app/features/today/presentation/read_code_screen.dart';
import 'package:devpilot_app/features/training/presentation/attempt_input_guard.dart';
import 'package:devpilot_app/features/training/presentation/attempt_screen.dart';
import 'package:devpilot_app/features/training/presentation/challenge_detail_screen.dart';
import 'package:devpilot_app/features/training/presentation/training_list_screen.dart';
import 'package:go_router/go_router.dart';

// Routes of the S3 learning screens (docs/02 §2.3): the rubber duck focus screens, Training, the
// Today sub-screens SCR-DIAGNOSTICS and SCR-READ-CODE, and the review card management.

/// `/rubber-duck/history`, `/rubber-duck/new` and `/rubber-duck/:sessionId`, outside the
/// navigation frame.
List<RouteBase> rubberDuckRoutes() => [
  // `:sessionId` 보다 먼저 온다 — 뒤에 두면 리터럴 경로가 파라미터로 먹힌다.
  GoRoute(
    path: AppRoutes.rubberDuckHistory,
    builder: (context, state) => const DuckHistoryScreen(),
  ),
  GoRoute(
    path: AppRoutes.rubberDuckNew,
    onExit: (context, state) => confirmLeave(context, rubberDuckInputGuardProvider),
    builder: (context, state) => rubberDuckStartScreen(state),
  ),
  GoRoute(
    path: '${AppRoutes.rubberDuck}/:sessionId',
    onExit: (context, state) => confirmLeave(context, rubberDuckInputGuardProvider),
    builder: (context, state) => withUuid(
      state,
      'sessionId',
      (sessionId) => RubberDuckScreen(
        route: (
          sessionId: sessionId,
          targetType: RubberDuckTargetType.unknown,
          targetId: null,
          conceptKey: null,
          skillCode: null,
        ),
        taskId: uuidOrNull(state.uri.queryParameters[AppRoutes.taskIdParameter]),
      ),
    ),
  ),
];

/// `/training`, `/training/challenges/:challengeId`, `/training/attempts/:attemptId`.
GoRoute trainingRoute() => GoRoute(
  path: AppRoutes.training,
  builder: (context, state) => TrainingListScreen(
    skillId: uuidOrNull(state.uri.queryParameters[AppRoutes.skillIdParameter]),
  ),
  routes: [
    GoRoute(
      path: 'challenges/:challengeId',
      builder: (context, state) => withUuid(
        state,
        'challengeId',
        (challengeId) => ChallengeDetailScreen(
          challengeId: challengeId,
          taskId: uuidOrNull(state.uri.queryParameters[AppRoutes.taskIdParameter]),
        ),
      ),
    ),
    GoRoute(
      path: 'attempts/:attemptId',
      onExit: (context, state) => confirmLeave(context, attemptHasUnsavedInputProvider),
      builder: (context, state) => withUuid(
        state,
        'attemptId',
        (attemptId) => AttemptScreen(
          attemptId: attemptId,
          taskId: uuidOrNull(state.uri.queryParameters[AppRoutes.taskIdParameter]),
          focusHints: state.uri.fragment == AppRoutes.hintsFragment,
        ),
      ),
    ),
  ],
);

/// `/tips` (SCR-TIPS). 짧은 시간이 났을 때 하나씩 읽는 자리다.
GoRoute tipsListRoute() => GoRoute(
  path: AppRoutes.tipsPrefix,
  builder: (context, state) => TipsListScreen(
    series: _enumFrom(TipSeries.values, state.uri.queryParameters['series']),
    level: _enumFrom(TipLevel.values, state.uri.queryParameters['level']),
  ),
);

/// `/tips/:tipKey` (SCR-TIP-DETAIL). 지난 팁도 본문을 그대로 연다 (docs/05 §20.4a).
GoRoute tipDetailRoute() => GoRoute(
  path: '${AppRoutes.tipsPrefix}/:tipKey',
  builder: (context, state) {
    final tipKey = state.pathParameters['tipKey'] ?? '';
    if (!tipKeyPattern.hasMatch(tipKey)) {
      return const NotFoundScreen();
    }
    return TipDetailScreen(tipKey: tipKey);
  },
);

/// `/terms` (SCR-TERMS). 말이 헷갈릴 때 찾는 자리다. 검색어와 기술 필터는 라우트에 남는다.
GoRoute termsListRoute() => GoRoute(
  path: AppRoutes.termsPrefix,
  builder: (context, state) => TermsListScreen(
    query: state.uri.queryParameters[AppRoutes.queryParameter],
    skillId: state.uri.queryParameters[AppRoutes.skillIdParameter],
  ),
);

/// `/terms/:termKey` (SCR-TERM-DETAIL). 은퇴한 용어도 그대로 열린다 (docs/05 §20.6).
GoRoute termDetailRoute() => GoRoute(
  path: '${AppRoutes.termsPrefix}/:termKey',
  builder: (context, state) {
    final termKey = state.pathParameters['termKey'] ?? '';
    if (!termKeyPattern.hasMatch(termKey)) {
      return const NotFoundScreen();
    }
    return TermDetailScreen(termKey: termKey);
  },
);

/// 쿼리의 대문자 이름 → 열거형. 모르는 값은 필터를 걸지 않은 것으로 본다.
T? _enumFrom<T extends Enum>(List<T> values, String? wireName) {
  if (wireName == null) {
    return null;
  }
  final wanted = wireName.replaceAll('_', '').toLowerCase();
  for (final value in values) {
    if (value.name.toLowerCase() == wanted) {
      return value;
    }
  }
  return null;
}

/// `/lessons` (SCR-LESSON-LIST). 노트 전부와 진행 — 이어서 할 것이 맨 위다.
GoRoute lessonListRoute() => GoRoute(
  path: AppRoutes.lessonsPrefix,
  builder: (context, state) => const LessonListScreen(),
);

/// `/lessons/:lessonKey` (SCR-LESSON). Inside the navigation frame: it is an ordinary screen, not
/// a focus screen — the learner leaves it mid-way often ("오늘은 여기까지").
GoRoute lessonRoute() => GoRoute(
  path: '${AppRoutes.lessonsPrefix}/:lessonKey',
  builder: (context, state) {
    final lessonKey = state.pathParameters['lessonKey'] ?? '';
    if (!lessonKeyPattern.hasMatch(lessonKey)) {
      return const NotFoundScreen();
    }
    return LessonScreen(lessonKey: lessonKey);
  },
);

/// `/today/diagnostics` and `/today/read/:readingKey` (the key must match the reading pattern).
List<RouteBase> todaySubRoutes() => [
  GoRoute(
    path: 'diagnostics',
    builder: (context, state) => const DiagnosticsScreen(),
  ),
  GoRoute(
    path: 'read/:readingKey',
    builder: (context, state) {
      final readingKey = state.pathParameters['readingKey'] ?? '';
      if (!readingKeyPattern.hasMatch(readingKey)) {
        return const NotFoundScreen();
      }
      return ReadCodeScreen(
        readingKey: readingKey,
        taskId: uuidOrNull(state.uri.queryParameters[AppRoutes.taskIdParameter]),
      );
    },
  ),
];

/// `/review/items?skillId=&status=`, `/review/items/new`, `/review/items/:reviewItemId` (the card
/// travels as `extra`; without it the list opens again).
List<RouteBase> reviewSubRoutes() => [
  GoRoute(
    path: 'items',
    builder: (context, state) => ReviewItemsScreen(
      filter: (
        skillId: uuidOrNull(state.uri.queryParameters[AppRoutes.skillIdParameter]),
        status:
            ReviewItemStatus.fromWire(state.uri.queryParameters[AppRoutes.statusParameter]) ??
            ReviewItemStatus.active,
      ),
    ),
    routes: [
      GoRoute(
        path: 'new',
        onExit: (context, state) => confirmLeave(context, reviewItemEditDirtyProvider),
        builder: (context, state) => const ReviewItemEditScreen(),
      ),
      GoRoute(
        path: ':reviewItemId',
        onExit: (context, state) => confirmLeave(context, reviewItemEditDirtyProvider),
        builder: (context, state) => withUuid(state, 'reviewItemId', (reviewItemId) {
          final extra = state.extra;
          return extra is ReviewItemView && extra.id == reviewItemId
              ? ReviewItemEditScreen(item: extra)
              : const ReviewItemReopen();
        }),
      ),
    ],
  ),
];
