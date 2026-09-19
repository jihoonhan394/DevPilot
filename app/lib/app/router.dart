import 'dart:async';

import 'package:devpilot_app/app/app_shell.dart';
import 'package:devpilot_app/app/install_card.dart';
import 'package:devpilot_app/app/more_screen.dart';
import 'package:devpilot_app/app/not_found_screen.dart';
import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/app/session_redirect.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/auth/auth_controller.dart';
import 'package:devpilot_app/core/widgets/confirm_dialog.dart';
import 'package:devpilot_app/features/auth/presentation/login_screen.dart';
import 'package:devpilot_app/features/auth/presentation/not_allowed_screen.dart';
import 'package:devpilot_app/features/dashboard/presentation/dashboard_screen.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_goal_screen.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_level_screen.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_plan_screen.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_project_screen.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_time_screen.dart';
import 'package:devpilot_app/features/plan/presentation/learning_goal_controller.dart';
import 'package:devpilot_app/features/plan/presentation/learning_goal_screen.dart';
import 'package:devpilot_app/features/plan/presentation/plan_history_screen.dart';
import 'package:devpilot_app/features/plan/presentation/plan_screen.dart';
import 'package:devpilot_app/features/plan/presentation/plan_version_screen.dart';
import 'package:devpilot_app/features/plan/presentation/replan_controller.dart';
import 'package:devpilot_app/features/plan/presentation/replan_screen.dart';
import 'package:devpilot_app/features/project/presentation/projects_screen.dart';
import 'package:devpilot_app/features/review/presentation/review_home_screen.dart';
import 'package:devpilot_app/features/review/presentation/review_session_screen.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_input_guard.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_screen.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/features/settings/presentation/settings_controller.dart';
import 'package:devpilot_app/features/settings/presentation/settings_screen.dart';
import 'package:devpilot_app/features/skill/presentation/skill_detail_screen.dart';
import 'package:devpilot_app/features/skill/presentation/skill_tree_screen.dart';
import 'package:devpilot_app/features/today/presentation/diagnostics_screen.dart';
import 'package:devpilot_app/features/today/presentation/today_screen.dart';
import 'package:devpilot_app/features/training/presentation/attempt_input_guard.dart';
import 'package:devpilot_app/features/training/presentation/attempt_screen.dart';
import 'package:devpilot_app/features/training/presentation/challenge_detail_screen.dart';
import 'package:devpilot_app/features/training/presentation/training_list_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart';
import 'package:go_router/go_router.dart';

/// Path parameters are UUIDs; anything else shows SCR-NOT-FOUND (docs/02 §2.3).
final _uuidPattern = RegExp(
  r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
);

/// Notifies go_router when the session or the profile status changes.
final class _RouterRefresh extends ChangeNotifier {
  void notify() => notifyListeners();
}

