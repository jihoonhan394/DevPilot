import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/plan/data/learning_goal_models.dart';
import 'package:devpilot_app/features/plan/data/learning_goal_repository.dart';
import 'package:devpilot_app/features/plan/data/plan_models.dart';
import 'package:devpilot_app/features/plan/data/plan_repository.dart';
import 'package:devpilot_app/features/plan/domain/milestone_ordering.dart';
import 'package:devpilot_app/features/plan/presentation/budget_risk_card.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// What SCR-PLAN shows: the active plan (null = `PLAN_NOT_FOUND` empty state) and the goal dates
/// of the header (null when `GET /learning-goal` failed; only those lines are hidden).
final class PlanScreenData {
  const PlanScreenData({required this.plan, required this.goal, this.busyMilestoneIds = const {}});

  final PlanView? plan;
  final LearningGoalView? goal;

  /// Milestones with a PATCH in flight; their controls are disabled.
  final Set<String> busyMilestoneIds;

  List<MilestoneView> get milestones =>
      MilestoneOrdering.sorted(plan?.milestones ?? const <MilestoneView>[]);

  PlanScreenData copyWith({PlanView? plan, Set<String>? busyMilestoneIds}) => PlanScreenData(
    plan: plan ?? this.plan,
    goal: goal,
    busyMilestoneIds: busyMilestoneIds ?? this.busyMilestoneIds,
  );
}

/// How a milestone action ended; the screen turns it into a toast.
sealed class PlanActionOutcome {
  const PlanActionOutcome();
}

final class PlanActionSaved extends PlanActionOutcome {
  const PlanActionSaved();
}

/// `CONCURRENT_MODIFICATION` or `PLAN_NOT_ACTIVE`: the plan was re-read (toast `plan.reloaded`).
final class PlanActionReloaded extends PlanActionOutcome {
  const PlanActionReloaded();
}

final class PlanActionFailed extends PlanActionOutcome {
  const PlanActionFailed(this.error);

  final Object error;
}

/// SCR-PLAN: `GET /plans/active` + milestone in-place PATCH (docs/02 SCR-PLAN, docs/05 §7.6).
final class PlanController extends AsyncNotifier<PlanScreenData> {
  final _createKeys = IdempotencyKeyCache();

  static const _reloadCodes = {
    ApiErrorCode.concurrentModification,
    ApiErrorCode.planNotActive,
  };

  @override
  Future<PlanScreenData> build() async {
    final planFuture = _fetchActivePlan();
    final goalFuture = _fetchGoal();
    return PlanScreenData(plan: await planFuture, goal: await goalFuture);
  }

  Future<PlanView?> _fetchActivePlan() async {
    try {
      return await ref.read(planRepositoryProvider).fetchActivePlan();
    } on ApiException catch (error) {
      if (error.code == ApiErrorCode.planNotFound) {
        return null;
      }
      rethrow;
    }
  }

  Future<LearningGoalView?> _fetchGoal() async {
    try {
      return await ref.read(learningGoalRepositoryProvider).fetchGoal();
    } on ApiException {
      // The goal lines of the header are optional; the plan itself still shows.
      return null;
    }
  }

  /// Re-reads everything, the budget card included, while the current data stays on screen.
  void reload() {
    ref.invalidate(activeBudgetProvider);
    ref.invalidateSelf();
  }

  /// Shows the plan version saved by SCR-REPLAN without another request.
  ///
  /// Every milestone got a new id (docs/05 §7.8); ids still held here are translated with
  /// `milestoneIdMapping`, and a PATCH still in flight for a removed milestone is dropped.
  void applyReplan(ReplanCommitResponse response) {
    final data = state.value;
    if (data == null) {
      reload();
      return;
    }
    final mapper = MilestoneIdMapper(response.milestoneIdMapping);
    // The new version has its own budget and risk.
    ref.invalidate(activeBudgetProvider);
    state = AsyncData(
      PlanScreenData(
        plan: response.plan,
        goal: data.goal,
        busyMilestoneIds: {
          for (final id in data.busyMilestoneIds) ?mapper.newIdOf(id),
        },
      ),
    );
  }

