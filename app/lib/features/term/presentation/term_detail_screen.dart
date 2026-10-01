import 'dart:async';

import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/error_message_mapper.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/term/data/term_models.dart';
import 'package:devpilot_app/features/term/data/term_repository.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';

/// 용어 1건 (docs/05 §20.6). 헷갈리는 짝을 누르면 같은 화면이 새 라우트로 쌓인다.
final termProvider = FutureProvider.family<TermView, String>(
  (ref, termKey) => ref.watch(termRepositoryProvider).fetch(termKey),
);

/// SCR-TERM-DETAIL (docs/02 §3.17). 어떤 표기를 쓸지 정해 주는 것이 이 화면의 일이다.
///
/// 그래서 제목은 늘 대표 표기이고, 다른 표기는 "이렇게도 불러요" 한 줄로 내린다(docs/19 §3.10).
class TermDetailScreen extends ConsumerStatefulWidget {
  const TermDetailScreen({super.key, required this.termKey});

  final String termKey;

  @override
  ConsumerState<TermDetailScreen> createState() => _TermDetailScreenState();
}

class _TermDetailScreenState extends ConsumerState<TermDetailScreen> {
  /// 같은 행동을 재시도하면 같은 key를 쓴다 (docs/02 §6.6).
  final IdempotencyKeyCache _cardKey = IdempotencyKeyCache();
  bool _creating = false;

