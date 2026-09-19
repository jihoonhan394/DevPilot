import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:flutter/material.dart';

/// Inline error below a field or section: icon plus text, never color alone (docs/02 A-3), and
/// announced as a live region.
class InlineError extends StatelessWidget {
  const InlineError({super.key, required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(top: AppSpacing.xs),
      child: Semantics(
        liveRegion: true,
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(Icons.error_outline, size: 16, color: colorScheme.error),
            const SizedBox(width: AppSpacing.xs),
            Expanded(
              child: Text(message, style: TextStyle(color: colorScheme.error)),
            ),
          ],
        ),
      ),
    );
  }
}
