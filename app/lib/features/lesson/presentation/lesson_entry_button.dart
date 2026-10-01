import 'package:devpilot_app/app/explain_with_duck_button.dart' show ExplainButtonStyle;
import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/features/lesson/data/lesson_repository.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// 그 skill의 개념 노트로 가는 버튼 (docs/05 §21.3, docs/01 §4 Teach before test).
///
/// 막혔을 때 화면이 들고 있는 것은 skill이지 노트 key가 아니다. 노트가 없는 skill이면 **아무것도 그리지 않는다** — 누를 곳이 있는데 404가
/// 나는 것보다 없는 편이 낫다. AI를 쓰지 않으므로 예산 상태와 무관하게 언제나 누를 수 있다.
class LessonEntryButton extends ConsumerWidget {
  const LessonEntryButton({
    super.key,
    required this.buttonKey,
    required this.skillId,
    required this.label,
    this.style = ExplainButtonStyle.text,
  });

  final Key buttonKey;

  /// skill이 없는 과제도 있다. 그러면 노트도 없다.
  final String? skillId;

  final String label;
  final ExplainButtonStyle style;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final skill = skillId;
    if (skill == null) {
      return const SizedBox.shrink();
    }
    final note = ref.watch(skillLessonProvider(skill)).value;
    if (note == null) {
      return const SizedBox.shrink();
    }
    void open() => context.go(AppRoutes.lesson(note.lessonKey));
    final text = Text(label);
    return switch (style) {
      ExplainButtonStyle.filled => FilledButton(key: buttonKey, onPressed: open, child: text),
      ExplainButtonStyle.outlined => OutlinedButton(key: buttonKey, onPressed: open, child: text),
      ExplainButtonStyle.text => TextButton(key: buttonKey, onPressed: open, child: text),
    };
  }
}
