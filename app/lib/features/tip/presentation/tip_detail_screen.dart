import 'dart:async';

import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/action_error.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/tip/data/tip_models.dart';
import 'package:devpilot_app/features/tip/data/tip_repository.dart';
import 'package:devpilot_app/features/tip/presentation/tip_providers.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';

/// SCR-TIP-DETAIL (docs/02 §3.17, docs/05 §20.4a).
///
/// 순서는 고정이다 — 증상 → 원인 → 예제 → 확인할 곳 → 5분 실험. 증상을 먼저 두는 이유는 "이걸 본 적 있다"에서 시작해야 원인을 읽을
/// 이유가 생기기 때문이다.
class TipDetailScreen extends ConsumerWidget {
  const TipDetailScreen({super.key, required this.tipKey});

  final String tipKey;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final tip = ref.watch(tipProvider(tipKey));
    return Scaffold(
      appBar: AppBar(title: Semantics(header: true, child: Text(l10n.tipDetailTitle))),
      body: ScreenBody(
        child: tip.when(
          loading: () => const SkeletonList(count: 4, lines: 3),
          error: (error, _) =>
              ErrorView(error: error, onRetry: () => ref.invalidate(tipProvider(tipKey))),
          data: (data) => _TipBody(tip: data),
        ),
      ),
    );
  }
}

class _TipBody extends StatelessWidget {
  const _TipBody({required this.tip});

  final DailyTipView tip;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final example = tip.example;
    final experiment = tip.experiment;
    final sourceUrl = tip.sourceUrl;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Wrap(
          spacing: AppSpacing.sm,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            _Tag(tip.series.label(l10n)),
            _Tag(tip.level.label(l10n)),
            Text(formatMinutes(tip.estimatedMinutes, l10n), style: theme.textTheme.bodySmall),
          ],
        ),
        const SizedBox(height: AppSpacing.sm),
        Semantics(header: true, child: Text(tip.title, style: theme.textTheme.titleLarge)),
        const Divider(height: AppSpacing.xl),
        _Section(title: l10n.tipDetailSymptom, body: tip.symptom),
        _Section(title: l10n.tipDetailCause, body: tip.cause),
        if (example != null) _ExampleSection(example: example),
        _Section(title: l10n.tipDetailWhereToLook, body: tip.whereToLook),
        if (experiment != null) _Section(title: l10n.tipDetailExperiment, body: experiment),
        if (sourceUrl != null) _SourceLink(url: sourceUrl),
        if (tip.skills.isNotEmpty) _SkillChips(tip: tip),
        const Divider(height: AppSpacing.xl),
        _FeedbackSection(tip: tip),
      ],
    );
  }
}

class _Section extends StatelessWidget {
  const _Section({required this.title, required this.body});

  final String title;
  final String body;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: AppSpacing.lg),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionTitle(title),
        const SizedBox(height: AppSpacing.xs),
        Text(body, style: Theme.of(context).textTheme.bodyMedium),
      ],
    ),
  );
}

/// 코드라 기본 접힘이고, 펼치면 monospace에 가로 스크롤을 허용한다 (docs/02 U-7·A-11).
class _ExampleSection extends StatelessWidget {
  const _ExampleSection({required this.example});

  final String example;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.lg),
      child: ExpansionTile(
        key: const Key('tip.example'),
        tilePadding: EdgeInsets.zero,
        title: Text(l10n.tipDetailExample),
        children: [
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Padding(
              padding: const EdgeInsets.only(bottom: AppSpacing.sm),
              child: Text(example, style: const TextStyle(fontFamily: 'monospace')),
            ),
          ),
        ],
      ),
    );
  }
}

/// 사용자 브라우저가 여는 링크다 — 서버는 이 주소를 가져오지 않는다 (docs/07 §5).
class _SourceLink extends StatelessWidget {
  const _SourceLink({required this.url});

