import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:devpilot_app/core/widgets/status_badge.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

// Badges of docs/02 §6.1 used in S1. Danger is only for CRITICAL (U-3).

class RiskBadge extends StatelessWidget {
  const RiskBadge({super.key, required this.risk});

  final RiskLevel risk;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return StatusBadge(
      label: risk.label(l10n),
      icon: Icons.schedule,
      semanticsGroup: l10n.planBudgetRisk,
      tone: switch (risk) {
        RiskLevel.low => AppTone.success,
        RiskLevel.medium => AppTone.info,
        RiskLevel.high => AppTone.warning,
        RiskLevel.critical => AppTone.danger,
        RiskLevel.unknown => AppTone.neutral,
      },
    );
  }
}

class PriorityBadge extends StatelessWidget {
  const PriorityBadge({super.key, required this.priority});

  final Priority priority;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return StatusBadge(
      label: priority.label(l10n),
      semanticsGroup: l10n.commonPriority,
      tone: priority == Priority.must ? AppTone.primary : AppTone.neutral,
    );
  }
}

class SideProjectStatusBadge extends StatelessWidget {
  const SideProjectStatusBadge({super.key, required this.status});

  final SideProjectStatus status;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return StatusBadge(
      label: status.label(l10n),
      semanticsGroup: l10n.projectsFieldStatus,
      tone: switch (status) {
        SideProjectStatus.active => AppTone.primary,
        SideProjectStatus.done => AppTone.success,
        SideProjectStatus.paused || SideProjectStatus.unknown => AppTone.neutral,
      },
    );
  }
}
