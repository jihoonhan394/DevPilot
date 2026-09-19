import 'package:devpilot_app/core/api/cursor_list.dart';
import 'package:devpilot_app/core/api/error_message_mapper.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/time/time_zone_support.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/inline_error.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/features/skill/data/skill_history_models.dart';
import 'package:devpilot_app/features/skill/domain/skill_rule_text.dart';
import 'package:devpilot_app/features/skill/presentation/skill_history_controller.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// "레벨 변경 기록" of SCR-SKILL-DETAIL (docs/02 §3.10, docs/05 §6.3). A failure here stays in
/// this section: the axis table above it keeps working.
class SkillHistorySection extends ConsumerWidget {
  const SkillHistorySection({super.key, required this.skillId});

  final String skillId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final history = ref.watch(skillHistoryControllerProvider(skillId));
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          key: const Key('skillDetail.history'),
          padding: const EdgeInsets.only(bottom: AppSpacing.sm),
          child: SectionTitle(l10n.skillDetailHistory),
        ),
        history.when(
          loading: () => const SkeletonList(count: 2, lines: 2),
          error: (error, _) => Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              InlineError(message: messageFor(error, l10n)),
              Align(
                alignment: AlignmentDirectional.centerStart,
                child: TextButton(
                  key: const Key('skillDetail.historyRetryButton'),
                  onPressed: () => ref.invalidate(skillHistoryControllerProvider(skillId)),
                  child: Text(l10n.commonErrorRetry),
                ),
              ),
            ],
          ),
          data: (list) => _HistoryList(skillId: skillId, list: list),
        ),
      ],
    );
  }
}

class _HistoryList extends ConsumerWidget {
  const _HistoryList({required this.skillId, required this.list});

  final String skillId;
  final CursorList<SkillStateChangeView> list;

  Future<void> _loadMore(BuildContext context, WidgetRef ref, AppLocalizations l10n) async {
    final restarted = await ref.read(skillHistoryControllerProvider(skillId).notifier).loadMore();
    if (restarted && context.mounted) {
      showToast(context, l10n.errorInvalidCursor);
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    if (list.items.isEmpty) {
      return Text(l10n.skillDetailHistoryEmpty, key: const Key('skillDetail.historyEmpty'));
    }
    final timeZone = ref.watch(userTimeZoneProvider);
    final support = ref.watch(timeZoneSupportProvider);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final change in list.items)
          _ChangeRow(change: change, timeZone: timeZone, support: support),
        _HistoryMore(
          list: list,
          onLoadMore: () => _loadMore(context, ref, l10n),
        ),
      ],
    );
  }
}

/// `{날짜} · {축} {from} → {to}`, the rule sentence, and the events the rule looked at.
class _ChangeRow extends StatelessWidget {
  const _ChangeRow({required this.change, required this.timeZone, required this.support});

  final SkillStateChangeView change;
  final String timeZone;
  final TimeZoneSupport support;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final date = formatInstantMonthDay(change.changedAt, timeZone, support, l10n);
    final title =
        '$date · '
        '${l10n.skillDetailHistoryItem(change.axis.label(l10n), change.fromLevel, change.toLevel)}';
    return Padding(
      key: Key('skillDetail.change.${change.id}'),
      padding: const EdgeInsets.only(bottom: AppSpacing.md),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: Theme.of(context).textTheme.titleSmall),
          Text(skillRuleText(change.ruleCode, l10n)),
          _EvidenceList(change: change, rowTitle: title, timeZone: timeZone, support: support),
        ],
      ),
    );
  }
}

/// `근거 기록 {n}개`; expanding shows each event's type and plan date.
class _EvidenceList extends StatelessWidget {
  const _EvidenceList({
    required this.change,
    required this.rowTitle,
    required this.timeZone,
    required this.support,
  });

  final SkillStateChangeView change;

  /// Part of the expander's accessible name so rows with the same count differ (docs/02 A-6).
  final String rowTitle;
  final String timeZone;
  final TimeZoneSupport support;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final events = change.evidenceEvents;
    final label = l10n.skillDetailEvidenceCount(events.length);
    if (events.isEmpty) {
      return Text(label, style: Theme.of(context).textTheme.bodySmall);
    }
    return ExpansionTile(
      key: Key('skillDetail.evidence.${change.id}'),
      tilePadding: EdgeInsets.zero,
      childrenPadding: const EdgeInsets.only(left: AppSpacing.md, bottom: AppSpacing.sm),
      expandedCrossAxisAlignment: CrossAxisAlignment.start,
      title: Text(label, semanticsLabel: '$rowTitle $label'),
      children: [
        for (final event in events) _EvidenceRow(event: event),
      ],
    );
  }
}

class _EvidenceRow extends StatelessWidget {
  const _EvidenceRow({required this.event});

  final EvidenceEventView event;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final date = LocalDate.tryParse(event.planDate);
    final day = date == null ? event.planDate : formatPlanDate(date, l10n);
    final text = '${event.eventType.label(l10n)} · $day';
    return Text(
      event.invalidated ? l10n.skillDetailEvidenceInvalidated(text) : text,
      style: Theme.of(context).textTheme.bodySmall,
    );
  }
}

/// "더 보기", the progress while a page loads, and the failure row (docs/02 §3.3).
class _HistoryMore extends StatelessWidget {
  const _HistoryMore({required this.list, required this.onLoadMore});

  final CursorList<SkillStateChangeView> list;
  final VoidCallback onLoadMore;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final error = list.loadMoreError;
    if (error != null) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(messageFor(error, l10n)),
          TextButton(
            key: const Key('skillDetail.historyMoreRetryButton'),
            onPressed: onLoadMore,
            child: Text(l10n.commonLoadMoreRetry),
          ),
        ],
      );
    }
    if (list.isLoadingMore) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: AppSpacing.sm),
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (!list.hasMore) {
      return const SizedBox.shrink();
    }
    return Align(
      alignment: AlignmentDirectional.centerStart,
      child: TextButton(
        key: const Key('skillDetail.historyMoreButton'),
        onPressed: onLoadMore,
        child: Text(l10n.skillDetailHistoryMore),
      ),
    );
  }
}
