import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/ai_status_widgets.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-MORE: mobile entry to destinations without a bottom tab (docs/02 §3.15), in the spec's
/// order and only for shipped stages. From 600 px the rail shows them, so the route goes to
/// `/today`. The AI banner or budget note sits above the list (docs/02 SCR-MORE).
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
          _MoreTile(
            tileKey: const Key('more.projects'),
            icon: Icons.code,
            label: l10n.moreProjects,
            path: AppRoutes.projects,
          ),
          _MoreTile(
            tileKey: const Key('more.training'),
            icon: Icons.fitness_center,
            label: l10n.moreTraining,
            path: AppRoutes.training,
          ),
          _MoreTile(
            tileKey: const Key('more.skills'),
            icon: Icons.account_tree_outlined,
            label: l10n.moreSkills,
            path: AppRoutes.skills,
          ),
          _MoreTile(
            tileKey: const Key('more.dashboard'),
            icon: Icons.insights_outlined,
            label: l10n.moreDashboard,
            path: AppRoutes.dashboard,
          ),
          _MoreTile(
            tileKey: const Key('more.settings'),
            icon: Icons.settings_outlined,
            label: l10n.moreSettings,
            path: AppRoutes.settings,
          ),
        ],
      ),
    );
  }
}

class _MoreTile extends StatelessWidget {
  const _MoreTile({
    required this.tileKey,
    required this.icon,
    required this.label,
    required this.path,
  });

  final Key tileKey;
  final IconData icon;
  final String label;
  final String path;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      key: tileKey,
      minTileHeight: AppSizes.listTile,
      leading: Icon(icon),
      title: Text(label),
      trailing: const Icon(Icons.chevron_right),
      onTap: () => context.go(path),
    );
  }
}
