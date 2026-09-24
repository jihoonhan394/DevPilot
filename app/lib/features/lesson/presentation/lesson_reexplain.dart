import 'package:devpilot_app/core/api/error_message_mapper.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/markdown_text.dart';
import 'package:devpilot_app/features/lesson/data/lesson_models.dart';
import 'package:devpilot_app/features/lesson/data/lesson_repository.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// "이 설명이 이해가 안 돼요" (docs/05 §21.10, ADR-047).
///
/// 설명 단계에서 막혔을 때의 유일한 출구다 — 힌트는 문제 풀이용이고 러버덕은 답을 주지 않는다.
/// 누를 때만 AI를 부르고(비용), 결과는 저장되지 않으므로 그 자리에서만 보인다.
class LessonReexplainButton extends ConsumerStatefulWidget {
  const LessonReexplainButton({super.key, required this.lessonKey, required this.unitKey});

  final String lessonKey;
  final String unitKey;

  @override
  ConsumerState<LessonReexplainButton> createState() => _LessonReexplainButtonState();
}

class _LessonReexplainButtonState extends ConsumerState<LessonReexplainButton> {
  var _busy = false;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    // AI가 꺼져 있으면 눌러 봐야 부를 수 없다 (docs/02 §6.2 aiAvailable).
    if (!ref.watch(aiStatusProvider).allowsAi) {
      return const SizedBox.shrink();
    }
    return Align(
      alignment: Alignment.centerLeft,
      child: TextButton.icon(
        key: const Key('lesson.reexplainButton'),
        onPressed: _busy ? null : _start,
        icon: _busy
            ? SizedBox.square(
                dimension: 16,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  semanticsLabel: l10n.lessonReexplaining,
                ),
              )
            : const Icon(Icons.help_outline, size: 18),
        label: Text(_busy ? l10n.lessonReexplaining : l10n.lessonReexplain),
      ),
    );
  }

  Future<void> _start() async {
    final reason = await _askReason();
    if (reason == null || !mounted) {
      return;
    }
    setState(() => _busy = true);
    try {
      final result = await ref
          .read(lessonRepositoryProvider)
          .reexplain(widget.lessonKey, widget.unitKey, reason);
      if (!mounted) {
        return;
      }
      // 시트를 열기 전에 진행 표시를 끈다 — 시트가 열려 있는 동안 돌고 있을 이유가 없다.
      setState(() => _busy = false);
      await _show(result);
    } on Object catch (error) {
      if (mounted) {
        setState(() => _busy = false);
        showToast(context, messageFor(error, AppLocalizations.of(context)));
      }
    }
  }

  /// 자유 입력을 받지 않는다 — 고정 선택지 셋이면 방향을 잡기에 충분하다(ADR-047).
  Future<ConfusionReason?> _askReason() {
    final l10n = AppLocalizations.of(context);
    return showModalBottomSheet<ConfusionReason>(
      context: context,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.all(AppSpacing.lg),
              child: Semantics(
                header: true,
                child: Text(
                  l10n.lessonReexplainAsk,
                  style: Theme.of(sheetContext).textTheme.titleMedium,
                ),
              ),
            ),
            for (final (reason, label) in <(ConfusionReason, String)>[
              (ConfusionReason.unfamiliarTerms, l10n.lessonReexplainTerms),
              (ConfusionReason.whyNotClear, l10n.lessonReexplainWhy),
              (ConfusionReason.exampleUnclear, l10n.lessonReexplainExample),
            ])
              ListTile(
                key: Key('lesson.reexplainReason.${reason.name}'),
                title: Text(label),
                onTap: () => Navigator.of(sheetContext).pop(reason),
              ),
            const SizedBox(height: AppSpacing.lg),
          ],
        ),
      ),
    );
  }

  Future<void> _show(ReexplainResult result) {
    final l10n = AppLocalizations.of(context);
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) => SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Semantics(
                header: true,
                child: Text(
                  l10n.lessonReexplainTitle,
                  key: const Key('lesson.reexplainTitle'),
                  style: Theme.of(sheetContext).textTheme.titleMedium,
                ),
              ),
              const SizedBox(height: AppSpacing.md),
              MarkdownText(result.explanation, textKey: const Key('lesson.reexplainText')),
              if (result.analogy case final analogy? when analogy.trim().isNotEmpty) ...[
                const Divider(height: AppSpacing.xl),
                Text(
                  l10n.lessonReexplainAnalogy,
                  style: Theme.of(sheetContext).textTheme.titleSmall,
                ),
                const SizedBox(height: AppSpacing.xs),
                MarkdownText(analogy),
              ],
              const SizedBox(height: AppSpacing.md),
              // 매번 달라지므로 본문을 대체하지 않는다는 것을 알려 둔다.
              Text(
                l10n.lessonReexplainNote,
                style: Theme.of(sheetContext).textTheme.bodySmall,
              ),
              const SizedBox(height: AppSpacing.lg),
              FilledButton(
                key: const Key('lesson.reexplainClose'),
                onPressed: () => Navigator.of(sheetContext).pop(),
                child: Text(l10n.commonClose),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
