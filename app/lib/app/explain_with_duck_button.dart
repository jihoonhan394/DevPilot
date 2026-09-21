import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/widgets/ai_status_widgets.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// Where a rubber duck entry button leads (docs/02 §2.3 `/rubber-duck/new` parameters).
@immutable
final class RubberDuckLaunch {
  const RubberDuckLaunch({
    required this.targetType,
    this.targetId,
    this.conceptKey,
    this.skillCode,
    this.taskId,
    this.preview,
  });

  final RubberDuckTargetType targetType;
  final String? targetId;
  final String? conceptKey;
  final String? skillCode;
  final String? taskId;

  /// Title and summary shown on the target card before the first send.
  final RubberDuckTargetPreview? preview;

  String get location => AppRoutes.rubberDuckStart(
    targetType: targetType.wireName,
    targetId: targetId,
    conceptKey: conceptKey,
    skillCode: skillCode,
    taskId: taskId,
  );
}

/// Opens SCR-RUBBER-DUCK. The URL follows the screen (a reload keeps it); "✕" returns to the
/// target's own screen.
void openRubberDuck(BuildContext context, RubberDuckLaunch launch) =>
    context.go(launch.location, extra: launch.preview);

/// A rubber duck entry button (docs/02 §6.5): the rubber duck needs the AI, so the button is
/// disabled while `aiStatus` is DISABLED or BALANCE_EXHAUSTED with the reason below it, and shows
/// the budget warning otherwise. Screens without an AI banner use this alone.
class ExplainWithDuckButton extends ConsumerWidget {
  const ExplainWithDuckButton({
    super.key,
    required this.buttonKey,
    required this.label,
    required this.launch,
    this.style = ExplainButtonStyle.text,
    this.semanticsLabel,
    this.beforeOpen,
  });

  final Key buttonKey;
  final String label;
  final RubberDuckLaunch launch;
  final ExplainButtonStyle style;

  /// A distinct name when several such buttons share one screen.
  final String? semanticsLabel;

  /// Runs just before the duck opens. SCR-LESSON counts this as help (docs/05 §21.7).
  final VoidCallback? beforeOpen;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final status = ref.watch(aiStatusProvider);
    final onPressed = status.allowsAi
        ? () {
            beforeOpen?.call();
            openRubberDuck(context, launch);
          }
        : null;
    final text = Text(label, semanticsLabel: semanticsLabel);
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        switch (style) {
          ExplainButtonStyle.filled => FilledButton(
            key: buttonKey,
            onPressed: onPressed,
            child: text,
          ),
          ExplainButtonStyle.outlined => OutlinedButton(
            key: buttonKey,
            onPressed: onPressed,
            child: text,
          ),
          ExplainButtonStyle.text => TextButton(key: buttonKey, onPressed: onPressed, child: text),
        },
        AiActionNote(status: status),
      ],
    );
  }
}

enum ExplainButtonStyle { filled, outlined, text }
