import 'dart:async';

import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_actions.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_callouts.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_conversation.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_input.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_result_view.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_state.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_target_card.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// The four states of SCR-RUBBER-DUCK: ① before a session, ② the conversation, ③ summarizing and
/// ④ the result. Desktop (≥ 1024) keeps the target card on the left (35 %).
class RubberDuckBody extends StatelessWidget {
  const RubberDuckBody({
    super.key,
    required this.data,
    required this.preview,
    required this.input,
    required this.inputFocus,
    required this.actions,
  });

  final RubberDuckScreenData data;
  final RubberDuckTargetPreview? preview;
  final TextEditingController input;
  final FocusNode inputFocus;
  final RubberDuckActions actions;

  @override
  Widget build(BuildContext context) {
    final session = data.session;
    final desktop = MediaQuery.sizeOf(context).width >= AppBreakpoints.desktop;
    final target = RubberDuckTargetCard(
      type: data.targetType,
      title: session?.targetTitle ?? preview?.title,
      summary: session == null ? preview?.summary : null,
      initiallyExpanded: desktop || session == null,
    );
    final main = session != null && !session.inProgress
        ? RubberDuckResultView(data: data, taskId: actions.taskId, onClose: actions.exit)
        : _Conversation(data: data, input: input, inputFocus: inputFocus, actions: actions);
    if (!desktop) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          target,
          const SizedBox(height: AppSpacing.lg),
          main,
        ],
      );
    }
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(flex: 35, child: target),
        const SizedBox(width: AppSpacing.xl),
        Expanded(flex: 65, child: main),
      ],
    );
  }
}

/// ① and ②: intro (before a session), the bubbles, the stuck callout, then the input or the
/// turn-limit summary button.
class _Conversation extends ConsumerWidget {
  const _Conversation({
    required this.data,
    required this.input,
    required this.inputFocus,
    required this.actions,
  });

  final RubberDuckScreenData data;
  final TextEditingController input;
  final FocusNode inputFocus;
  final RubberDuckActions actions;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final session = data.session;
    final aiStatus = ref.watch(aiStatusProvider);
    final limit = session != null && session.turnLimitReached;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (session == null) Text(l10n.rubberDuckIntro(5), key: const Key('rubberDuck.intro')),
        if (session != null)
          RubberDuckConversation(turns: session.turns, pendingText: data.pendingText)
        else if (data.pendingText != null)
          RubberDuckConversation(turns: const [], pendingText: data.pendingText),
        if (data.summarizing) const SummarizingNote(),
        if (session != null && session.suggestHint && !data.busy)
          StuckHintCallout(
            targetType: session.targetType,
            onContinue: inputFocus.requestFocus,
            onToHints: actions.toHints,
            onFinish: () => unawaited(actions.finish()),
          ),
        const SizedBox(height: AppSpacing.md),
        if (limit) ...[
          TurnLimitReached(onSummarize: data.busy ? null : () => unawaited(actions.finish())),
          _KeptExplanation(input: input),
        ] else
          RubberDuckInput(
            controller: input,
            focusNode: inputFocus,
            first: session == null,
            aiStatus: aiStatus,
            busy: data.busy,
            inputError: data.inputError,
            remainingTurns: session == null ? null : session.maxTurns - session.turnCount,
            onSend: () => unawaited(actions.send()),
            onFinish: session == null ? null : () => unawaited(actions.finish()),
          ),
      ],
    );
  }
}

/// After `409 INVALID_STATE_TRANSITION` the typed explanation stays for a while with "복사".
class _KeptExplanation extends StatelessWidget {
  const _KeptExplanation({required this.input});

  final TextEditingController input;

  @override
  Widget build(BuildContext context) {
    final text = input.text;
    if (text.trim().isEmpty) {
      return const SizedBox.shrink();
    }
    final l10n = AppLocalizations.of(context);
    return Card(
      key: const Key('rubberDuck.keptExplanation'),
      child: ListTile(
        title: Text(text, maxLines: 3, overflow: TextOverflow.ellipsis),
        trailing: TextButton(
          key: const Key('rubberDuck.copyButton'),
          onPressed: () async {
            await Clipboard.setData(ClipboardData(text: text));
            if (context.mounted) {
              showToast(context, l10n.commonErrorCopied);
            }
          },
          child: Text(l10n.rubberDuckCopy),
        ),
      ),
    );
  }
}