  final String url;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final host = Uri.tryParse(url)?.host ?? url;
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.md),
      child: Align(
        alignment: Alignment.centerLeft,
        child: TextButton.icon(
          // 공식 문서는 그 사이트에서 읽는다: 앱도 서버도 이 주소를 가져오지 않는다.
          onPressed: () => launchUrl(Uri.parse(url), webOnlyWindowName: '_blank'),
          icon: const Icon(Icons.open_in_new, size: 16),
          label: Text('${l10n.tipDetailSource} · $host'),
        ),
      ),
    );
  }
}

class _SkillChips extends StatelessWidget {
  const _SkillChips({required this.tip});

  final DailyTipView tip;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.md),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SectionTitle(l10n.tipDetailSkills),
          const SizedBox(height: AppSpacing.xs),
          Wrap(
            spacing: AppSpacing.sm,
            runSpacing: AppSpacing.xs,
            children: [
              for (final skill in tip.skills)
                ActionChip(
                  label: Text(skill.name),
                  onPressed: () => context.push(AppRoutes.skillDetail(skill.id)),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

/// 칩 3개. 한 번 고르면 바꿀 수 없다 — 서버가 덮어쓰지 않고 현재 값을 돌려준다 (docs/05 §20.3).
class _FeedbackSection extends ConsumerStatefulWidget {
  const _FeedbackSection({required this.tip});

  final DailyTipView tip;

  @override
  ConsumerState<_FeedbackSection> createState() => _FeedbackSectionState();
}

class _FeedbackSectionState extends ConsumerState<_FeedbackSection> {
  bool _sending = false;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tip = widget.tip;
    // 받아 본 적 없는 팁은 서버가 404로 막는다 — 누르기 전에 말해 준다 (docs/02 §3.17)
    if (tip.shownOn == null) {
      return Text(l10n.tipFeedbackNotShownYet, style: Theme.of(context).textTheme.bodySmall);
    }
    final chosen = tip.feedback;
    final locked = chosen != null;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionTitle(l10n.tipDetailFeedbackTitle),
        const SizedBox(height: AppSpacing.sm),
        Wrap(
          spacing: AppSpacing.sm,
          runSpacing: AppSpacing.xs,
          children: [
            for (final feedback in TipFeedback.known)
              ChoiceChip(
                key: Key('tip.feedback.${feedback.name}'),
                label: Text(feedback.label(l10n)),
                selected: chosen == feedback,
                onSelected: locked || _sending ? null : (_) => unawaited(_choose(feedback)),
              ),
          ],
        ),
        if (locked) ...[
          const SizedBox(height: AppSpacing.sm),
          Text(l10n.tipFeedbackLocked, style: Theme.of(context).textTheme.bodySmall),
        ],
      ],
    );
  }

  Future<void> _choose(TipFeedback feedback) async {
    setState(() => _sending = true);
    final l10n = AppLocalizations.of(context);
    try {
      await ref
          .read(tipRepositoryProvider)
          .chooseFeedback(
            widget.tip.tipKey,
            feedback,
            idempotencyKey: IdempotencyKey.generate(),
          );
      ref
        ..invalidate(tipProvider(widget.tip.tipKey))
        ..invalidate(todayTipProvider);
      if (!mounted) {
        return;
      }
      _announce(feedback, l10n);
    } on ApiException catch (error) {
      if (mounted) {
        await presentActionError(context, ref, error);
      }
    } finally {
      if (mounted) {
        setState(() => _sending = false);
      }
    }
  }

  void _announce(TipFeedback feedback, AppLocalizations l10n) {
    switch (feedback) {
      case TipFeedback.learned:
        showToast(
          context,
          l10n.tipFeedbackLearnedToast,
          actionLabel: l10n.tipFeedbackReview,
          onAction: () => context.push(AppRoutes.review),
        );
      case TipFeedback.willTry:
        showToast(context, l10n.tipFeedbackWillTryToast);
      case TipFeedback.knewIt:
        showToast(context, l10n.tipFeedbackKnewItToast);
      case TipFeedback.unknown:
        break;
    }
  }
}

class _Tag extends StatelessWidget {
  const _Tag(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.sm, vertical: 2),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(AppRadius.sm),
      ),
      child: Text(text, style: theme.textTheme.labelSmall),
    );
  }
}
