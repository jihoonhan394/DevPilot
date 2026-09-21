import 'package:devpilot_app/core/api/error_message_mapper.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/code_text_field.dart';
import 'package:devpilot_app/core/widgets/inline_error.dart';
import 'package:devpilot_app/core/widgets/markdown_text.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/features/lesson/data/lesson_models.dart';
import 'package:devpilot_app/features/lesson/presentation/lesson_controller.dart';
import 'package:devpilot_app/features/lesson/presentation/lesson_screen.dart';
import 'package:devpilot_app/features/lesson/presentation/lesson_step.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// 걸음 하나의 본문 (docs/02 SCR-LESSON). 한 걸음씩 보여 준다.
class LessonStepView extends StatefulWidget {
  const LessonStepView({
    super.key,
    required this.lesson,
    required this.unit,
    required this.state,
    required this.controller,
    required this.onOpenUnit,
  });

  final LessonView lesson;
  final LessonUnitView unit;
  final UnitWalkState state;
  final UnitWalkController controller;
  final ValueChanged<String> onOpenUnit;

  @override
  State<LessonStepView> createState() => _LessonStepViewState();
}

class _LessonStepViewState extends State<LessonStepView> {
  final _predict = TextEditingController();
  final _answer = TextEditingController();
  List<TextEditingController> _blanks = const [];

  @override
  void initState() {
    super.initState();
    _resetBlanks();
    final starter = widget.unit.problem.starterCode;
    if (starter != null) {
      _answer.text = starter;
    }
  }

  @override
  void didUpdateWidget(LessonStepView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.unit.unitKey != widget.unit.unitKey) {
      _predict.clear();
      _answer.text = widget.unit.problem.starterCode ?? '';
      _resetBlanks();
    }
  }

  void _resetBlanks() {
    for (final controller in _blanks) {
      controller.dispose();
    }
    _blanks = List.generate(widget.unit.complete.blanks, (_) => TextEditingController());
  }

  @override
  void dispose() {
    _predict.dispose();
    _answer.dispose();
    for (final controller in _blanks) {
      controller.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final failure = widget.state.failure;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (failure != null) ...[
          InlineError(message: messageFor(failure, AppLocalizations.of(context))),
          const SizedBox(height: AppSpacing.md),
        ],
        _step(context),
      ],
    );
  }

  Widget _step(BuildContext context) => switch (widget.state.step) {
    LessonStep.why => LessonOverview(
      lesson: widget.lesson,
      onStart: widget.controller.next,
      onOpenUnit: widget.onOpenUnit,
    ),
    LessonStep.explain => _Explain(view: widget),
    LessonStep.example => _Example(view: widget),
    LessonStep.predict => _Predict(view: widget, field: _predict),
    LessonStep.complete => _Complete(view: widget, fields: _blanks),
    LessonStep.problem => _Problem(view: widget, answer: _answer),
    LessonStep.compare => _Compare(view: widget, answer: _answer),
    LessonStep.next => _Next(view: widget),
  };
}

/// 걸음 1: 설명.
class _Explain extends StatelessWidget {
  const _Explain({required this.view});

  final LessonStepView view;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        MarkdownText(view.unit.explain, textKey: const Key('lesson.explain')),
        const SizedBox(height: AppSpacing.lg),
        FilledButton(
          key: const Key('lesson.nextButton'),
          onPressed: view.controller.next,
          child: Text(l10n.lessonSeeExample),
        ),
        _SkipToProblem(view: view),
      ],
    );
  }
}

/// 걸음 2: 예제.
class _Example extends StatelessWidget {
  const _Example({required this.view});

  final LessonStepView view;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final example = view.unit.example;
    final output = example.output;
    final note = example.note;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        CodeBlock(code: example.code, language: example.language),
        if (output != null && output.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.md),
          SectionTitle(l10n.lessonExampleOutput),
          CodeBlock(code: output),
        ],
        if (note != null && note.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.md),
          MarkdownText(note),
        ],
        const SizedBox(height: AppSpacing.lg),
        FilledButton(
          key: const Key('lesson.nextButton'),
          onPressed: view.controller.next,
          child: Text(l10n.lessonTryIt),
        ),
        _SkipToProblem(view: view),
      ],
    );
  }
}

/// 걸음 3: 출력 예측. 내면 바로 채점한다(AI 없음).
class _Predict extends StatelessWidget {
  const _Predict({required this.view, required this.field});

