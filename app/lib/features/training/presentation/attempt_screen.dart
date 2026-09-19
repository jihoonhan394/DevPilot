import 'dart:async';

import 'package:devpilot_app/app/not_found_screen.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/ai_status_widgets.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/features/training/presentation/attempt_actions.dart';
import 'package:devpilot_app/features/training/presentation/attempt_controller.dart';
import 'package:devpilot_app/features/training/presentation/attempt_input_guard.dart';
import 'package:devpilot_app/features/training/presentation/attempt_state.dart';
import 'package:devpilot_app/features/training/presentation/challenge_body.dart';
import 'package:devpilot_app/features/training/presentation/hint_ladder_view.dart';
import 'package:devpilot_app/features/training/presentation/self_explanation_section.dart';
import 'package:devpilot_app/features/training/presentation/submission_section.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// SCR-TRAINING-ATTEMPT: explain first, take hints one rung at a time, submit and wait for the
/// evaluation (docs/02 §3.7, §4.5). Desktop shows the problem on the left (40 %) and the steps on
/// the right (60 %).
class AttemptScreen extends ConsumerStatefulWidget {
  const AttemptScreen({super.key, required this.attemptId, this.taskId, this.focusHints = false});

  final String attemptId;

  /// `?taskId=`: the Today CHALLENGE task ("Today로 돌아가 완료하기").
  final String? taskId;

  /// `#hints`: back from the rubber duck, the next hint button gets focus (RD-3).
  final bool focusHints;

  @override
  ConsumerState<AttemptScreen> createState() => _AttemptScreenState();
}

class _AttemptScreenState extends ConsumerState<AttemptScreen> {
  late final AppLifecycleListener _lifecycle;
  final _hintsKey = GlobalKey();
  final _hintFocus = FocusNode();
  var _hintsFocused = false;

  AttemptController get _controller =>
      ref.read(attemptControllerProvider(widget.attemptId).notifier);

  @override
  void initState() {
    super.initState();
    // A hidden browser tab pauses the evaluation poll (docs/02 §6.3).
    _lifecycle = AppLifecycleListener(
      onHide: () => _controller.pausePolling(),
      onShow: () => _controller.unpausePolling(),
    );
    Future.microtask(() => ref.read(attemptInputGuardProvider.notifier).clear());
  }

  @override
  void dispose() {
    _lifecycle.dispose();
    _hintFocus.dispose();
    super.dispose();
  }

  void _focusHintsOnce(AttemptScreenData data) {
    if (!widget.focusHints || _hintsFocused || data.locked) {
      return;
    }
    _hintsFocused = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final target = _hintsKey.currentContext;
      if (target != null && target.mounted) {
        unawaited(Scrollable.ensureVisible(target));
        _hintFocus.requestFocus();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final screen = ref.watch(attemptControllerProvider(widget.attemptId));
    final error = screen.error;
    if (error is ApiException && error.code == ApiErrorCode.resourceNotFound) {
      return const NotFoundScreen();
    }
    final data = screen.value;
    if (data != null) {
      _focusHintsOnce(data);
    }
    return Scaffold(
      appBar: AppBar(
        title: Semantics(
          header: true,
          child: Text(data?.attempt.challengeTitle ?? l10n.trainingProblem),
        ),
        actions: [if (data != null) _AttemptMenu(data: data, taskId: widget.taskId)],
      ),
      body: Column(
        children: [
          AiUnavailableBanner(status: ref.watch(aiStatusProvider)),
          Expanded(
            child: ScreenBody(
              child: screen.when(
                loading: () => const SkeletonList(count: 3, lines: 3),
                error: (error, _) => ErrorView(error: error, onRetry: _controller.reload),
                data: (data) => _AttemptLayout(
                  data: data,
                  taskId: widget.taskId,
                  hintsKey: _hintsKey,
                  hintFocus: _hintFocus,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _AttemptLayout extends StatelessWidget {
  const _AttemptLayout({
    required this.data,
    required this.taskId,
    required this.hintsKey,
    required this.hintFocus,
  });

  final AttemptScreenData data;
  final String? taskId;
  final GlobalKey hintsKey;
  final FocusNode hintFocus;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final desktop = MediaQuery.sizeOf(context).width >= AppBreakpoints.desktop;
    final problem = _ProblemPanel(data: data, expanded: desktop);
    final steps = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (data.closed) ...[
          Text(l10n.trainingAttemptAbandoned, key: const Key('attempt.abandoned')),
          const SizedBox(height: AppSpacing.md),
        ],
        SelfExplanationSection(data: data),
        const Divider(height: AppSpacing.xxl),
        KeyedSubtree(
          key: hintsKey,
          child: HintLadderView(data: data, nextButtonFocus: hintFocus),
        ),
        const Divider(height: AppSpacing.xxl),
        SubmissionSection(data: data, taskId: taskId),
      ],
    );
    if (!desktop) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          problem,
          const Divider(height: AppSpacing.xxl),
          steps,
        ],
      );
    }
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(flex: 4, child: problem),
        const SizedBox(width: AppSpacing.xl),
        Expanded(flex: 6, child: steps),
      ],
    );
  }
}

/// `CollapsibleProblem`: meta line always visible, the problem text folded on mobile.
class _ProblemPanel extends StatelessWidget {
  const _ProblemPanel({required this.data, required this.expanded});

  final AttemptScreenData data;
  final bool expanded;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        ChallengeMeta(challenge: data.challenge),
        ExpansionTile(
          key: const Key('attempt.problem'),
          tilePadding: EdgeInsets.zero,
          initiallyExpanded: true,
          maintainState: true,
          title: Text(l10n.trainingProblem),
          children: [ChallengeBody(challenge: data.challenge, showMeta: false)],
        ),
      ],
    );
  }
}

/// `⋮` with "그만두기"; disabled while an evaluation runs or after the attempt ended.
class _AttemptMenu extends ConsumerWidget {
  const _AttemptMenu({required this.data, required this.taskId});

  final AttemptScreenData data;
  final String? taskId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final canAbandon = !data.closed && !data.busy && !data.attempt.evaluating;
    return PopupMenuButton<void>(
      key: const Key('attempt.menuButton'),
      tooltip: l10n.trainingAttemptMenu,
      itemBuilder: (_) => [
        PopupMenuItem<void>(
          key: const Key('attempt.abandonMenuItem'),
          enabled: canAbandon,
          onTap: () => unawaited(abandonAttemptFlow(context, ref, data, taskId)),
          child: Text(l10n.trainingAttemptMenuAbandon),
        ),
      ],
    );
  }
}
