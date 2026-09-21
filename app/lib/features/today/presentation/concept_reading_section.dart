import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/markdown_text.dart';
import 'package:devpilot_app/features/today/data/reading_models.dart';
import 'package:devpilot_app/features/today/data/reading_repository.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

/// `ConceptReadingSection`: the material block of a `READING` main card (docs/02 SCR-TODAY,
/// docs/06 §5.3, content docs/19 §3.13). It answers "what am I supposed to read?" — before this
/// the card only said "공식 문서를 읽고 핵심 3가지를 스스로 적어 보세요" without naming a document.
///
/// There is no separate screen: the block lives inside the Today card and `GET /readings/{key}`
/// serves it, the same endpoint SCR-READ-CODE uses (docs/05 §19.7). When the lookup fails the
/// block is hidden and the task still shows — its title and description are already in
/// `MainTaskView`. `readingKey == null` (the skill has no concept reading) hides it too, so the
/// card looks exactly as it did before.
class ConceptReadingSection extends ConsumerWidget {
  const ConceptReadingSection({super.key, required this.readingKey});

  final String readingKey;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final concept = ref.watch(readingProvider(readingKey)).value?.concept;
    if (concept == null) {
      return const SizedBox.shrink();
    }
    return _ConceptReadingBlock(concept: concept);
  }
}

class _ConceptReadingBlock extends StatelessWidget {
  const _ConceptReadingBlock({required this.concept});

  final ConceptReadingView concept;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    return Column(
      key: const Key('today.conceptReading'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const SizedBox(height: AppSpacing.md),
        Semantics(
          header: true,
          child: Text(l10n.todayReadingMaterial, style: textTheme.titleSmall),
        ),
        const SizedBox(height: AppSpacing.xs),
        // The document title stays as its publisher writes it: never translated or shortened.
        Text(
          concept.title,
          key: const Key('today.readingTitle'),
          maxLines: 3,
          overflow: TextOverflow.ellipsis,
        ),
        Text(
          '${concept.publisher} · ${concept.versionScope}',
          key: const Key('today.readingSource'),
          style: textTheme.bodySmall,
        ),
        const SizedBox(height: AppSpacing.sm),
        Align(
          alignment: Alignment.centerLeft,
          child: Semantics(
            link: true,
            child: OutlinedButton.icon(
              key: const Key('today.readingOpenButton'),
              // The official document is read on its own site: neither app nor server fetches it.
              onPressed: () => launchUrl(Uri.parse(concept.url), webOnlyWindowName: '_blank'),
              icon: const Icon(Icons.open_in_new, size: 16),
              label: Text(l10n.todayReadingOpen),
            ),
          ),
        ),
        Text(l10n.todayReadingOpenHint, style: textTheme.bodySmall),
        if (concept.checkPoints.isNotEmpty)
          // A list to read, not checkboxes: no answer is sent or stored (docs/02 SCR-TODAY).
          ExpansionTile(
            key: const Key('today.readingCheckPoints'),
            title: Text(l10n.todayReadingCheckPoints, style: textTheme.titleSmall),
            tilePadding: EdgeInsets.zero,
            children: [
              for (final point in concept.checkPoints)
                Padding(
                  padding: const EdgeInsets.only(bottom: AppSpacing.xs),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const ExcludeSemantics(child: Text('•  ')),
                      Expanded(child: MarkdownText(point)),
                    ],
                  ),
                ),
            ],
          ),
      ],
    );
  }
}
