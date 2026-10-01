import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/app_theme.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:devpilot_app/core/widgets/copy_text_button.dart';
import 'package:devpilot_app/core/widgets/markdown_text.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/status_badge.dart';
import 'package:devpilot_app/features/today/data/reading_models.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

// The parts of SCR-READ-CODE (docs/02 §3.16): what to read, why, and where — never the code.

/// `RepoHeader`: name, stack and license (or the read-only badge), why, and the repository link
/// the user's own browser opens.
class RepoHeader extends StatelessWidget {
  const RepoHeader({super.key, required this.code, required this.retired});

  final CodeReadingView code;

  /// Retired unit (docs/19 §8.2): still resolvable, no longer proposed.
  final bool retired;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final repo = code.repo;
    final why = repo.why;
    final note = repo.licenseNote;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(repo.name, key: const Key('readCode.repoName'), style: textTheme.titleLarge),
        Text([?repo.stack, if (!repo.readOnly) repo.license].join(' · ')),
        if (repo.readOnly) ...[
          const SizedBox(height: AppSpacing.xs),
          Align(
            alignment: Alignment.centerLeft,
            child: StatusBadge(
              key: const Key('readCode.readOnlyBadge'),
              label: l10n.readCodeReadOnlyBadge,
              tone: AppTone.warning,
              icon: Icons.lock,
            ),
          ),
          Text(note ?? l10n.readCodeReadOnlyNote, key: const Key('readCode.readOnlyNote')),
        ],
        if (retired) Text(l10n.readCodeRetired, key: const Key('readCode.retired')),
        if (why != null && why.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.md),
          SectionTitle(l10n.readCodeWhy),
          Text(why),
        ],
        Align(
          alignment: Alignment.centerLeft,
          child: Semantics(
            link: true,
            child: TextButton.icon(
              key: const Key('readCode.openRepoButton'),
              onPressed: () => launchUrl(Uri.parse(repo.url), webOnlyWindowName: '_blank'),
              icon: const Icon(Icons.open_in_new, size: 16),
              label: Text(l10n.readCodeOpenRepo),
            ),
          ),
        ),
      ],
    );
  }
}

/// ① "내 컴퓨터로 가져오기": `cloneHint` in one monospace line (the only horizontal scroll, A-11)
/// and the pinned commit the line numbers refer to (RC-4).
class CloneStep extends StatelessWidget {
  const CloneStep({super.key, required this.repo});

  final CuratedRepoView repo;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final commit = repo.pinnedCommit;
    return Column(
      key: const Key('readCode.cloneStep'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionTitle(l10n.readCodeStepClone),
        Text(l10n.readCodeNoCodeNote, style: Theme.of(context).textTheme.bodySmall),
        const SizedBox(height: AppSpacing.sm),
        Container(
          key: const Key('readCode.cloneHint'),
          padding: const EdgeInsets.all(AppSpacing.md),
          decoration: BoxDecoration(
            color: DevPilotColors.of(context).codeBackground,
            borderRadius: const BorderRadius.all(Radius.circular(AppRadius.md)),
          ),
          child: SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Text(repo.cloneHint, softWrap: false, style: AppTheme.codeTextStyle),
          ),
        ),
        Align(
          alignment: Alignment.centerRight,
          child: CopyTextButton(
            key: const Key('readCode.cloneCopyButton'),
            value: repo.cloneHint,
            label: l10n.readCodeCopy,
          ),
        ),
        if (commit != null) ...[
          Wrap(
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              Text(l10n.readCodePinnedCommit(commit.substring(0, commit.length.clamp(0, 7)))),
              CopyTextButton(
                key: const Key('readCode.commitCopyButton'),
                value: commit,
                label: l10n.readCodePinnedCommitCopy,
              ),
            ],
          ),
          Text(l10n.readCodePinnedCommitNote, style: Theme.of(context).textTheme.bodySmall),
        ],
      ],
    );
  }
}

/// ② "이 파일을 여세요": the sub-folder note, the path broken at `/`, the line range and time.
class FileStep extends StatelessWidget {
  const FileStep({super.key, required this.code, required this.estimatedMinutes});

  final CodeReadingView code;
  final int? estimatedMinutes;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final subPath = code.repo.subPath;
    final minutes = estimatedMinutes;
    final range = minutes == null
        ? l10n.readCodeRangeOnly(code.startLine, code.endLine)
        : l10n.readCodeRange(code.startLine, code.endLine, formatMinutes(minutes, l10n));
    return Column(
      key: const Key('readCode.fileStep'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionTitle(l10n.readCodeStepOpen),
        if (subPath.isNotEmpty) Text(l10n.readCodeSubPathNote(subPath)),
        const SizedBox(height: AppSpacing.xs),
        Text(
          // A zero-width space after each "/" lets a long path wrap there.
          code.path.replaceAll('/', '/​'),
          key: const Key('readCode.path'),
          style: AppTheme.codeTextStyle,
        ),
        Align(
          alignment: Alignment.centerRight,
          child: CopyTextButton(
            key: const Key('readCode.pathCopyButton'),
            value: code.path,
            label: l10n.readCodeCopyPath,
          ),
        ),
        Text(range, key: const Key('readCode.range')),
      ],
    );
  }
}

/// ③ "읽으면서 생각할 질문" with the points to look for.
class QuestionStep extends StatelessWidget {
  const QuestionStep({super.key, required this.code});

  final CodeReadingView code;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Column(
      key: const Key('readCode.questionStep'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionTitle(l10n.readCodeStepThink),
        MarkdownText(code.question, textKey: const Key('readCode.question')),
        if (code.lookFor.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.sm),
          Text(l10n.readCodeLookFor, style: Theme.of(context).textTheme.titleSmall),
          for (final point in code.lookFor) MarkdownText('- $point'),
        ],
      ],
    );
  }
}
