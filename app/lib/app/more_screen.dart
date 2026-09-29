import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/ai_status_widgets.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-MORE: mobile entry to destinations without a bottom tab (docs/02 §3.15), only for shipped
/// stages. From 600 px the rail shows them, so the route goes to `/today`. The AI banner or budget
/// note sits above the list (docs/02 SCR-MORE).
///
/// 묶음과 한 줄 설명은 BL-CLI-49다. 기능 이름만 늘어놓으면 처음 쓰는 사람은 <b>어디부터 열어야 할지</b> 알 수 없다 —
/// 그래서 "무엇을 하러 가는가"로 나누고, 각 줄이 그 화면이 무엇을 위한 곳인지 말한다.
class MoreScreen extends ConsumerWidget {
  const MoreScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final aiStatus = ref.watch(aiStatusProvider);
    if (MediaQuery.sizeOf(context).width >= AppBreakpoints.tablet) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (context.mounted) {
          context.go(AppRoutes.today);
        }
      });
    }
    return Scaffold(
      appBar: AppBar(title: Semantics(header: true, child: Text(l10n.moreTitle))),
      body: ListView(
        children: [
          AiUnavailableBanner(status: aiStatus),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg),
            child: BudgetWarningNote(status: aiStatus),
          ),
          _GroupHeader(key: const Key('more.group.learn'), label: l10n.moreGroupLearn),
          // 진단의 다른 입구(온보딩 5단계, Today 생성 전 카드, 진단 결과 화면)는 모두 한 번 닫히면
          // 다시 열리지 않는다. 여기가 항상 열려 있는 유일한 문이다.
          _MoreTile(
            tileKey: const Key('more.diagnostics'),
            icon: Icons.straighten,
            label: l10n.moreDiagnostics,
            description: l10n.moreDiagnosticsDesc,
            path: AppRoutes.diagnostics,
          ),
          _MoreTile(
            tileKey: const Key('more.lessons'),
            icon: Icons.menu_book_outlined,
            label: l10n.lessonListOpen,
            description: l10n.moreLessonsDesc,
            path: AppRoutes.lessonsPrefix,
          ),
          _MoreTile(
            tileKey: const Key('more.tips'),
            icon: Icons.lightbulb_outline,
            label: l10n.tipsListTitle,
            description: l10n.moreTipsDesc,
            path: AppRoutes.tipsPrefix,
          ),
          _MoreTile(
            tileKey: const Key('more.terms'),
            icon: Icons.abc,
            label: l10n.termsListTitle,
            description: l10n.moreTermsDesc,
            path: AppRoutes.termsPrefix,
          ),
          _MoreTile(
            tileKey: const Key('more.training'),
            icon: Icons.fitness_center,
            label: l10n.moreTraining,
            description: l10n.moreTrainingDesc,
            path: AppRoutes.training,
          ),
          _GroupHeader(key: const Key('more.group.record'), label: l10n.moreGroupRecord),
          // 서버에 "내가 한 일" 목록이 없어서 러버덕 대화와 코드 읽기는 끝나면 다시 못 연다.
          // 이 브라우저가 기억한 방문 기록이 그 자리를 메운다
          // (DevPilot-ops/reachability-audit-2026-09-29.md).
          _MoreTile(
            tileKey: const Key('more.recent'),
            icon: Icons.history,
            label: l10n.moreRecent,
            description: l10n.moreRecentDesc,
            path: AppRoutes.recent,
          ),
          _MoreTile(
            tileKey: const Key('more.skills'),
            icon: Icons.account_tree_outlined,
            label: l10n.moreSkills,
            description: l10n.moreSkillsDesc,
            path: AppRoutes.skills,
          ),
          _MoreTile(
            tileKey: const Key('more.projects'),
            icon: Icons.code,
            label: l10n.moreProjects,
            description: l10n.moreProjectsDesc,
            path: AppRoutes.projects,
          ),
          _MoreTile(
            tileKey: const Key('more.dashboard'),
            icon: Icons.insights_outlined,
            label: l10n.moreDashboard,
            description: l10n.moreDashboardDesc,
            path: AppRoutes.dashboard,
          ),
          _GroupHeader(key: const Key('more.group.settings'), label: l10n.moreGroupSettings),
          _MoreTile(
            tileKey: const Key('more.settings'),
            icon: Icons.settings_outlined,
            label: l10n.moreSettings,
            description: l10n.moreSettingsDesc,
            path: AppRoutes.settings,
          ),
        ],
      ),
    );
  }
}

/// 묶음 제목. 스크린 리더는 목록 안의 구획으로 읽는다.
class _GroupHeader extends StatelessWidget {
  const _GroupHeader({super.key, required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        AppSpacing.lg,
        AppSpacing.md,
        AppSpacing.lg,
        AppSpacing.xs,
      ),
      child: Semantics(
        header: true,
        child: Text(
          label,
          style: theme.textTheme.labelLarge?.copyWith(color: theme.colorScheme.primary),
        ),
      ),
    );
  }
}

class _MoreTile extends StatelessWidget {
  const _MoreTile({
    required this.tileKey,
    required this.icon,
    required this.label,
    required this.description,
    required this.path,
  });

  final Key tileKey;
  final IconData icon;
  final String label;

  /// 그 화면이 <b>무엇을 위한 곳인지</b> 한 줄. 기능을 나열하지 않는다.
  final String description;
  final String path;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      key: tileKey,
      minTileHeight: AppSizes.listTile,
      leading: Icon(icon),
      title: Text(label),
      subtitle: Text(description),
      trailing: const Icon(Icons.chevron_right),
      onTap: () => context.go(path),
    );
  }
}
