import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/features/plan/data/learning_goal_models.dart';
import 'package:devpilot_app/features/plan/data/plan_models.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// Horizontal timeline above the milestone list from 600 px (docs/02 SCR-PLAN "타임라인"):
/// month ticks, one bar per milestone with its priority as text, a vertical line for today and
/// a mark for the target date.
///
/// The list below carries the same facts in text, so the chart is hidden from screen readers
/// behind one summary label (docs/02 A-3).
class PlanTimelineBar extends StatelessWidget {
  const PlanTimelineBar({
    super.key,
    required this.milestones,
    required this.today,
    required this.goal,
  });

  final List<MilestoneView> milestones;
  final LocalDate today;
  final LearningGoalView? goal;

  static const _rowHeight = 28.0;
  static const _axisHeight = 20.0;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final completion = LocalDate.tryParse(goal?.targetCompletionDate);
    final dates = [
      for (final milestone in milestones) ...[
        LocalDate.parse(milestone.startDate),
        LocalDate.parse(milestone.endDate),
      ],
      today,
      ?completion,
    ]..sort();
    final scale = _TimeScale(
      first: LocalDate(dates.first.year, dates.first.month, 1),
      last: dates.last.addDays(1),
    );
    final months = [
      for (var month = scale.first; month.isBefore(scale.last); month = month.addMonths(1)) month,
    ];
    final height = _axisHeight + milestones.length * _rowHeight;
    return Semantics(
      label: l10n.planTimelineSummary(milestones.length),
      child: ExcludeSemantics(
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.md),
            child: LayoutBuilder(
              builder: (context, constraints) {
                double x(LocalDate date) => scale.fraction(date) * constraints.maxWidth;
                return SizedBox(
                  height: height,
                  child: Stack(
                    children: [
                      for (final month in months) _MonthTick(left: x(month), month: month.month),
                      for (var index = 0; index < milestones.length; index++)
                        _MilestoneBar(
                          milestone: milestones[index],
                          top: _axisHeight + index * _rowHeight + 2,
                          height: _rowHeight - 4,
                          left: x(LocalDate.parse(milestones[index].startDate)),
                          right: x(LocalDate.parse(milestones[index].endDate).addDays(1)),
                        ),
                      _DateMarker(left: x(today), label: l10n.planTimelineToday),
                      if (completion != null)
                        _DateMarker(left: x(completion), label: l10n.planTimelineCompletion),
                    ],
                  ),
                );
              },
            ),
          ),
        ),
      ),
    );
  }
}

/// Maps dates to 0..1 between [first] and [last].
final class _TimeScale {
  _TimeScale({required this.first, required this.last})
    : _totalDays = first.daysUntil(last) < 1 ? 1 : first.daysUntil(last);

  final LocalDate first;
  final LocalDate last;
  final int _totalDays;

  double fraction(LocalDate date) => first.daysUntil(date) / _totalDays;
}

class _MonthTick extends StatelessWidget {
  const _MonthTick({required this.left, required this.month});

  final double left;
  final int month;

  @override
  Widget build(BuildContext context) {
    return Positioned(
      left: left,
      top: 0,
      child: Text(
        AppLocalizations.of(context).planTimelineMonth(month),
        style: Theme.of(context).textTheme.bodySmall,
      ),
    );
  }
}

class _MilestoneBar extends StatelessWidget {
  const _MilestoneBar({
    required this.milestone,
    required this.top,
    required this.height,
    required this.left,
    required this.right,
  });

  final MilestoneView milestone;
  final double top;
  final double height;
  final double left;
  final double right;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final colors = DevPilotColors.of(context).tone(
      milestone.priority == Priority.must ? AppTone.primary : AppTone.neutral,
      Theme.of(context).colorScheme,
    );
    return Positioned(
      left: left,
      top: top,
      width: right - left < 4 ? 4 : right - left,
      height: height,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: colors.background,
          borderRadius: const BorderRadius.all(Radius.circular(AppRadius.sm)),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xs),
          child: Align(
            alignment: Alignment.centerLeft,
            child: Text(
              l10n.planTimelineBarLabel(milestone.priority.label(l10n), milestone.title),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.labelSmall?.copyWith(color: colors.foreground),
            ),
          ),
        ),
      ),
    );
  }
}

class _DateMarker extends StatelessWidget {
  const _DateMarker({required this.left, required this.label});

  final double left;
  final String label;

  @override
  Widget build(BuildContext context) {
    final color = Theme.of(context).colorScheme.primary;
    return Positioned(
      left: left,
      top: 0,
      bottom: 0,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: Theme.of(context).textTheme.labelSmall?.copyWith(color: color)),
          Expanded(
            child: SizedBox(width: 2, child: ColoredBox(color: color)),
          ),
        ],
      ),
    );
  }
}
