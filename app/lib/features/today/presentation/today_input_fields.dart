import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/minutes_input_dialog.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/features/today/domain/today_rules.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// `MinutesChips` + `EnergySegmented` of SCR-TODAY and its regenerate sheet (docs/02 SCR-TODAY).
class TodayInputFields extends StatelessWidget {
  const TodayInputFields({
    super.key,
    required this.minutes,
    required this.energy,
    required this.enabled,
    required this.onMinutesChanged,
    required this.onEnergyChanged,
  });

  final int minutes;
  final EnergyLevel energy;
  final bool enabled;
  final ValueChanged<int> onMinutesChanged;
  final ValueChanged<EnergyLevel> onEnergyChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionTitle(l10n.todayMinutesQuestion),
        const SizedBox(height: AppSpacing.sm),
        _MinutesChips(minutes: minutes, enabled: enabled, onChanged: onMinutesChanged),
        const SizedBox(height: AppSpacing.xl),
        SectionTitle(l10n.todayEnergyLabel),
        const SizedBox(height: AppSpacing.sm),
        SegmentedButton<EnergyLevel>(
          key: const Key('today.energy'),
          segments: [
            for (final level in EnergyLevel.known)
              ButtonSegment(
                value: level,
                label: Text(level.label(l10n), key: Key('today.energy.${level.name}')),
              ),
          ],
          selected: {energy},
          showSelectedIcon: false,
          onSelectionChanged: enabled ? (selection) => onEnergyChanged(selection.first) : null,
        ),
      ],
    );
  }
}

class _MinutesChips extends StatelessWidget {
  const _MinutesChips({required this.minutes, required this.enabled, required this.onChanged});

  final int minutes;
  final bool enabled;
  final ValueChanged<int> onChanged;

  Future<void> _pickCustom(BuildContext context) async {
    final l10n = AppLocalizations.of(context);
    final picked = await showMinutesInputDialog(
      context,
      title: l10n.todayMinutesDialogTitle,
      initialMinutes: minutes,
      minMinutes: TodayRules.minMinutes,
      maxMinutes: TodayRules.maxMinutes,
    );
    if (picked != null) {
      onChanged(picked);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final custom = !TodayRules.isChipValue(minutes);
    return Wrap(
      spacing: AppSpacing.sm,
      runSpacing: AppSpacing.sm,
      children: [
        for (final value in TodayRules.minuteChips)
          ChoiceChip(
            key: Key('today.minutes.$value'),
            // The chips read "60분", "120분" as in the wireframe, not "1시간".
            label: Text(l10n.commonMinutes(value)),
            selected: value == minutes,
            onSelected: enabled ? (_) => onChanged(value) : null,
          ),
        ChoiceChip(
          key: const Key('today.minutes.custom'),
          label: Text(
            custom
                ? l10n.todayMinutesCustomValue(formatMinutes(minutes, l10n))
                : l10n.todayMinutesCustom,
          ),
          selected: custom,
          onSelected: enabled ? (_) => _pickCustom(context) : null,
        ),
      ],
    );
  }
}
