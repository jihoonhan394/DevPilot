/// Route paths of docs/02 §2.3 available up to S2.
abstract final class AppRoutes {
  static const root = '/';
  static const login = '/login';
  static const notAllowed = '/not-allowed';

  static const onboardingGoal = '/onboarding/goal';
  static const onboardingTime = '/onboarding/time';
  static const onboardingLevel = '/onboarding/level';
  static const onboardingProject = '/onboarding/project';
  static const onboardingPlan = '/onboarding/plan';

  /// SCR-DIAGNOSTICS ships in S3; step 5 only links here when suggestions exist (S3+).
  static const diagnostics = '/today/diagnostics';

  static const today = '/today';
  static const dashboard = '/dashboard';
  static const review = '/review';
  static const reviewSession = '/review/session';
  static const plan = '/plan';
  static const replan = '/plan/replan';
  static const learningGoal = '/plan/goal';
  static const planVersions = '/plan/versions';
  static const skills = '/skills';
  static const projects = '/projects';
  static const settings = '/settings';
  static const more = '/more';

  /// Where `/` and a finished login go (docs/02 §2.3, U-1).
  static const start = today;

  /// `from=goal` query value of [replan] (docs/02 §2.3 query parameters).
  static const replanFromGoal = 'goal';

  /// Query parameter names of docs/02 §2.3.
  static const completeParameter = 'complete';
  static const taskIdParameter = 'taskId';

  static String planVersion(String planId) => '$planVersions/$planId';

  static String skillDetail(String skillId) => '$skills/$skillId';

  static String replanFrom(String source) =>
      Uri(path: replan, queryParameters: {'from': source}).toString();

  /// SCR-REVIEW-SESSION started from the Today REVIEW task.
  static String reviewSessionFor(String taskId) =>
      Uri(path: reviewSession, queryParameters: {taskIdParameter: taskId}).toString();

  /// SCR-TODAY with the completion sheet of [taskId] open.
  static String todayComplete(String taskId) =>
      Uri(path: today, queryParameters: {completeParameter: taskId}).toString();

  static const onboardingInputSteps = [
    onboardingGoal,
    onboardingTime,
    onboardingLevel,
    onboardingProject,
  ];
}

/// Which session and onboarding state a path needs (docs/02 §2.3 인증·온보딩 columns).
enum RouteAccess {
  /// No session needed: `/login`, `/not-allowed`.
  public,

  /// Session needed, onboarding must not be finished: onboarding steps 1~4.
  onboardingInput,

  /// Session needed, onboarding state does not matter: `/settings`.
  signedIn,

  /// Session and a finished onboarding needed: every other app screen.
  onboarded,

  /// Not an app route: SCR-NOT-FOUND without a redirect.
  unknown;

  static final _detailPatterns = [
    RegExp(r'^/plan/versions/[^/]+$'),
    RegExp(r'^/skills/[^/]+$'),
  ];

  static const _onboardedPaths = {
    AppRoutes.root,
    AppRoutes.onboardingPlan,
    AppRoutes.today,
    AppRoutes.dashboard,
    AppRoutes.review,
    AppRoutes.reviewSession,
    AppRoutes.plan,
    AppRoutes.replan,
    AppRoutes.learningGoal,
    AppRoutes.planVersions,
    AppRoutes.skills,
    AppRoutes.projects,
    AppRoutes.more,
  };

  static RouteAccess of(String path) {
    if (path == AppRoutes.login || path == AppRoutes.notAllowed) {
      return public;
    }
    if (AppRoutes.onboardingInputSteps.contains(path)) {
      return onboardingInput;
    }
    if (path == AppRoutes.settings) {
      return signedIn;
    }
    if (_onboardedPaths.contains(path) ||
        _detailPatterns.any((pattern) => pattern.hasMatch(path))) {
      return onboarded;
    }
    return unknown;
  }

  bool get requiresSession => this == onboardingInput || this == signedIn || this == onboarded;
}
