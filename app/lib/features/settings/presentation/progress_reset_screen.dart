import 'dart:async';

import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/error_message_mapper.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_draft_controller.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/features/settings/data/me_repository.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// 서버와 같은 문구여야 한다 (docs/05 §3.7, `ProgressResetService.CONFIRMATION`).
const resetConfirmation = '초기화합니다';

/// SCR-ACCOUNT-RESET (docs/02 §3.14, ADR-056, BL-CLI-50).
///
/// <b>무엇이 지워지고 무엇이 남는지 먼저 보이고</b>, 확인 입력은 그다음이다. 되돌릴 수 없는 일에 "예/아니오"만 묻지 않는다.
class ProgressResetScreen extends ConsumerStatefulWidget {
  const ProgressResetScreen({super.key});

  @override
  ConsumerState<ProgressResetScreen> createState() => _ProgressResetScreenState();
}

class _ProgressResetScreenState extends ConsumerState<ProgressResetScreen> {
  final TextEditingController _confirmController = TextEditingController();

  /// 같은 행동을 재시도하면 같은 key를 쓴다 (docs/02 §6.6).
  final IdempotencyKeyCache _resetKey = IdempotencyKeyCache();
  bool _includeProjects = false;
  bool _busy = false;

  bool get _canSubmit => _confirmController.text.trim() == resetConfirmation && !_busy;

  @override
  void dispose() {
    _confirmController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: Semantics(header: true, child: Text(l10n.settingsResetTitle))),
      body: ScreenBody(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(l10n.settingsResetLead),
            const Divider(height: AppSpacing.xl),
            _Bullets(
              title: l10n.settingsResetRemovedTitle,
              items: [
                l10n.settingsResetRemovedPlan,
                l10n.settingsResetRemovedToday,
                l10n.settingsResetRemovedSkill,
                l10n.settingsResetRemovedReview,
                l10n.settingsResetRemovedTraining,
                // 체크박스를 켜면 이 줄이 "남는 것"에서 여기로 옮겨 온다 — 보낼 값과 화면이 어긋나지 않게
                if (_includeProjects) l10n.settingsResetKeptProjects,
              ],
            ),
            const SizedBox(height: AppSpacing.md),
            _Bullets(
              title: l10n.settingsResetKeptTitle,
              items: [
                l10n.settingsResetKeptAccount,
                l10n.settingsResetKeptContent,
                if (!_includeProjects) l10n.settingsResetKeptProjects,
              ],
            ),
            const Divider(height: AppSpacing.xl),
            CheckboxListTile(
              key: const Key('reset.includeProjects'),
              contentPadding: EdgeInsets.zero,
              controlAffinity: ListTileControlAffinity.leading,
              value: _includeProjects,
              title: Text(l10n.settingsResetIncludeProjects),
              onChanged: _busy
                  ? null
                  : (value) => setState(() => _includeProjects = value ?? false),
            ),
            const SizedBox(height: AppSpacing.md),
            TextField(
              key: const Key('reset.confirmField'),
              controller: _confirmController,
              enabled: !_busy,
              decoration: InputDecoration(labelText: l10n.settingsResetConfirmLabel),
              // 틀린 값에 오류 문구를 띄우지 않는다 — 버튼이 꺼져 있는 것으로 충분하다
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: AppSpacing.lg),
            FilledButton(
              key: const Key('reset.submit'),
              style: FilledButton.styleFrom(backgroundColor: theme.colorScheme.error),
              onPressed: _canSubmit ? () => unawaited(_reset(l10n)) : null,
              child: Text(l10n.settingsResetButton),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _reset(AppLocalizations l10n) async {
    setState(() => _busy = true);
    try {
      await ref
          .read(meRepositoryProvider)
          .resetProgress(
            confirmation: _confirmController.text.trim(),
            includeProjects: _includeProjects,
            idempotencyKey: _resetKey.keyFor({'includeProjects': _includeProjects}),
          );
      _resetKey.settle(null);
      if (!mounted) {
        return;
      }
      // 지난 입력이 새 시작에 끼어들면 안 된다 (docs/02 SCR-ACCOUNT-RESET)
      ref.read(onboardingDraftProvider.notifier).clear();
      ref.invalidate(meProvider);
      showToast(context, l10n.settingsResetDone);
      context.go(AppRoutes.onboardingGoal);
    } on Object catch (error) {
      _resetKey.settle(error);
      if (mounted) {
        showToast(context, messageFor(error, l10n));
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }
}

/// 제목 + 점 목록. 지워지는 것과 남는 것을 같은 모양으로 보여 준다.
class _Bullets extends StatelessWidget {
  const _Bullets({required this.title, required this.items});

  final String title;
  final List<String> items;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: theme.textTheme.titleSmall),
        const SizedBox(height: AppSpacing.xs),
        for (final item in items)
          Padding(
            padding: const EdgeInsets.only(bottom: AppSpacing.xs),
            child: Text('· $item', style: theme.textTheme.bodySmall),
          ),
      ],
    );
  }
}
