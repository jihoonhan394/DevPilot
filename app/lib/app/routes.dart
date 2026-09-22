import 'package:flutter/foundation.dart';

/// What an entry screen already knows about a rubber duck target, passed as go_router `extra` to
/// `/rubber-duck/new` (docs/02 SCR-RUBBER-DUCK ① "대상 카드"). A reload loses it; the session's
/// `targetTitle` takes over after the first send.
@immutable
final class RubberDuckTargetPreview {
  const RubberDuckTargetPreview({this.title, this.summary});

  final String? title;
  final String? summary;
}

/// Route paths of docs/02 §2.3 available up to S3.
abstract final class AppRoutes {
  static const root = '/';
  static const login = '/login';
  static const notAllowed = '/not-allowed';

  static const onboardingGoal = '/onboarding/goal';
  static const onboardingTime = '/onboarding/time';
  static const onboardingLevel = '/onboarding/level';
  static const onboardingProject = '/onboarding/project';
  static const onboardingPlan = '/onboarding/plan';

  /// SCR-DIAGNOSTICS: step 5 "진단 시작" and the Today diagnostic card lead here.
  static const diagnostics = '/today/diagnostics';

  /// SCR-LESSON prefix: `/lessons/:lessonKey`.
  static const lessonsPrefix = '/lessons';

  /// SCR-READ-CODE prefix: `/today/read/:readingKey?taskId=`.
  static const readCodePrefix = '/today/read';

  static const training = '/training';
  static const trainingChallenges = '/training/challenges';
  static const trainingAttempts = '/training/attempts';

  static const rubberDuck = '/rubber-duck';
  static const rubberDuckNew = '/rubber-duck/new';

  static const reviewItems = '/review/items';
  static const reviewItemsNew = '/review/items/new';

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
  static const skillIdParameter = 'skillId';
  static const statusParameter = 'status';
  static const targetTypeParameter = 'targetType';
  static const targetIdParameter = 'targetId';
  static const conceptKeyParameter = 'conceptKey';
  static const skillCodeParameter = 'skillCode';

  /// Fragment of SCR-TRAINING-ATTEMPT that scrolls to the Hint Ladder (docs/02 RD-3 hand-off).
  static const hintsFragment = 'hints';

  static String planVersion(String planId) => '$planVersions/$planId';

  static String skillDetail(String skillId) => '$skills/$skillId';

  /// SCR-TRAINING-LIST, filtered by [skillId] when given.
  static String trainingFor(String? skillId) => _withQuery(training, {skillIdParameter: skillId});

  /// SCR-CHALLENGE-DETAIL; [taskId] when opened from the Today CHALLENGE task.
  static String challengeDetail(String challengeId, {String? taskId}) =>
      _withQuery('$trainingChallenges/$challengeId', {taskIdParameter: taskId});

  /// SCR-TRAINING-ATTEMPT; [focusHints] adds `#hints` (back from the rubber duck, RD-3).
  static String attempt(String attemptId, {String? taskId, bool focusHints = false}) => Uri(
    path: '$trainingAttempts/$attemptId',
    queryParameters: taskId == null ? null : {taskIdParameter: taskId},
    fragment: focusHints ? hintsFragment : null,
  ).toString();

  /// SCR-LESSON of [lessonKey].
  static String lesson(String lessonKey) => '$lessonsPrefix/$lessonKey';

  /// SCR-READ-CODE of [readingKey] for the Today READ_CODE task [taskId].
  static String readCode(String readingKey, {String? taskId}) =>
      _withQuery('$readCodePrefix/$readingKey', {taskIdParameter: taskId});

  /// SCR-RUBBER-DUCK before a session exists (docs/02 §2.3 `/rubber-duck/new` parameters).
  static String rubberDuckStart({
    required String targetType,
    String? targetId,
    String? conceptKey,
    String? skillCode,
    String? taskId,
  }) => _withQuery(rubberDuckNew, {
    targetTypeParameter: targetType,
    targetIdParameter: targetId,
    conceptKeyParameter: conceptKey,
    skillCodeParameter: skillCode,
    taskIdParameter: taskId,
  });

  /// SCR-RUBBER-DUCK of an existing session.
  static String rubberDuckSession(String sessionId, {String? taskId}) =>
      _withQuery('$rubberDuck/$sessionId', {taskIdParameter: taskId});

  /// SCR-REVIEW-ITEMS with its initial filters.
  static String reviewItemsFor({String? skillId, String? status}) =>
      _withQuery(reviewItems, {skillIdParameter: skillId, statusParameter: status});

  /// SCR-REVIEW-ITEM-EDIT of one card (the item travels as `extra`).
  static String reviewItem(String reviewItemId) => '$reviewItems/$reviewItemId';

  static String _withQuery(String path, Map<String, String?> parameters) {
    final present = {
      for (final entry in parameters.entries)
        if (entry.value != null) entry.key: entry.value!,
    };
    return Uri(path: path, queryParameters: present.isEmpty ? null : present).toString();
  }

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
    RegExp(r'^/today/read/[^/]+$'),
    RegExp(r'^/training/(challenges|attempts)/[^/]+$'),
    RegExp(r'^/rubber-duck/[^/]+$'),
    RegExp(r'^/review/items/[^/]+$'),
    RegExp(r'^/lessons/[^/]+$'),
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
    AppRoutes.diagnostics,
    AppRoutes.training,
    AppRoutes.reviewItems,
    AppRoutes.lessonsPrefix,
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
