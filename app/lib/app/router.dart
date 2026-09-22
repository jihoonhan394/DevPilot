import 'package:devpilot_app/app/app_shell.dart';
import 'package:devpilot_app/app/install_card.dart';
import 'package:devpilot_app/app/learning_routes.dart';
import 'package:devpilot_app/app/more_screen.dart';
import 'package:devpilot_app/app/not_found_screen.dart';
import 'package:devpilot_app/app/route_helpers.dart';
import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/app/session_redirect.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/auth/auth_controller.dart';
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
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/features/settings/presentation/settings_controller.dart';
import 'package:devpilot_app/features/settings/presentation/settings_screen.dart';
import 'package:devpilot_app/features/skill/presentation/skill_detail_screen.dart';
import 'package:devpilot_app/features/skill/presentation/skill_tree_screen.dart';
import 'package:devpilot_app/features/today/presentation/today_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

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
      ...rubberDuckRoutes(),
      ShellRoute(
        builder: (context, state, child) => AppShell(location: state.uri.path, child: child),
        routes: [
          GoRoute(
            path: AppRoutes.today,
            builder: (context, state) => TodayScreen(
              completeTaskId: state.uri.queryParameters[AppRoutes.completeParameter],
              footer: const InstallCard(),
            ),
            routes: todaySubRoutes(),
          ),
          lessonListRoute(),
          lessonRoute(),
          GoRoute(path: AppRoutes.dashboard, builder: (context, state) => const DashboardScreen()),
          GoRoute(
            path: AppRoutes.review,
            builder: (context, state) => const ReviewHomeScreen(),
            routes: reviewSubRoutes(),
          ),
          GoRoute(
            path: AppRoutes.plan,
            builder: (context, state) => const PlanScreen(),
            routes: [
              GoRoute(
                path: 'replan',
                onExit: (context, state) => confirmLeave(context, replanHasUnsavedChangesProvider),
                builder: (context, state) => ReplanScreen(
                  fromGoal: state.uri.queryParameters['from'] == AppRoutes.replanFromGoal,
                ),
              ),
              GoRoute(
                path: 'goal',
                onExit: (context, state) =>
                    confirmLeave(context, learningGoalHasUnsavedChangesProvider),
                builder: (context, state) => const LearningGoalScreen(),
              ),
              GoRoute(
                path: 'versions',
                builder: (context, state) => const PlanHistoryScreen(),
                routes: [
                  GoRoute(
                    path: ':planId',
                    builder: (context, state) =>
                        withUuid(state, 'planId', (planId) => PlanVersionScreen(planId: planId)),
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
                    withUuid(state, 'skillId', (skillId) => SkillDetailScreen(skillId: skillId)),
              ),
            ],
          ),
          trainingRoute(),
          GoRoute(path: AppRoutes.projects, builder: (context, state) => const ProjectsScreen()),
          GoRoute(
            path: AppRoutes.settings,
            onExit: (context, state) => confirmLeave(context, settingsHasUnsavedChangesProvider),
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