  final LessonStepView view;
  final TextEditingController field;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final question = view.unit.predict;
    final state = view.state;
    final code = question.code;
    final result = state.predictResult;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionTitle(l10n.lessonPredictTitle),
        MarkdownText(question.question, textKey: const Key('lesson.predictQuestion')),
        if (code != null && code.isNotEmpty)
          CodeBlock(code: code, language: view.unit.example.language),
        const SizedBox(height: AppSpacing.md),
        if (question.multipleChoice)
          RadioGroup<String>(
            groupValue: state.predictAnswer,
            onChanged: (value) {
              if (result == null) {
                view.controller.chooseCandidate(value ?? '');
              }
            },
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                for (var index = 0; index < question.choices.length; index++)
                  RadioListTile<String>(
                    key: Key('lesson.choice.$index'),
                    contentPadding: EdgeInsets.zero,
                    title: Text(question.choices[index]),
                    value: question.choices[index],
                  ),
              ],
            ),
          )
        else
          TextField(
            key: const Key('lesson.predictField'),
            controller: field,
            enabled: result == null,
            decoration: const InputDecoration(border: OutlineInputBorder()),
          ),
        const SizedBox(height: AppSpacing.md),
        if (result == null)
          FilledButton(
            key: const Key('lesson.checkButton'),
            onPressed: state.busy ? null : () => _check(question),
            child: Text(l10n.lessonCheck),
          )
        else ...[
          _Verdict(correct: result.correct, expected: result.expected),
          MarkdownText(result.explanation, textKey: const Key('lesson.predictExplanation')),
          const SizedBox(height: AppSpacing.md),
          FilledButton(
            key: const Key('lesson.nextButton'),
            onPressed: view.controller.next,
            child: Text(l10n.lessonCompleteTitle),
          ),
        ],
      ],
    );
  }

  void _check(LessonQuestionView question) {
    final answer = question.multipleChoice ? (view.state.predictAnswer ?? '') : field.text;
    if (answer.trim().isEmpty) {
      return;
    }
    view.controller.checkPredict(answer);
  }
}

/// 걸음 4: 빈칸 채우기.
class _Complete extends StatelessWidget {
  const _Complete({required this.view, required this.fields});

  final LessonStepView view;
  final List<TextEditingController> fields;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final question = view.unit.complete;
    final result = view.state.completeResult;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionTitle(l10n.lessonCompleteTitle),
        MarkdownText(question.question, textKey: const Key('lesson.completeQuestion')),
        CodeBlock(code: question.code ?? '', language: view.unit.example.language),
        const SizedBox(height: AppSpacing.md),
        for (var index = 0; index < fields.length; index++) ...[
          TextField(
            key: Key('lesson.blank.$index'),
            controller: fields[index],
            enabled: result == null,
            decoration: InputDecoration(
              labelText: l10n.lessonBlankLabel(index + 1),
              border: const OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: AppSpacing.sm),
        ],
        if (result == null)
          FilledButton(
            key: const Key('lesson.checkButton'),
            onPressed: view.state.busy
                ? null
                : () => view.controller.checkComplete([
                    for (final field in fields) field.text,
                  ]),
            child: Text(l10n.lessonCheck),
          )
        else ...[
          _Verdict(correct: result.correct, expected: result.expected.join(', ')),
          MarkdownText(result.explanation, textKey: const Key('lesson.completeExplanation')),
          const SizedBox(height: AppSpacing.md),
          FilledButton(
            key: const Key('lesson.nextButton'),
            onPressed: view.controller.next,
            child: Text(l10n.lessonProblemTitle),
          ),
        ],
      ],
    );
  }
}

/// 걸음 5: 백지 문제. 막히면 힌트 → 러버덕 → 모범 답안 순서로 연다.
class _Problem extends StatelessWidget {
  const _Problem({required this.view, required this.answer});

  final LessonStepView view;
  final TextEditingController answer;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final problem = view.unit.problem;
    final state = view.state;
    final opened = state.hintsOpened;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionTitle(l10n.lessonProblemTitle),
        MarkdownText(problem.prompt, textKey: const Key('lesson.problemPrompt')),
        if (problem.deliverables.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.md),
          SectionTitle(l10n.lessonDeliverables),
          for (var index = 0; index < problem.deliverables.length; index++)
            MarkdownText('${index + 1}. ${problem.deliverables[index]}'),
        ],
        const SizedBox(height: AppSpacing.md),
        CodeTextField(
          key: const Key('lesson.answerField'),
          controller: answer,
          label: l10n.lessonAnswerLabel,
          helperText: l10n.lessonAnswerHint,
        ),
        const SizedBox(height: AppSpacing.lg),
        if (view.unit.prerequisiteUnits.isNotEmpty) ...[
          SectionTitle(l10n.lessonPrerequisite),
          for (final key in view.unit.prerequisiteUnits)
            _PrerequisiteLink(view: view, unitKey: key),
          const SizedBox(height: AppSpacing.md),
        ],
        Text(l10n.lessonStuckHint, style: Theme.of(context).textTheme.bodySmall),
        const SizedBox(height: AppSpacing.sm),
        for (var index = 0; index < opened && index < problem.hints.length; index++) ...[
          SectionTitle(l10n.lessonHintNumber(index + 1)),
          MarkdownText(problem.hints[index], textKey: Key('lesson.hint.$index')),
        ],
        if (opened < problem.hints.length)
          OutlinedButton(
            key: const Key('lesson.hintButton'),
            onPressed: view.controller.openHint,
            child: Text(l10n.lessonShowHint),
          ),
        if (opened >= problem.hints.length) ...[
          LessonDuckButton(lesson: view.lesson, onAsked: view.controller.askedTheDuck),
          FilledButton(
            key: const Key('lesson.revealButton'),
            onPressed: state.busy ? null : view.controller.revealAnswer,
            child: Text(l10n.lessonShowAnswer),
          ),
        ],
      ],
    );
  }
}