final routerProvider = Provider<GoRouter>((ref) {
  final refresh = _RouterRefresh();
  ref.listen<AuthState>(authStateProvider, (_, _) => refresh.notify());
  ref.listen(meProvider, (previous, next) {
    // Rule 5: an account whose deletion was requested is signed out (docs/02 §2.4).
    if (next.value?.status == UserStatus.deletionRequested &&
        ref.read(authStateProvider) is SignedIn) {
      ref.read(authStateProvider.notifier).signOut(reason: SignOutReason.deleted);
    }
    refresh.notify();
  });

  final router = GoRouter(
    initialLocation: AppRoutes.root,
    refreshListenable: refresh,
    redirect: (context, state) => redirectFor(
      auth: ref.read(authStateProvider),
      profile: ProfileStatus.of(ref.read(meProvider)),
      location: state.uri,
    ),
    routes: [
      GoRoute(path: AppRoutes.root, redirect: (context, state) => AppRoutes.start),
      GoRoute(
        path: AppRoutes.login,
        builder: (context, state) => LoginScreen(reason: state.uri.queryParameters['reason']),
      ),
      GoRoute(path: AppRoutes.notAllowed, builder: (context, state) => const NotAllowedScreen()),
      GoRoute(
        path: AppRoutes.onboardingGoal,
        builder: (context, state) => const OnboardingGoalScreen(),
      ),
      GoRoute(
        path: AppRoutes.onboardingTime,
        builder: (context, state) => const OnboardingTimeScreen(),
      ),
      GoRoute(
        path: AppRoutes.onboardingLevel,
        builder: (context, state) => const OnboardingLevelScreen(),
      ),
      GoRoute(
        path: AppRoutes.onboardingProject,
        builder: (context, state) => const OnboardingProjectScreen(),
      ),
      GoRoute(
        path: AppRoutes.onboardingPlan,
        builder: (context, state) => const OnboardingPlanScreen(),
      ),
      // A focus screen without the navigation frame (docs/02 §2.2).
      GoRoute(
        path: AppRoutes.reviewSession,
        onExit: (context, state) => confirmReviewSessionExit(
          context,
          state.uri.queryParameters[AppRoutes.taskIdParameter],
        ),
        builder: (context, state) =>
            ReviewSessionScreen(taskId: state.uri.queryParameters[AppRoutes.taskIdParameter]),
      ),
      // SCR-RUBBER-DUCK is a focus screen too (docs/02 §2.2).
      GoRoute(
        path: AppRoutes.rubberDuckNew,
        onExit: (context, state) => _confirmLeave(context, rubberDuckInputGuardProvider),
        builder: (context, state) => _rubberDuckStart(state),
      ),
      GoRoute(
        path: '${AppRoutes.rubberDuck}/:sessionId',
        onExit: (context, state) => _confirmLeave(context, rubberDuckInputGuardProvider),
        builder: (context, state) => _withUuid(
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
            taskId: _uuidOrNull(state.uri.queryParameters[AppRoutes.taskIdParameter]),
          ),
        ),
      ),
      ShellRoute(
        builder: (context, state, child) => AppShell(location: state.uri.path, child: child),
        routes: [
          GoRoute(
            path: AppRoutes.today,
            builder: (context, state) => TodayScreen(
              completeTaskId: state.uri.queryParameters[AppRoutes.completeParameter],
              footer: const InstallCard(),
            ),
            routes: [
              GoRoute(
                path: 'diagnostics',
                builder: (context, state) => const DiagnosticsScreen(),
              ),
            ],
          ),
          GoRoute(path: AppRoutes.dashboard, builder: (context, state) => const DashboardScreen()),
          GoRoute(path: AppRoutes.review, builder: (context, state) => const ReviewHomeScreen()),
          GoRoute(
            path: AppRoutes.plan,
            builder: (context, state) => const PlanScreen(),
            routes: [
              GoRoute(
                path: 'replan',
                onExit: (context, state) => _confirmLeave(context, replanHasUnsavedChangesProvider),
                builder: (context, state) => ReplanScreen(
                  fromGoal: state.uri.queryParameters['from'] == AppRoutes.replanFromGoal,
                ),
              ),
              GoRoute(
                path: 'goal',
                onExit: (context, state) =>
                    _confirmLeave(context, learningGoalHasUnsavedChangesProvider),
                builder: (context, state) => const LearningGoalScreen(),
              ),
              GoRoute(
                path: 'versions',
                builder: (context, state) => const PlanHistoryScreen(),
                routes: [
                  GoRoute(
                    path: ':planId',
                    builder: (context, state) =>
                        _withUuid(state, 'planId', (planId) => PlanVersionScreen(planId: planId)),
                  ),
                ],
              ),
            ],
          ),
          GoRoute(
            path: AppRoutes.skills,
            builder: (context, state) => const SkillTreeScreen(),
            routes: [
              GoRoute(
                path: ':skillId',
                builder: (context, state) =>
                    _withUuid(state, 'skillId', (skillId) => SkillDetailScreen(skillId: skillId)),
              ),
            ],
          ),
          GoRoute(
            path: AppRoutes.training,
            builder: (context, state) => TrainingListScreen(
              skillId: _uuidOrNull(state.uri.queryParameters[AppRoutes.skillIdParameter]),
            ),
            routes: [
              GoRoute(
                path: 'challenges/:challengeId',
                builder: (context, state) => _withUuid(
                  state,
                  'challengeId',
                  (challengeId) => ChallengeDetailScreen(
                    challengeId: challengeId,
                    taskId: _uuidOrNull(state.uri.queryParameters[AppRoutes.taskIdParameter]),
                  ),
                ),
              ),
              GoRoute(
                path: 'attempts/:attemptId',
                onExit: (context, state) => _confirmLeave(context, attemptHasUnsavedInputProvider),
                builder: (context, state) => _withUuid(
                  state,
                  'attemptId',
                  (attemptId) => AttemptScreen(
                    attemptId: attemptId,
                    taskId: _uuidOrNull(state.uri.queryParameters[AppRoutes.taskIdParameter]),
                    focusHints: state.uri.fragment == AppRoutes.hintsFragment,
                  ),
                ),
              ),
            ],
          ),
          GoRoute(path: AppRoutes.projects, builder: (context, state) => const ProjectsScreen()),
          GoRoute(
            path: AppRoutes.settings,
            onExit: (context, state) => _confirmLeave(context, settingsHasUnsavedChangesProvider),
            builder: (context, state) => const SettingsScreen(),
          ),
          GoRoute(path: AppRoutes.more, builder: (context, state) => const MoreScreen()),
        ],
      ),
    ],
    errorBuilder: (context, state) => const NotFoundScreen(),
  );
  ref.onDispose(() {
    router.dispose();
    refresh.dispose();
  });
  return router;
});

