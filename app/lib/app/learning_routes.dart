import 'package:devpilot_app/app/not_found_screen.dart';
import 'package:devpilot_app/app/route_helpers.dart';
import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/data/review_item_models.dart';
import 'package:devpilot_app/features/review/presentation/review_item_edit_controller.dart';
import 'package:devpilot_app/features/review/presentation/review_item_edit_screen.dart';
import 'package:devpilot_app/features/review/presentation/review_item_reopen.dart';
import 'package:devpilot_app/features/review/presentation/review_items_screen.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_input_guard.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_screen.dart';
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

/// `/rubber-duck/new` and `/rubber-duck/:sessionId`, outside the navigation frame.
List<RouteBase> rubberDuckRoutes() => [
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
