import 'package:devpilot_app/core/history/recent_store.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Remembers that this screen was opened, once per build of the route.
///
/// Wrap the body of a screen that the reader may want to come back to. The title is whatever the
/// screen already shows, so an empty one records nothing — a half-loaded screen is not worth a line
/// in the list.
class RecordVisit extends ConsumerStatefulWidget {
  const RecordVisit({
    super.key,
    required this.kind,
    required this.route,
    required this.title,
    required this.child,
  });

  final RecentKind kind;
  final String route;

  /// Null or empty while the screen is still loading: nothing is recorded yet.
  final String? title;
  final Widget child;

  @override
  ConsumerState<RecordVisit> createState() => _RecordVisitState();
}

class _RecordVisitState extends ConsumerState<RecordVisit> {
  String? _recorded;

  @override
  Widget build(BuildContext context) {
    final title = widget.title;
    // 제목이 들어온 첫 프레임에 한 번만 적는다. build 중에 provider 를 고치면 안 되므로 프레임 뒤로 민다.
    if (title != null && title.isNotEmpty && _recorded != widget.route) {
      _recorded = widget.route;
      final entry = RecentEntry(
        kind: widget.kind,
        route: widget.route,
        title: title,
        visitedAt: DateTime.now(),
      );
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          ref.read(recentEntriesProvider.notifier).add(entry);
        }
      });
    }
    return widget.child;
  }
}