  /// Status dropdown: saved at once, shown before the response and reverted on failure.
  Future<PlanActionOutcome> changeStatus(String milestoneId, MilestoneStatus status) {
    assert(status != MilestoneStatus.unknown, 'unknown is never sent');
    return _patch(
      milestoneId,
      optimistic: (milestone) => milestone.copyWith(status: status),
      request: (milestone) => MilestonePatchRequest(status: status, version: milestone.version),
    );
  }

  /// Memo editor "저장". An empty text clears the memo.
  Future<PlanActionOutcome> saveDescription(String milestoneId, String description) => _patch(
    milestoneId,
    request: (milestone) =>
        MilestonePatchRequest(description: description, version: milestone.version),
  );

  /// ↑/↓: two sequential `sortOrder` PATCHes; a failed second one re-reads the plan.
  Future<PlanActionOutcome> move(String milestoneId, MoveDirection direction) async {
    final data = state.value;
    final swap = data == null
        ? null
        : MilestoneOrdering.swap(data.milestones, milestoneId, direction);
    if (swap == null) {
      return const PlanActionSaved();
    }
    final first = await _patch(
      swap.moved.milestone.id,
      request: (milestone) =>
          MilestonePatchRequest(sortOrder: swap.moved.sortOrder, version: milestone.version),
    );
    if (first is! PlanActionSaved) {
      return first;
    }
    final second = await _patch(
      swap.neighbor.milestone.id,
      request: (milestone) =>
          MilestonePatchRequest(sortOrder: swap.neighbor.sortOrder, version: milestone.version),
    );
    if (second is PlanActionFailed) {
      reload();
    }
    return second;
  }

  /// Empty state "계획 만들기": `POST /plans` then shows the new plan.
  Future<PlanActionOutcome> createPlan() async {
    final idempotencyKey = _createKeys.keyFor(const {});
    try {
      final plan = await ref
          .read(planRepositoryProvider)
          .createPlan(idempotencyKey: idempotencyKey);
      _createKeys.settle(null);
      final data = state.value;
      if (data != null && ref.mounted) {
        state = AsyncData(PlanScreenData(plan: plan, goal: data.goal));
      }
      return const PlanActionSaved();
    } on ApiException catch (error) {
      _createKeys.settle(error);
      if (error.code == ApiErrorCode.activePlanExists) {
        reload();
        return const PlanActionReloaded();
      }
      return PlanActionFailed(error);
    }
  }

  Future<PlanActionOutcome> _patch(
    String milestoneId, {
    required MilestonePatchRequest Function(MilestoneView milestone) request,
    MilestoneView Function(MilestoneView milestone)? optimistic,
  }) async {
    final data = state.value;
    final plan = data?.plan;
    final original = plan?.milestones.where((milestone) => milestone.id == milestoneId).firstOrNull;
    if (data == null || plan == null || original == null) {
      return const PlanActionSaved();
    }
    _replaceMilestone(optimistic?.call(original) ?? original, busy: true);
    try {
      final saved = await ref
          .read(planRepositoryProvider)
          .patchMilestone(plan.id, milestoneId, request(original));
      _replaceMilestone(saved, busy: false);
      return const PlanActionSaved();
    } on ApiException catch (error) {
      _replaceMilestone(original, busy: false);
      if (_reloadCodes.contains(error.code)) {
        reload();
        return const PlanActionReloaded();
      }
      return PlanActionFailed(error);
    }
  }

  void _replaceMilestone(MilestoneView milestone, {required bool busy}) {
    final data = state.value;
    final plan = data?.plan;
    if (data == null || plan == null || !ref.mounted) {
      return;
    }
    state = AsyncData(
      data.copyWith(
        plan: plan.copyWith(
          milestones: [
            for (final current in plan.milestones) current.id == milestone.id ? milestone : current,
          ],
        ),
        busyMilestoneIds: busy
            ? {...data.busyMilestoneIds, milestone.id}
            : ({...data.busyMilestoneIds}..remove(milestone.id)),
      ),
    );
  }
}

final planControllerProvider = AsyncNotifierProvider.autoDispose<PlanController, PlanScreenData>(
  PlanController.new,
);
