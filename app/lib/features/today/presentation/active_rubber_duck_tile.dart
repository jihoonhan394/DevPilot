import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_local_store.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_repository.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// The rubber duck session this device left IN_PROGRESS, checked with `GET /rubber-duck/{id}`.
/// A session that ended (or a 404) drops the record; other failures only hide the row
/// (docs/02 SCR-TODAY "진행 중 러버덕 줄").
final activeRubberDuckProvider = FutureProvider.autoDispose<({String sessionId, String? taskId})?>((
  ref,
) async {
  final local = ref.watch(rubberDuckLocalStoreProvider);
  final active = local.readActive();
  if (active == null) {
    return null;
  }
  try {
    final session = await ref.read(rubberDuckRepositoryProvider).fetchSession(active.sessionId);
    if (session.inProgress) {
      return active;
    }
  } on ApiException catch (error) {
    if (error.code != ApiErrorCode.resourceNotFound) {
      return null;
    }
  }
  local.clearActive();
  return null;
});

/// `ActiveRubberDuckTile`: "설명하던 러버덕이 있어요." + "이어서 설명하기".
class ActiveRubberDuckTile extends ConsumerWidget {
  const ActiveRubberDuckTile({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final active = ref.watch(activeRubberDuckProvider).value;
    if (active == null) {
      return const SizedBox.shrink();
    }
    final l10n = AppLocalizations.of(context);
    return Card(
      key: const Key('today.duckTile'),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg, vertical: AppSpacing.sm),
        child: Row(
          children: [
            Expanded(child: Text(l10n.todayDuckContinue)),
            OutlinedButton(
              key: const Key('today.duckContinueButton'),
              onPressed: () => context.go(
                AppRoutes.rubberDuckSession(active.sessionId, taskId: active.taskId),
              ),
              child: Text(l10n.todayDuckContinueButton),
            ),
          ],
        ),
      ),
    );
  }
}
