import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Collapsed "문제 신고 정보" section: code, traceId, time and a copy button (docs/02 §3.3, §5.1).
///
/// Technical details stay here and never in the main message (docs/02 §9, docs/08 §8.5).
class ReportInfo extends StatelessWidget {
  const ReportInfo({super.key, required this.failure});

  final ApiException failure;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final traceId = failure.traceId;
    final occurredAt = failure.occurredAt;
    // SelectionArea + Text, not SelectableText: on the web SelectableText reaches screen readers as
    // an empty read-only field (docs/02 A-6).
    return ExpansionTile(
      key: const Key('common.reportInfo'),
      title: Text(l10n.commonErrorReportInfo),
      children: [
        SelectionArea(
          child: Column(
            children: [
              ListTile(
                dense: true,
                title: Text(l10n.commonErrorCodeLabel),
                subtitle: Text(failure.code),
              ),
              if (traceId != null)
                ListTile(
                  dense: true,
                  title: Text(l10n.commonErrorTraceIdLabel),
                  subtitle: Text(traceId),
                ),
              if (occurredAt != null)
                ListTile(
                  dense: true,
                  title: Text(l10n.commonErrorTimeLabel),
                  subtitle: Text(occurredAt.toUtc().toIso8601String()),
                ),
            ],
          ),
        ),
        Align(
          alignment: Alignment.centerRight,
          child: TextButton.icon(
            key: const Key('common.copyReportButton'),
            onPressed: () => _copy(context, l10n),
            icon: const Icon(Icons.copy),
            label: Text(l10n.commonErrorCopy),
          ),
        ),
      ],
    );
  }

  Future<void> _copy(BuildContext context, AppLocalizations l10n) async {
    final lines = [
      '${l10n.commonErrorCodeLabel}: ${failure.code}',
      if (failure.traceId != null) '${l10n.commonErrorTraceIdLabel}: ${failure.traceId}',
      if (failure.occurredAt != null)
        '${l10n.commonErrorTimeLabel}: ${failure.occurredAt!.toUtc().toIso8601String()}',
    ];
    await Clipboard.setData(ClipboardData(text: lines.join('\n')));
    if (context.mounted) {
      showToast(context, l10n.commonErrorCopied);
    }
  }
}
