import 'package:devpilot_app/core/storage/key_value_store.dart';
import 'package:devpilot_app/core/widgets/confirm_dialog.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Browser-local record that the provider notice was accepted (docs/07 §8.1: "동의 상태는 기기
/// 로컬 저장. 서버 저장 없음").
const aiProviderNoticeStorageKey = 'devpilot.ai.providerNotice.accepted';

/// The one-time notice before the first input goes to the AI provider (docs/07 §8.1, ADR-013):
/// hint generation, answer evaluation and the rubber duck. Returns true when the user accepted it
/// now or before; false leaves the input untouched and sends nothing.
Future<bool> confirmAiProviderNotice(BuildContext context) async {
  final store = ProviderScope.containerOf(context, listen: false).read(keyValueStoreProvider);
  if (store.read(aiProviderNoticeStorageKey) != null) {
    return true;
  }
  final l10n = AppLocalizations.of(context);
  final accepted = await showConfirmDialog(
    context,
    title: l10n.aiProviderNoticeTitle,
    body: l10n.aiProviderNoticeBody,
    confirmLabel: l10n.aiProviderNoticeConfirm,
    cancelLabel: l10n.commonCancel,
    confirmKey: const Key('ai.providerNoticeConfirmButton'),
  );
  if (accepted) {
    store.write(aiProviderNoticeStorageKey, 'true');
  }
  return accepted;
}
