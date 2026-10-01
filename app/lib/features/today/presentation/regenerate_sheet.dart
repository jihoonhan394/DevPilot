import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/features/today/presentation/today_input_controller.dart';
import 'package:devpilot_app/features/today/presentation/today_input_fields.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// `RegenerateSheet`: minutes and energy again, then [submitLabel] (docs/02 SCR-TODAY).
/// Pops `true` when the user asked for a new plan.
class RegenerateSheet extends ConsumerWidget {
  const RegenerateSheet({super.key, required this.submitLabel, this.note});

  final String submitLabel;

  /// Shown above the button ("하나 더 하기": the completed task stays recorded).
  final String? note;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final input = ref.watch(todayInputProvider);
    final controller = ref.read(todayInputProvider.notifier);
    final noteText = note;
    return SingleChildScrollView(
      padding: const EdgeInsets.all(AppSpacing.xl),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TodayInputFields(
            minutes: input.minutes,
            energy: input.energy,
            enabled: true,
            onMinutesChanged: controller.setMinutes,
            onEnergyChanged: controller.setEnergy,
          ),
          if (noteText != null) ...[
            const SizedBox(height: AppSpacing.lg),
            Text(noteText, key: const Key('today.regenerateNote')),
          ],
          const SizedBox(height: AppSpacing.xl),
          FilledButton(
            key: const Key('today.regenerateSubmitButton'),
            onPressed: () => Navigator.of(context).pop(true),
            child: Text(submitLabel),
          ),
        ],
      ),
    );
  }
}
