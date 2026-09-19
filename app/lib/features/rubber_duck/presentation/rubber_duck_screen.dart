import 'dart:async';

import 'package:devpilot_app/app/not_found_screen.dart';
import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/widgets/ai_status_widgets.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_local_store.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_actions.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_body.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_controller.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_input_guard.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_state.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// SCR-RUBBER-DUCK: explain in your own words, answer the AI's questions, then summarize the gaps
/// into review cards (docs/02 §3.16, FR-25). A focus screen without the navigation frame.
class RubberDuckScreen extends ConsumerStatefulWidget {
  const RubberDuckScreen({super.key, required this.route, this.taskId, this.preview});

  final RubberDuckRoute route;

  /// The Today task to return to ("Today로 돌아가 완료하기").
  final String? taskId;

  /// Target title and summary from the entry screen (`extra`), shown before the first send.
  final RubberDuckTargetPreview? preview;

  @override
  ConsumerState<RubberDuckScreen> createState() => _RubberDuckScreenState();
}

class _RubberDuckScreenState extends ConsumerState<RubberDuckScreen> {
  static const _draftDelay = Duration(seconds: 1);
  final _input = TextEditingController();
  final _inputFocus = FocusNode();
  late final AppLifecycleListener _lifecycle;
  Timer? _draftTimer;

  @override
  void initState() {
    super.initState();
    final sessionId = widget.route.sessionId;
    if (sessionId != null) {
      _input.text = ref.read(rubberDuckLocalStoreProvider).readDraft(sessionId);
    }
    _input.addListener(_inputChanged);
    // Another device may have ended the session while this tab was hidden.
    _lifecycle = AppLifecycleListener(
      onShow: () => ref.read(rubberDuckControllerProvider(widget.route).notifier).reload(),
    );
    Future.microtask(_inputChanged);
  }

  @override
  void dispose() {
    _draftTimer?.cancel();
    _lifecycle.dispose();
    _input.dispose();
    _inputFocus.dispose();
    super.dispose();
  }

  void _inputChanged() {
    if (!mounted) {
      return;
    }
    ref.read(rubberDuckInputGuardProvider.notifier).set(dirty: _input.text.trim().isNotEmpty);
    final sessionId = widget.route.sessionId;
    if (sessionId == null) {
      return;
    }
    _draftTimer?.cancel();
    _draftTimer = Timer(_draftDelay, () {
      ref.read(rubberDuckLocalStoreProvider).writeDraft(sessionId, _input.text);
    });
  }

  RubberDuckActions _actions(BuildContext context) => RubberDuckActions(
    context: context,
    ref: ref,
    route: widget.route,
    taskId: widget.taskId,
    input: _input,
  );

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final provider = rubberDuckControllerProvider(widget.route);
    final screen = ref.watch(provider);
    final error = screen.error;
    if (error is ApiException && error.code == ApiErrorCode.resourceNotFound) {
      return const NotFoundScreen();
    }
    final data = screen.value;
    final session = data?.session;
    final ended = session != null && !session.inProgress;
    final actions = _actions(context);
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          key: const Key('rubberDuck.exitButton'),
          tooltip: l10n.rubberDuckExit,
          icon: const Icon(Icons.close),
          onPressed: actions.exit,
        ),
        title: Semantics(
          header: true,
          child: Text(ended ? l10n.rubberDuckSummaryTitle : l10n.rubberDuckTitle),
        ),
        actions: [
          if (session != null && session.inProgress)
            _SessionActions(
              turnCount: session.turnCount,
              maxTurns: session.maxTurns,
              onAbandon: data!.busy ? null : actions.abandon,
            ),
        ],
      ),
      body: Column(
        children: [
          AiUnavailableBanner(status: ref.watch(aiStatusProvider)),
          Expanded(
            child: ScreenBody(
              child: screen.when(
                loading: () => const SkeletonList(count: 2, lines: 3),
                error: (error, _) => ErrorView(
                  error: error,
                  onRetry: () => ref.read(provider.notifier).reload(),
                ),
                data: (data) => RubberDuckBody(
                  data: data,
                  preview: widget.preview,
                  input: _input,
                  inputFocus: _inputFocus,
                  actions: actions,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// "턴 {n} / {max}" and `⋮` with "그만두기" while the session is in progress.
class _SessionActions extends StatelessWidget {
  const _SessionActions({
    required this.turnCount,
    required this.maxTurns,
    required this.onAbandon,
  });

  final int turnCount;
  final int maxTurns;
  final VoidCallback? onAbandon;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(l10n.rubberDuckTurns(turnCount, maxTurns), key: const Key('rubberDuck.turnCount')),
        PopupMenuButton<void>(
          key: const Key('rubberDuck.menuButton'),
          tooltip: l10n.rubberDuckMenu,
          itemBuilder: (_) => [
            PopupMenuItem<void>(
              key: const Key('rubberDuck.abandonMenuItem'),
              enabled: onAbandon != null,
              onTap: onAbandon,
              child: Text(l10n.rubberDuckMenuAbandon),
            ),
          ],
        ),
      ],
    );
  }
}
