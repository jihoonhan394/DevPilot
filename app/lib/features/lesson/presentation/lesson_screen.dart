import 'package:devpilot_app/app/explain_with_duck_button.dart';
import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/markdown_text.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/lesson/data/lesson_models.dart';
import 'package:devpilot_app/features/lesson/data/lesson_repository.dart';
import 'package:devpilot_app/features/lesson/presentation/lesson_controller.dart';
import 'package:devpilot_app/features/lesson/presentation/lesson_step_views.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// SCR-LESSON (docs/02): 가르치는 화면이다.
///
/// 노트 하나는 학습 단위 3~6개이고, 단위 하나가 5~15분짜리 한 바퀴다. 화면은 그 한 바퀴를 **한 걸음씩** 보여 준다 —
/// 한 화면에 다 펼치면 답을 먼저 보게 되고, 평일 60분에 어디까지 했는지도 흐려진다.
class LessonScreen extends ConsumerStatefulWidget {
  const LessonScreen({super.key, required this.lessonKey});

  final String lessonKey;

  @override
  ConsumerState<LessonScreen> createState() => _LessonScreenState();
}

class _LessonScreenState extends ConsumerState<LessonScreen> {
  String? _unitKey;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final lesson = ref.watch(lessonProvider(widget.lessonKey));
    return Scaffold(
      appBar: AppBar(
        title: Semantics(header: true, child: Text(l10n.lessonTitle)),
      ),
      body: lesson.when(
        loading: () => const ScreenBody(child: SkeletonList(count: 2, lines: 4)),
        error: (error, _) => ScreenBody(
          child: ErrorView(error: error, onRetry: _reload),
        ),
        data: _body,
      ),
    );
  }

  void _reload() => ref.invalidate(lessonProvider(widget.lessonKey));

  Widget _body(LessonView lesson) {
    final unitKey = _unitKey ?? (lesson.nextUnit ?? lesson.units.first).unitKey;
    final unit = lesson.units.firstWhere(
      (candidate) => candidate.unitKey == unitKey,
      orElse: () => lesson.units.first,
    );
    final target = (lessonKey: lesson.lessonKey, unitKey: unit.unitKey);
    final walk = ref.watch(unitWalkProvider(target));
    final controller = ref.read(unitWalkProvider(target).notifier);
    return ScreenBody(
      key: Key('lesson.step.${walk.step.name}'),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _UnitBar(lesson: lesson, unit: unit),
          const SizedBox(height: AppSpacing.md),
          LessonStepView(
            lesson: lesson,
            unit: unit,
            state: walk,
            controller: controller,
            onOpenUnit: (next) => setState(() => _unitKey = next),
          ),
        ],
      ),
    );
  }
}

/// 어느 단위의 몇 번째 걸음인지. 하루가 끊겨도 여기서 이어 간다.
class _UnitBar extends StatelessWidget {
  const _UnitBar({required this.lesson, required this.unit});

  final LessonView lesson;
  final LessonUnitView unit;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final index = lesson.units.indexWhere((item) => item.unitKey == unit.unitKey) + 1;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          '${lesson.title} · $index/${lesson.units.length}',
          key: const Key('lesson.unitBar'),
          style: theme.textTheme.labelLarge,
        ),
        const SizedBox(height: AppSpacing.xs),
        Semantics(
          header: true,
          child: Text(unit.title, style: theme.textTheme.titleLarge),
        ),
        Text(
          l10n.lessonUnitMinutes(unit.minutes),
          style: theme.textTheme.bodySmall,
        ),
      ],
    );
  }
}

/// 노트의 왜 배우나 + 단위 목록 (걸음 0).
class LessonOverview extends StatelessWidget {
  const LessonOverview({super.key, required this.lesson, required this.onStart, this.onOpenUnit});

  final LessonView lesson;
  final VoidCallback onStart;
  final ValueChanged<String>? onOpenUnit;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionTitle(l10n.lessonWhyTitle),
        MarkdownText(lesson.whyItMatters, textKey: const Key('lesson.whyItMatters')),
        const SizedBox(height: AppSpacing.sm),
        MarkdownText(lesson.oneLine, style: theme.textTheme.titleSmall),
        const SizedBox(height: AppSpacing.lg),
        SectionTitle(l10n.lessonUnitsHeading),
        for (final unit in lesson.units)
          ListTile(
            key: Key('lesson.unit.${unit.unitKey}'),
            contentPadding: EdgeInsets.zero,
            title: Text(unit.title),
            subtitle: Text(l10n.lessonUnitMinutes(unit.minutes)),
            trailing: unit.progress == null ? null : Text(l10n.lessonUnitDone),
            onTap: onOpenUnit == null ? null : () => onOpenUnit!(unit.unitKey),
          ),
        const SizedBox(height: AppSpacing.lg),
        FilledButton(
          key: const Key('lesson.startButton'),
          onPressed: onStart,
          child: Text(l10n.lessonStart),
        ),
      ],
    );
  }
}

/// 노트 끝에 붙는 것: 자주 하는 실수와 근거 문서.
class LessonFooter extends StatelessWidget {
  const LessonFooter({super.key, required this.lesson});

  final LessonView lesson;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (lesson.commonMistakes.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.lg),
          SectionTitle(l10n.lessonCommonMistakes),
          for (final mistake in lesson.commonMistakes) MarkdownText('- $mistake'),
        ],
        if (lesson.sources.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.lg),
          SectionTitle(l10n.lessonSources),
          for (final source in lesson.sources) _SourceLink(source: source),
        ],
        if (lesson.readMore.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.lg),
          SectionTitle(l10n.lessonReadMore),
          for (final source in lesson.readMore) _SourceLink(source: source),
        ],
      ],
    );
  }
}

class _SourceLink extends StatelessWidget {
  const _SourceLink({required this.source});

  final LessonSourceView source;

  @override
  Widget build(BuildContext context) {
    final version = source.versionScope;
    // 링크는 MarkdownText가 새 탭으로 연다. 앱도 서버도 그 페이지를 불러오지 않는다.
    return MarkdownText(
      '- [${source.title}](${source.url})${version == null ? '' : ' · $version'}',
    );
  }
}

/// 러버덕으로 보내는 버튼. 도움 단계가 `DUCK`이 된다.
class LessonDuckButton extends StatelessWidget {
  const LessonDuckButton({super.key, required this.lesson, required this.onAsked});

  final LessonView lesson;
  final VoidCallback onAsked;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return ExplainWithDuckButton(
      buttonKey: const Key('lesson.duckButton'),
      label: l10n.lessonAskDuck,
      style: ExplainButtonStyle.outlined,
      beforeOpen: onAsked,
      launch: RubberDuckLaunch(
        targetType: RubberDuckTargetType.concept,
        conceptKey: lesson.skillCode,
        skillCode: lesson.skillCode,
        preview: RubberDuckTargetPreview(title: lesson.title),
      ),
    );
  }
}