/// 걸음 6: 모범 답안과 견주기. 체크는 채점이 아니다 — 레벨을 올리지 않는다.
class _Compare extends StatelessWidget {
  const _Compare({required this.view, required this.answer});

  final LessonStepView view;
  final TextEditingController answer;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final state = view.state;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionTitle(l10n.lessonModelAnswer),
        MarkdownText(state.modelAnswer ?? '', textKey: const Key('lesson.modelAnswer')),
        if (answer.text.trim().isNotEmpty) ...[
          const SizedBox(height: AppSpacing.md),
          SectionTitle(l10n.lessonMyAnswer),
          CodeBlock(code: answer.text),
        ],
        const SizedBox(height: AppSpacing.md),
        SectionTitle(l10n.lessonSelfChecks),
        for (var index = 0; index < state.selfChecks.length; index++)
          CheckboxListTile(
            key: Key('lesson.selfCheck.$index'),
            contentPadding: EdgeInsets.zero,
            title: Text(state.selfChecks[index]),
            value: index < state.selfChecksMet.length && state.selfChecksMet[index],
            onChanged: (met) => view.controller.toggleSelfCheck(index, met ?? false),
          ),
        const SizedBox(height: AppSpacing.lg),
        FilledButton(
          key: const Key('lesson.finishButton'),
          onPressed: state.busy ? null : view.controller.finish,
          child: Text(l10n.lessonFinish),
        ),
      ],
    );
  }
}

/// 걸음 7: 내 프로젝트에 쓰기 + 다음 단위.
class _Next extends StatelessWidget {
  const _Next({required this.view});

  final LessonStepView view;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final next = view.lesson.units
        .where((unit) => unit.unitKey != view.unit.unitKey && unit.progress == null)
        .firstOrNull;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionTitle(l10n.lessonInProject),
        MarkdownText(view.lesson.inProject, textKey: const Key('lesson.inProject')),
        const SizedBox(height: AppSpacing.lg),
        if (next != null)
          FilledButton(
            key: const Key('lesson.nextUnitButton'),
            onPressed: () => view.onOpenUnit(next.unitKey),
            child: Text(l10n.lessonNextUnit(next.title)),
          )
        else
          Text(l10n.lessonAllDone, key: const Key('lesson.allDone')),
        LessonFooter(lesson: view.lesson),
      ],
    );
  }
}

/// 즉시 채점 결과. 스크린 리더가 바로 읽도록 live region이다(A-6).
class _Verdict extends StatelessWidget {
  const _Verdict({required this.correct, required this.expected});

  final bool correct;
  final String expected;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Semantics(
      liveRegion: true,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            correct ? l10n.lessonCorrect : l10n.lessonWrong,
            key: const Key('lesson.verdict'),
            style: Theme.of(context).textTheme.titleSmall,
          ),
          if (!correct) Text(l10n.lessonExpected(expected)),
        ],
      ),
    );
  }
}

/// "이미 안다 → 문제부터". 도움으로 치지 않는다.
class _SkipToProblem extends StatelessWidget {
  const _SkipToProblem({required this.view});

  final LessonStepView view;

  @override
  Widget build(BuildContext context) {
    if (!view.state.step.canSkipToProblem) {
      return const SizedBox.shrink();
    }
    return TextButton(
      key: const Key('lesson.skipButton'),
      onPressed: view.controller.skipToProblem,
      child: Text(AppLocalizations.of(context).lessonSkipToProblem),
    );
  }
}

/// 막혔을 때 먼저 볼 단위. 아직 만들지 않은 단위는 보이지 않는다.
class _PrerequisiteLink extends StatelessWidget {
  const _PrerequisiteLink({required this.view, required this.unitKey});

  final LessonStepView view;
  final String unitKey;

  @override
  Widget build(BuildContext context) {
    final unit = view.lesson.units.where((item) => item.unitKey == unitKey).firstOrNull;
    if (unit == null) {
      return const SizedBox.shrink();
    }
    return TextButton(
      key: Key('lesson.prerequisite.$unitKey'),
      onPressed: () => view.onOpenUnit(unitKey),
      child: Text(unit.title),
    );
  }
}