  /// 만들기 응답으로 받은 카드. 있으면 진입 때 읽은 값 대신 이것을 보인다.
  List<CreatedCardView>? _cards;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final term = ref.watch(termProvider(widget.termKey));
    return Scaffold(
      appBar: AppBar(title: Semantics(header: true, child: Text(l10n.termDetailTitle))),
      body: ScreenBody(
        child: term.when(
          loading: () => const SkeletonList(count: 4, lines: 3),
          error: (error, _) => ErrorView(
            error: error,
            onRetry: () => ref.invalidate(termProvider(widget.termKey)),
          ),
          data: (data) => _content(l10n, data),
        ),
      ),
    );
  }

  Widget _content(AppLocalizations l10n, TermView term) {
    final theme = Theme.of(context);
    final aliases = term.aliases;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(child: Text(term.representative, style: theme.textTheme.headlineSmall)),
            Text(term.level.label(l10n), style: theme.textTheme.labelSmall),
          ],
        ),
        Text(term.english, style: theme.textTheme.bodyMedium),
        if (term.retired) ...[
          const SizedBox(height: AppSpacing.xs),
          Text(
            l10n.termDetailRetired,
            key: const Key('term.retired'),
            style: theme.textTheme.bodySmall,
          ),
        ],
        if (aliases.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.xs),
          Text(
            '${l10n.termDetailAliases}: ${aliases.join(', ')}',
            key: const Key('term.aliases'),
            style: theme.textTheme.bodySmall,
          ),
        ],
        const Divider(height: AppSpacing.xl),
        _Section(title: l10n.termDetailDefinition, body: term.definition),
        _Section(title: l10n.termDetailExample, body: term.example),
        if (term.confusableWith.isNotEmpty) _confusable(l10n, term),
        if (term.skills.isNotEmpty) _skills(l10n, term),
        _sourceLink(l10n, term),
        const Divider(height: AppSpacing.xl),
        _cardSection(l10n, term),
      ],
    );
  }

  /// 헷갈리는 짝. 뜻은 주지 않는다 — 궁금하면 그 용어로 넘어간다.
  Widget _confusable(AppLocalizations l10n, TermView term) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      Text(l10n.termDetailConfusable, style: Theme.of(context).textTheme.titleSmall),
      const SizedBox(height: AppSpacing.xs),
      for (final other in term.confusableWith)
        Card(
          margin: const EdgeInsets.only(bottom: AppSpacing.xs),
          child: ListTile(
            key: Key('term.confusable.${other.termKey}'),
            title: Text(other.representative),
            subtitle: Text(other.english),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.push(AppRoutes.term(other.termKey)),
          ),
        ),
      const SizedBox(height: AppSpacing.md),
    ],
  );

  Widget _skills(AppLocalizations l10n, TermView term) => Padding(
    padding: const EdgeInsets.only(bottom: AppSpacing.md),
    child: Wrap(
      spacing: AppSpacing.sm,
      runSpacing: AppSpacing.xs,
      children: [
        Text(
          '${l10n.termDetailSkills}:',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        for (final skill in term.skills)
          ActionChip(
            key: Key('term.skill.${skill.code}'),
            label: Text(skill.name),
            onPressed: () => context.push(AppRoutes.skillDetail(skill.id)),
          ),
      ],
    ),
  );

  /// 사용자 브라우저가 여는 링크다 — 서버는 이 주소를 가져오지 않는다 (docs/07 §5).
  Widget _sourceLink(AppLocalizations l10n, TermView term) {
    final host = Uri.tryParse(term.sourceUrl)?.host ?? term.sourceUrl;
    return Align(
      alignment: Alignment.centerLeft,
      child: TextButton.icon(
        key: const Key('term.source'),
        onPressed: () => launchUrl(Uri.parse(term.sourceUrl), webOnlyWindowName: '_blank'),
        icon: const Icon(Icons.open_in_new, size: 16),
        label: Text('${l10n.termDetailSource} · $host'),
      ),
    );
  }

  /// 앞뒤 두 장을 만든다는 것을 <b>누르기 전에</b> 알린다 (docs/05 §20.7).
  Widget _cardSection(AppLocalizations l10n, TermView term) {
    final cards = _cards ?? term.cards;
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (cards.length >= 2)
          FilledButton(
            key: const Key('term.card.view'),
            onPressed: () => context.push(_reviewItemsPath(term)),
            child: Text(l10n.termCardView),
          )
        else
          FilledButton(
            key: const Key('term.card.create'),
            onPressed: _creating || term.skills.isEmpty ? null : () => unawaited(_createCard(l10n)),
            child: Text(l10n.termCardButton),
          ),
        const SizedBox(height: AppSpacing.xs),
        Text(
          term.skills.isEmpty ? l10n.termCardNoSkill : l10n.termCardHelp,
          style: theme.textTheme.bodySmall,
        ),
        for (final card in cards) ...[
          const SizedBox(height: AppSpacing.xs),
          Text(
            '${_direction(l10n, card)} · ${l10n.termCardNextReview(card.dueDate)}',
            key: Key('term.card.${card.conceptKey}'),
            style: theme.textTheme.labelSmall,
          ),
        ],
      ],
    );
  }

  String _reviewItemsPath(TermView term) =>
      AppRoutes.reviewItemsFor(skillId: term.skills.first.id, status: 'ACTIVE');

  static String _direction(AppLocalizations l10n, CreatedCardView card) =>
      card.conceptKey.endsWith(':REVERSE') ? l10n.termCardReverse : l10n.termCardForward;

  Future<void> _createCard(AppLocalizations l10n) async {
    setState(() => _creating = true);
    try {
      final response = await ref
          .read(termRepositoryProvider)
          .createCard(widget.termKey, idempotencyKey: _cardKey.keyFor(const {}));
      _cardKey.settle(null);
      if (!mounted) {
        return;
      }
      setState(() => _cards = response.cards);
      showToast(
        context,
        response.createdCount > 0 ? l10n.termCardCreated : l10n.termCardAlready,
      );
    } on Object catch (error) {
      _cardKey.settle(error);
      if (mounted) {
        showToast(context, messageFor(error, l10n));
      }
    } finally {
      if (mounted) {
        setState(() => _creating = false);
      }
    }
  }
}

class _Section extends StatelessWidget {
  const _Section({required this.title, required this.body});

  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.md),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: theme.textTheme.titleSmall),
          const SizedBox(height: AppSpacing.xs),
          Text(body),
        ],
      ),
    );
  }
}
