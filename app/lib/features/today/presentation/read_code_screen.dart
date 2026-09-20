import 'dart:async';

import 'package:devpilot_app/app/explain_with_duck_button.dart';
import 'package:devpilot_app/app/not_found_screen.dart';
import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/ai_status_widgets.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/features/today/data/reading_models.dart';
import 'package:devpilot_app/features/today/data/reading_repository.dart';
import 'package:devpilot_app/features/today/presentation/read_code_sections.dart';
import 'package:devpilot_app/features/today/presentation/today_actions.dart';
import 'package:devpilot_app/features/today/presentation/today_controller.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-READ-CODE: which open-source file and lines to read in the user's own IDE, why, and what
/// to think about; then "읽었으면 설명하기" (docs/02 §3.16, FR-27, AC-28). The screen never shows
/// code: the server does not fetch the repository.
class ReadCodeScreen extends ConsumerWidget {
  const ReadCodeScreen({super.key, required this.readingKey, this.taskId});

  final String readingKey;

  /// `?taskId=`: the Today READ_CODE task. Without it the guide shows but explaining and
  /// recording are hidden.
  final String? taskId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final reading = ref.watch(readingProvider(readingKey));
    final error = reading.error;
    if (error is ApiException &&
        (error.code == ApiErrorCode.resourceNotFound ||
            error.code == ApiErrorCode.validationFailed)) {
      return const NotFoundScreen();
    }
    // A concept reading (`kind = CONCEPT`) has no file to open: it is shown inside the Today
    // READING card instead, so this screen is not its destination (docs/02 SCR-TODAY).
    if (reading.hasValue && reading.requireValue.code == null) {
      return const NotFoundScreen();
    }
    return Scaffold(
      appBar: AppBar(title: Semantics(header: true, child: Text(l10n.readCodeTitle))),
      body: Column(
        children: [
          AiUnavailableBanner(status: ref.watch(aiStatusProvider)),
          Expanded(
            child: ScreenBody(
              child: reading.when(
                loading: () => const SkeletonList(count: 3, lines: 3),
                error: (error, _) => ErrorView(
                  error: error,
                  onRetry: () => ref.invalidate(readingProvider(readingKey)),
                ),
                data: (reading) =>
                    _ReadingGuide(reading: reading, code: reading.code!, taskId: taskId),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ReadingGuide extends StatelessWidget {
  const _ReadingGuide({required this.reading, required this.code, required this.taskId});

  final ReadingView reading;
  final CodeReadingView code;
  final String? taskId;

  @override
  Widget build(BuildContext context) {
    final task = taskId;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        RepoHeader(code: code, retired: reading.retired),
        const Divider(height: AppSpacing.xxl),
        CloneStep(repo: code.repo),
        const Divider(height: AppSpacing.xxl),
        FileStep(code: code, estimatedMinutes: reading.estimatedMinutes),
        const Divider(height: AppSpacing.xxl),
        QuestionStep(code: code),
        const SizedBox(height: AppSpacing.xl),
        if (task == null)
          Text(AppLocalizations.of(context).readCodeNoTask, key: const Key('readCode.noTask'))
        else
          _ReadingActions(reading: reading, code: code, taskId: task),
      ],
    );
  }
}

/// "읽었으면 설명하기" (the rubber duck on this task, blocked with its reason while the AI is off)
/// and "여기까지 기록" (the partial record of the running session → DEFERRED, no RC-1 check).
class _ReadingActions extends ConsumerWidget {
  const _ReadingActions({required this.reading, required this.code, required this.taskId});

  final ReadingView reading;
  final CodeReadingView code;
  final String taskId;

  Future<void> _recordPartially(BuildContext context, WidgetRef ref) async {
    await openCompleteSheet(context, ref, partial: true);
    final main = ref.read(todayControllerProvider).value?.mainTask;
    if (context.mounted && main?.id == taskId && main?.status == TaskStatus.deferred) {
      context.go(AppRoutes.today);
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final today = ref.watch(todayControllerProvider).value;
    final main = today?.mainTask;
    final canRecord =
        main != null &&
        main.id == taskId &&
        main.status == TaskStatus.inProgress &&
        today?.mainSession != null;
    final aiStatus = ref.watch(aiStatusProvider);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        FilledButton(
          key: const Key('readCode.explainButton'),
          onPressed: aiStatus.allowsAi ? () => openRubberDuck(context, _launch(l10n)) : null,
          child: Text(l10n.readCodeExplain),
        ),
        AiActionNote(status: aiStatus, extraReason: l10n.readCodeAiOff),
        Text(l10n.readCodeExplainNote, style: Theme.of(context).textTheme.bodySmall),
        if (canRecord)
          TextButton(
            key: const Key('readCode.partialButton'),
            onPressed: () => unawaited(_recordPartially(context, ref)),
            child: Text(l10n.readCodePartial),
          ),
      ],
    );
  }

  RubberDuckLaunch _launch(AppLocalizations l10n) => RubberDuckLaunch(
    targetType: RubberDuckTargetType.codeReading,
    targetId: taskId,
    skillCode: reading.skills.firstOrNull?.code,
    taskId: taskId,
    preview: RubberDuckTargetPreview(
      title: l10n.readCodeDuckTitle(
        code.repo.name,
        code.fileName,
        code.startLine,
        code.endLine,
      ),
      summary: code.question,
    ),
  );
}
