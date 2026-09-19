import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

/// Navigation frame of the signed-in screens (docs/02 §2.1, §2.2).
///
/// Mobile (< 600) has a bottom bar; tablet has a rail with labels below the icons, desktop
/// (≥ 1024) an extended rail. Only S1 destinations exist: on mobile Today and Review are not built
/// yet, so the bar holds Plan and More, and More lists Projects, Skills and Settings.
class AppShell extends StatelessWidget {
  const AppShell({super.key, required this.location, required this.child});

  final String location;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final width = MediaQuery.sizeOf(context).width;
    if (width < AppBreakpoints.tablet) {
      return _MobileShell(location: location, child: child);
    }
    return _RailShell(
      location: location,
      extended: width >= AppBreakpoints.desktop,
      child: child,
    );
  }
}

enum _Destination {
  plan(AppRoutes.plan, Icons.timeline),
  projects(AppRoutes.projects, Icons.code),
  skills(AppRoutes.skills, Icons.account_tree_outlined),
  settings(AppRoutes.settings, Icons.settings_outlined),
  more(AppRoutes.more, Icons.menu);

  const _Destination(this.path, this.icon);

  final String path;
  final IconData icon;

  String label(AppLocalizations l10n) => switch (this) {
    plan => l10n.navPlan,
    projects => l10n.navProjects,
    skills => l10n.navSkills,
    settings => l10n.navSettings,
    more => l10n.navMore,
  };

  bool matches(String location) => location == path || location.startsWith('$path/');

  /// The destination shown as selected for [location] (docs/02 §2.2: sub-routes select their
  /// parent destination).
  static _Destination? of(String location) {
    for (final destination in values) {
      if (destination.matches(location)) {
        return destination;
      }
    }
    return null;
  }
}

class _MobileShell extends StatelessWidget {
  const _MobileShell({required this.location, required this.child});

  static const _tabs = [_Destination.plan, _Destination.more];

  final String location;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final current = _Destination.of(location);
    // Projects, Skills and Settings live under More on mobile.
    final selectedIndex = current == _Destination.plan ? 0 : 1;
    return Scaffold(
      body: child,
      bottomNavigationBar: NavigationBar(
        key: const Key('shell.bottomNavigation'),
        selectedIndex: selectedIndex,
        onDestinationSelected: (index) => context.go(_tabs[index].path),
        destinations: [
          for (final tab in _tabs)
            NavigationDestination(
              key: Key('shell.nav.${tab.name}'),
              icon: Icon(tab.icon),
              label: tab.label(l10n),
            ),
        ],
      ),
    );
  }
}

class _RailShell extends StatelessWidget {
  const _RailShell({required this.location, required this.extended, required this.child});

  static const _railItems = [_Destination.plan, _Destination.projects, _Destination.skills];

  final String location;
  final bool extended;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final current = _Destination.of(location);
    final railIndex = _railItems.indexOf(current ?? _Destination.more);
    return Scaffold(
      body: Row(
        children: [
          NavigationRail(
            key: const Key('shell.navigationRail'),
            extended: extended,
            minWidth: AppBreakpoints.railWidth,
            minExtendedWidth: AppBreakpoints.extendedRailWidth,
            labelType: extended ? NavigationRailLabelType.none : NavigationRailLabelType.all,
            selectedIndex: railIndex < 0 ? null : railIndex,
            onDestinationSelected: (index) => context.go(_railItems[index].path),
            destinations: [
              for (final item in _railItems)
                NavigationRailDestination(
                  icon: Icon(item.icon, key: Key('shell.nav.${item.name}')),
                  label: Text(item.label(l10n)),
                ),
            ],
            trailingAtBottom: true,
            trailing: _RailSettingsButton(
              extended: extended,
              selected: current == _Destination.settings,
            ),
          ),
          const VerticalDivider(width: 1),
          // The nested navigator's modal barrier blocks the semantics of everything painted
          // before it up to the nearest semantics container. Without this container the rail
          // (painted first) disappears from the accessibility tree (docs/02 A-6).
          // The page area's own messenger keeps toasts inside it: a toast over the whole shell
          // covered the bottom-pinned Settings button for 4 s.
          Expanded(
            child: Semantics(
              container: true,
              child: ScaffoldMessenger(child: child),
            ),
          ),
        ],
      ),
    );
  }
}

/// Settings pinned to the bottom of the rail (docs/02 §2.2 "rail (하단 고정)").
class _RailSettingsButton extends StatelessWidget {
  const _RailSettingsButton({required this.extended, required this.selected});

  final bool extended;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final colorScheme = Theme.of(context).colorScheme;
    final color = selected ? colorScheme.onPrimaryContainer : colorScheme.onSurfaceVariant;
    final label = Text(_Destination.settings.label(l10n), style: TextStyle(color: color));
    final icon = Icon(_Destination.settings.icon, color: color);
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.lg),
      child: Semantics(
        selected: selected,
        child: TextButton(
          key: const Key('shell.nav.settings'),
          style: TextButton.styleFrom(
            backgroundColor: selected ? colorScheme.primaryContainer : null,
            minimumSize: Size(extended ? 180 : 64, 56),
          ),
          onPressed: () => context.go(AppRoutes.settings),
          child: extended
              ? Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    icon,
                    const SizedBox(width: AppSpacing.md),
                    label,
                  ],
                )
              : Column(mainAxisSize: MainAxisSize.min, children: [icon, label]),
        ),
      ),
    );
  }
}
