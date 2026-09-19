import 'dart:async';

import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/time/clock.dart';
import 'package:devpilot_app/core/time/session_time_rules.dart';
import 'package:devpilot_app/features/today/data/today_models.dart';
import 'package:devpilot_app/features/today/presentation/task_type_links.dart';
import 'package:devpilot_app/features/today/presentation/today_actions.dart';
import 'package:devpilot_app/features/today/presentation/today_controller.dart';
import 'package:devpilot_app/features/today/presentation/today_state.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// Footer of the main task card for each status (docs/02 SCR-TODAY table
/// "main task 상태별 카드 하단"). One filled button at most (U-1).
class MainTaskActions extends ConsumerWidget {
  const MainTaskActions({super.key, required this.task, required this.data});

  final MainTaskView task;
  final TodayScreenData data;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final enabled = !data.busy;
    return switch (task.status) {
      TaskStatus.planned => _Buttons(
        primary: _Action('today.startButton', l10n.todayStartButton, () {
          unawaited(_start(context, ref));
        }),
        secondary: _Action('today.skipButton', l10n.todaySkipButton, () {
          unawaited(changeTodayMainStatus(context, ref, TaskStatus.skipped));
        }),
        enabled: enabled,
      ),
      TaskStatus.inProgress => _InProgressFooter(data: data, enabled: enabled),
      TaskStatus.completed => _CompletedFooter(task: task, data: data, enabled: enabled),
      TaskStatus.skipped => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(l10n.todaySkipped, key: const Key('today.statusText')),
          const SizedBox(height: AppSpacing.md),
          _Buttons(
            primary: _regenerateAction(context, ref, l10n),
            secondary: data.canUndoSkip
                ? _Action('today.undoButton', l10n.commonUndo, () {
                    unawaited(changeTodayMainStatus(context, ref, TaskStatus.planned));
                  })
                : null,
            enabled: enabled,
          ),
        ],
      ),
      TaskStatus.deferred => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(l10n.todayDeferred, key: const Key('today.statusText')),
          const SizedBox(height: AppSpacing.md),
          _Buttons(primary: _regenerateAction(context, ref, l10n), enabled: enabled),
        ],
      ),
      TaskStatus.unknown => Text(l10n.enumUnknown, key: const Key('today.statusText')),
    };
  }

  _Action _regenerateAction(BuildContext context, WidgetRef ref, AppLocalizations l10n) =>
      _Action('today.regenerateButton', l10n.todayRegenerate, () {
        unawaited(regenerateToday(context, ref, mainStatus: task.status));
      });

  /// "시작", then the task's own screen for CHALLENGE and READ_CODE.
  Future<void> _start(BuildContext context, WidgetRef ref) async {
    final outcome = await ref.read(todayControllerProvider.notifier).start();
    if (!context.mounted) {
      return;
    }
    final location = startLocationOf(task);
    if (outcome is TodayActionDone && location != null) {
      context.go(location);
      return;
    }
    await presentTodayOutcome(context, ref, outcome);
  }
}

/// "진행 중 · {n}분째" + "완료" / "여기까지 기록". Without a running session of this task the
/// primary button starts one again (the task was started elsewhere or its session was closed).
class _InProgressFooter extends ConsumerWidget {
  const _InProgressFooter({required this.data, required this.enabled});

  final TodayScreenData data;
  final bool enabled;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final session = data.mainSession;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (session == null)
          Text(l10n.todayInProgressNoSession, key: const Key('today.statusText'))
        else
          _ElapsedText(startedAt: session.startedAt),
        const SizedBox(height: AppSpacing.md),
        if (session == null)
          _Buttons(
            primary: _Action('today.startButton', l10n.todayStartButton, () async {
              final outcome = await ref.read(todayControllerProvider.notifier).start();
              if (context.mounted) {
                await presentTodayOutcome(context, ref, outcome);
              }
            }),
            enabled: enabled,
          )
        else
          _Buttons(
            primary: _Action('today.completeButton', l10n.todayCompleteButton, () {
              unawaited(openCompleteSheet(context, ref, partial: false));
            }),
            secondary: _Action('today.partialButton', l10n.todayPartialButton, () {
              unawaited(openCompleteSheet(context, ref, partial: true));
            }),
            enabled: enabled,
          ),
        if (data.mainTask case final task?) TaskTypeLinks(task: task, enabled: enabled),
      ],
    );
  }
}

class _CompletedFooter extends ConsumerWidget {
  const _CompletedFooter({required this.task, required this.data, required this.enabled});

  final MainTaskView task;
  final TodayScreenData data;
  final bool enabled;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final colorScheme = Theme.of(context).colorScheme;
    final minutes = data.recordedMinutes(task.id);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Icon(Icons.check_circle, color: colorScheme.primary),
            const SizedBox(width: AppSpacing.sm),
            Expanded(child: Text(l10n.todayCompleted, key: const Key('today.statusText'))),
          ],
        ),
        if (minutes > 0) Text(l10n.todayCompletedMinutes(formatMinutes(minutes, l10n))),
        const SizedBox(height: AppSpacing.md),
        _Buttons(
          secondary: _Action('today.oneMoreButton', l10n.todayOneMore, () {
            unawaited(regenerateToday(context, ref, mainStatus: TaskStatus.completed));
          }),
          enabled: enabled,
        ),
      ],
    );
  }
}

/// "진행 중 · {n}분째", refreshed every minute from the session start.
class _ElapsedText extends ConsumerStatefulWidget {
  const _ElapsedText({required this.startedAt});

  final DateTime startedAt;

  @override
  ConsumerState<_ElapsedText> createState() => _ElapsedTextState();
}

class _ElapsedTextState extends ConsumerState<_ElapsedText> {
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(minutes: 1), (_) => setState(() {}));
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final now = ref.watch(clockProvider)();
    final minutes = SessionTimeRules.elapsedMinutes(widget.startedAt, now);
    return Text(
      l10n.todayInProgress(formatMinutes(minutes, l10n)),
      key: const Key('today.statusText'),
    );
  }
}

final class _Action {
  const _Action(this.key, this.label, this.onPressed);

  final String key;
  final String label;
  final VoidCallback onPressed;
}

/// A filled primary button and a text secondary button, both disabled while [enabled] is false.
class _Buttons extends StatelessWidget {
  const _Buttons({this.primary, this.secondary, required this.enabled});

  final _Action? primary;
  final _Action? secondary;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final main = primary;
    final other = secondary;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (main != null)
          FilledButton(
            key: Key(main.key),
            onPressed: enabled ? main.onPressed : null,
            child: Text(main.label),
          ),
        if (other != null)
          TextButton(
            key: Key(other.key),
            onPressed: enabled ? other.onPressed : null,
            child: Text(other.label),
          ),
      ],
    );
  }
}
