import 'package:devpilot_app/app/explain_with_duck_button.dart';
import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
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
        // 몇 번째 단위인지 글자로만 있으면 진도가 보이지 않는다 (docs/02 SCR-LESSON).
        ClipRRect(
          borderRadius: BorderRadius.circular(AppRadius.sm),
          child: LinearProgressIndicator(
            value: lesson.units.isEmpty ? 0 : index / lesson.units.length,
            minHeight: 6,
            backgroundColor: theme.colorScheme.surfaceContainerHighest,
          ),
        ),
        const SizedBox(height: AppSpacing.sm),
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
    final done = lesson.units.where((unit) => unit.progress != null).length;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // 글만 이어지면 어디가 무엇인지 읽히지 않는다. 이 노트를 왜 읽는지를 색 있는 상자로 먼저 세운다.
        _WhyBox(lesson: lesson),
        const SizedBox(height: AppSpacing.lg),
        SectionTitle(l10n.lessonUnitsHeading),
        const SizedBox(height: AppSpacing.xs),
        _UnitProgress(done: done, total: lesson.units.length),
        const SizedBox(height: AppSpacing.sm),
        for (final unit in lesson.units) ...[
          Card(
            child: ListTile(
              key: Key('lesson.unit.${unit.unitKey}'),
              title: Text(unit.title),
              subtitle: Text(l10n.lessonUnitMinutes(unit.minutes)),
              trailing: unit.progress == null
                  ? const Icon(Icons.chevron_right)
                  : Icon(Icons.check_circle, color: DevPilotColors.of(context).success),
              onTap: onOpenUnit == null ? null : () => onOpenUnit!(unit.unitKey),
            ),
          ),
          const SizedBox(height: AppSpacing.xs),
        ],
        const SizedBox(height: AppSpacing.md),
        FilledButton(
          key: const Key('lesson.startButton'),
          onPressed: onStart,
          child: Text(l10n.lessonStart),
        ),
      ],
    );
  }
}

/// 이 노트를 왜 읽는지. 페이지 맨 위의 **시각적 기준점**이다 — 흰 바탕에 글만 이어지면 처음 여는 사람은 어디부터
/// 읽을지 정하지 못한다. 색은 테마 토큰만 쓴다(docs/02 §10.2, A-2 대비 기준).
class _WhyBox extends StatelessWidget {
  const _WhyBox({required this.lesson});

  final LessonView lesson;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: scheme.primaryContainer,
        borderRadius: BorderRadius.circular(AppRadius.lg),
      ),
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              l10n.lessonWhyTitle,
              style: theme.textTheme.labelLarge?.copyWith(color: scheme.onPrimaryContainer),
            ),
            const SizedBox(height: AppSpacing.xs),
            MarkdownText(
              lesson.whyItMatters,
              textKey: const Key('lesson.whyItMatters'),
              style: theme.textTheme.bodyMedium?.copyWith(color: scheme.onPrimaryContainer),
            ),
            const SizedBox(height: AppSpacing.sm),
            MarkdownText(
              lesson.oneLine,
              style: theme.textTheme.titleSmall?.copyWith(color: scheme.onPrimaryContainer),
            ),
            // "이걸 외워야 하나, 이 정도면 프로젝트를 만들어도 되나"에 답한다 (ADR-069). 계획이 들고 있던
            // 값인데 이 화면에 없었다 — 읽는 사람은 기준을 알 수 없었다.
            if (lesson.requirement case final requirement?)
              _Requirement(requirement: requirement, color: scheme.onPrimaryContainer),
          ],
        ),
      ),
    );
  }
}

/// "이 기술은 필수이고, 목표는 지식 3(혼자 기본 가능)이에요. 지금은 전혀 모름이에요." (ADR-069)
///
/// 노트 본문이 주제가 아닌 어휘를 쓸 때(상태 코드 같은) 읽는 사람이 묻는 것은 "이걸 외워야 하나"다. 그 답은
/// 계획에 있다 — 우선순위와 축별 목표. 지식 축만 쓴다: 노트는 가르치는 단계이고 §7.2에서 노트가 올리는 축이
/// 지식이다. 구현·설명 목표는 문제와 러버덕이 맡는다.
class _Requirement extends StatelessWidget {
  const _Requirement({required this.requirement, required this.color});

  final LessonRequirementView requirement;
  final Color color;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final priority = requirement.priority;
    if (priority == null || priority == Priority.unknown) {
      return const SizedBox.shrink();
    }
    final target = requirement.targets.knowledge;
    final now = requirement.planningLevels.knowledge;
    final text = now >= target
        ? l10n.lessonRequirementMet(priority.label(l10n), skillLevelLabel(target, l10n))
        : l10n.lessonRequirement(
            priority.label(l10n),
            skillLevelLabel(target, l10n),
            skillLevelLabel(now, l10n),
          );
    return Padding(
      padding: const EdgeInsets.only(top: AppSpacing.sm),
      child: Text(
        text,
        key: const Key('lesson.requirement'),
        style: theme.textTheme.bodySmall?.copyWith(color: color),
      ),
    );
  }
}

/// 단위 몇 개를 마쳤는지. 숫자만 있으면 진도가 보이지 않는다.
class _UnitProgress extends StatelessWidget {
  const _UnitProgress({required this.done, required this.total});

  final int done;
  final int total;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          l10n.lessonUnitProgress(done, total),
          key: const Key('lesson.unitProgress'),
          style: theme.textTheme.bodySmall,
        ),
        const SizedBox(height: AppSpacing.xs),
        ClipRRect(
          borderRadius: BorderRadius.circular(AppRadius.sm),
          child: LinearProgressIndicator(
            value: total == 0 ? 0 : done / total,
            minHeight: 6,
            backgroundColor: theme.colorScheme.surfaceContainerHighest,
          ),
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
