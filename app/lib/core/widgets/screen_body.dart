import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:flutter/material.dart';

/// Scrollable screen content with the width rules of docs/02 §2.1: full width with a 16 px margin
/// on mobile, at most 720 on tablet and 960 on desktop, centered.
class ScreenBody extends StatelessWidget {
  const ScreenBody({super.key, required this.child, this.controller, this.maxWidth});

  final Widget child;
  final ScrollController? controller;

  /// Narrower limit for forms; the breakpoint width applies when it is larger.
  final double? maxWidth;

  static double contentWidthFor(double screenWidth) {
    if (screenWidth >= AppBreakpoints.desktop) {
      return AppBreakpoints.desktopContentWidth;
    }
    if (screenWidth >= AppBreakpoints.tablet) {
      return AppBreakpoints.tabletContentWidth;
    }
    return double.infinity;
  }

  @override
  Widget build(BuildContext context) {
    final breakpointWidth = contentWidthFor(MediaQuery.sizeOf(context).width);
    final limit = maxWidth;
    final width = limit != null && limit < breakpointWidth ? limit : breakpointWidth;
    return SingleChildScrollView(
      controller: controller,
      padding: const EdgeInsets.fromLTRB(
        AppSpacing.screen,
        AppSpacing.lg,
        AppSpacing.screen,
        AppSpacing.xxl,
      ),
      child: Center(
        child: ConstrainedBox(
          constraints: BoxConstraints(maxWidth: width),
          child: child,
        ),
      ),
    );
  }
}

/// Section heading announced as a header to screen readers (docs/02 A-4).
class SectionTitle extends StatelessWidget {
  const SectionTitle(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      header: true,
      child: Text(text, style: Theme.of(context).textTheme.titleMedium),
    );
  }
}