/// `/rubber-duck/new?targetType=&targetId=|conceptKey=&skillCode=&taskId=` (docs/02 §2.3). A
/// missing or unknown target type, or a target that does not fit it, is SCR-NOT-FOUND.
Widget _rubberDuckStart(GoRouterState state) {
  final query = state.uri.queryParameters;
  final type = RubberDuckTargetType.fromWire(query[AppRoutes.targetTypeParameter]);
  final targetId = _uuidOrNull(query[AppRoutes.targetIdParameter]);
  final conceptKey = query[AppRoutes.conceptKeyParameter];
  final concept = type == RubberDuckTargetType.concept;
  if (type == null || (concept ? conceptKey == null : targetId == null)) {
    return const NotFoundScreen();
  }
  final extra = state.extra;
  return RubberDuckScreen(
    route: (
      sessionId: null,
      targetType: type,
      targetId: concept ? null : targetId,
      conceptKey: concept ? conceptKey : null,
      skillCode: query[AppRoutes.skillCodeParameter],
    ),
    taskId: _uuidOrNull(query[AppRoutes.taskIdParameter]),
    preview: extra is RubberDuckTargetPreview ? extra : null,
  );
}

/// A query value that must be a UUID (`taskId`, `skillId`); anything else is ignored.
String? _uuidOrNull(String? value) => value != null && _uuidPattern.hasMatch(value) ? value : null;

Widget _withUuid(GoRouterState state, String name, Widget Function(String id) build) {
  final value = state.pathParameters[name];
  return value != null && _uuidPattern.hasMatch(value) ? build(value) : const NotFoundScreen();
}

/// Leaves at once when nothing is dirty, otherwise asks first (docs/02 §6.8). A sign-out or an
/// expired session always leaves: the screen cannot save without a session.
FutureOr<bool> _confirmLeave(BuildContext context, ProviderListenable<bool> hasUnsavedChanges) {
  final container = ProviderScope.containerOf(context, listen: false);
  if (container.read(authStateProvider) is SignedOut || !container.read(hasUnsavedChanges)) {
    return true;
  }
  return confirmDiscardChanges(context);
}
