import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// First-load placeholder shaped like the screen (docs/02 §3.3). A spinner alone is not allowed.
///
/// The blocks pulse over 1.2 s unless animations are disabled (docs/02 A-12). Screen readers hear
/// one "불러오는 중이에요" instead of the empty blocks.
class LoadingSkeleton extends StatefulWidget {
  const LoadingSkeleton({super.key, required this.child});

  final Widget child;

  @override
  State<LoadingSkeleton> createState() => _LoadingSkeletonState();
}

class _LoadingSkeletonState extends State<LoadingSkeleton> with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: AppMotion.shimmer,
    lowerBound: 0.55,
  );

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (MediaQuery.disableAnimationsOf(context)) {
      _controller
        ..stop()
        ..value = 1;
    } else if (!_controller.isAnimating) {
      _controller.repeat(reverse: true);
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Semantics(
      key: const Key('common.loading'),
      label: AppLocalizations.of(context).commonLoading,
      child: ExcludeSemantics(
        child: FadeTransition(opacity: _controller, child: widget.child),
      ),
    );
  }
}

/// One grey block of a skeleton.
class SkeletonBox extends StatelessWidget {
  const SkeletonBox({super.key, this.width, this.height = 16});

  final double? width;
  final double height;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: width,
      height: height,
      margin: const EdgeInsets.only(bottom: AppSpacing.md),
      decoration: BoxDecoration(
        color: DevPilotColors.of(context).skeleton,
        borderRadius: const BorderRadius.all(Radius.circular(AppRadius.sm)),
      ),
    );
  }
}

/// A card-shaped skeleton with [lines] text blocks, used for list rows and cards.
class SkeletonCard extends StatelessWidget {
  const SkeletonCard({super.key, this.lines = 3});

  final int lines;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            for (var index = 0; index < lines; index++)
              FractionallySizedBox(
                widthFactor: index == 0 ? 0.5 : (index.isOdd ? 0.9 : 0.7),
                child: const SkeletonBox(),
              ),
          ],
        ),
      ),
    );
  }
}

/// [count] skeleton cards stacked with section spacing.
class SkeletonList extends StatelessWidget {
  const SkeletonList({super.key, this.count = 3, this.lines = 3});

  final int count;
  final int lines;

  @override
  Widget build(BuildContext context) {
    return LoadingSkeleton(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (var index = 0; index < count; index++) ...[
            if (index > 0) const SizedBox(height: AppSpacing.md),
            SkeletonCard(lines: lines),
          ],
        ],
      ),
    );
  }
}
