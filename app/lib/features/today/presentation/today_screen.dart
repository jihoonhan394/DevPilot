import 'dart:async';

import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/refreshing_bar.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/features/today/presentation/today_actions.dart';
import 'package:devpilot_app/features/today/presentation/today_controller.dart';
import 'package:devpilot_app/features/today/presentation/today_generate_panel.dart';
import 'package:devpilot_app/features/today/presentation/today_generated_view.dart';
import 'package:devpilot_app/features/today/presentation/today_input_controller.dart';
import 'package:devpilot_app/features/today/presentation/today_state.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-TODAY: the start page. Minutes and energy → one main task with its reasons, started and
/// finished here (docs/02 §3.5, §4.2).
class TodayScreen extends ConsumerStatefulWidget {
  const TodayScreen({super.key, this.completeTaskId, this.footer});

  /// `?complete=<taskId>`: opens the completion sheet when that task is in progress.
  final String? completeTaskId;

  /// Extra content below the plan (the install card).
  final Widget? footer;

  @override
  ConsumerState<TodayScreen> createState() => _TodayScreenState();
}

class _TodayScreenState extends ConsumerState<TodayScreen> {
  late final AppLifecycleListener _lifecycle;
  var _completeSheetHandled = false;

  @override
  void initState() {
    super.initState();
    // Coming back to the browser tab re-reads Today (docs/02 SCR-TODAY "데이터").
    _lifecycle = AppLifecycleListener(
      onShow: () => ref.read(todayControllerProvider.notifier).reload(),
    );
  }

  @override
  void dispose() {
    _lifecycle.dispose();
    super.dispose();
  }

  void _openRequestedSheet(TodayScreenData data) {
    final taskId = widget.completeTaskId;
    final main = data.mainTask;
    if (_completeSheetHandled || taskId == null || main == null) {
      return;
    }
    _completeSheetHandled = true;
    if (main.id == taskId && main.status == TaskStatus.inProgress && data.mainSession != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          unawaited(openCompleteSheet(context, ref, partial: false));
        }
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    // The form values live as long as the screen, also while the form itself is not shown.
    ref.listen(todayInputProvider, (_, _) {});
    final screen = ref.watch(todayControllerProvider);
    final data = screen.value;
    if (data != null) {
      _openRequestedSheet(data);
    }
    // Before generation the header uses `GET /me.today` (docs/02 SCR-TODAY "행동·검증").
    final LocalDate planDate =
        LocalDate.tryParse(data?.today?.planDate) ?? ref.watch(userTodayProvider);
    return Scaffold(
      appBar: AppBar(
        title: Semantics(
          header: true,
          child: Text(formatPlanDate(planDate, l10n), key: const Key('today.date')),
        ),
        actions: [
          TextButton(
            key: const Key('today.dashboardLink'),
            onPressed: () => context.go(AppRoutes.dashboard),
            child: Text(l10n.todayDashboardLink),
          ),
          const SizedBox(width: AppSpacing.sm),
        ],
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(2),
          child: RefreshingBar(visible: screen.isRefreshing),
        ),
      ),
      body: screen.when(
        loading: () => const ScreenBody(child: _TodaySkeleton()),
        error: (error, _) => ScreenBody(
          child: ErrorView(
            error: error,
            onRetry: () => ref.read(todayControllerProvider.notifier).reload(),
          ),
        ),
        data: (data) => ScreenBody(
          child: _TodayBody(data: data, footer: widget.footer),
        ),
      ),
    );
  }
}

class _TodayBody extends StatelessWidget {
  const _TodayBody({required this.data, required this.footer});

  final TodayScreenData data;
  final Widget? footer;

  @override
  Widget build(BuildContext context) {
    final today = data.today;
    if (data.noPlan) {
      return TodayNoPlanState(busy: data.busy);
    }
    if (today == null) {
      return TodayGeneratePanel(busy: data.busy, footer: footer);
    }
    return TodayGeneratedView(data: data, today: today, footer: footer);
  }
}

/// Main card and review row placeholders (docs/02 SCR-TODAY "Loading").
class _TodaySkeleton extends StatelessWidget {
  const _TodaySkeleton();

  @override
  Widget build(BuildContext context) {
    return const LoadingSkeleton(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          FractionallySizedBox(
            alignment: Alignment.centerLeft,
            widthFactor: 0.4,
            child: SkeletonBox(height: 20),
          ),
          SizedBox(height: AppSpacing.md),
          SkeletonCard(lines: 5),
          SizedBox(height: AppSpacing.md),
          SkeletonCard(lines: 1),
        ],
      ),
    );
  }
}
