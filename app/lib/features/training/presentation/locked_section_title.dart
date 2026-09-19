import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// "② 힌트 / ③ 답 제출 — 설명 후에 열려요": a section that waits for the self-explanation.
class LockedSectionTitle extends StatelessWidget {
  const LockedSectionTitle({super.key, required this.title});

  final String title;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(child: SectionTitle(title)),
        const Icon(Icons.lock_outline, size: 18),
        const SizedBox(width: AppSpacing.xs),
        Text(AppLocalizations.of(context).trainingLocked),
      ],
    );
  }
}
