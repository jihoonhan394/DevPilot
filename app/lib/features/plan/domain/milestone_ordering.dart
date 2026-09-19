import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/features/plan/data/plan_models.dart';

/// Direction of the ↑/↓ buttons on a milestone card.
enum MoveDirection { up, down }

/// The two `sortOrder` PATCHes of one ↑/↓ tap (docs/02 SCR-PLAN "순서 ↑↓").
final class MilestoneSwap {
  const MilestoneSwap({required this.moved, required this.neighbor});

  /// The tapped milestone with its new sort order.
  final ({MilestoneView milestone, int sortOrder}) moved;

  /// The adjacent milestone with its new sort order.
  final ({MilestoneView milestone, int sortOrder}) neighbor;
}

/// Ordering rules of the plan timeline.
abstract final class MilestoneOrdering {
  /// Server order: sortOrder → startDate → id (docs/05 §7.1).
  static List<MilestoneView> sorted(Iterable<MilestoneView> milestones) =>
      milestones.toList()..sort(_compare);

  static int _compare(MilestoneView first, MilestoneView second) {
    final bySortOrder = first.sortOrder.compareTo(second.sortOrder);
    if (bySortOrder != 0) {
      return bySortOrder;
    }
    final byStart = first.startDate.compareTo(second.startDate);
    return byStart != 0 ? byStart : first.id.compareTo(second.id);
  }

  /// Swap with the neighbor in [direction], or null at the list edge.
  ///
  /// Distinct sort orders are swapped. Equal ones (possible after a replan) become neighbor ± 1 so
  /// the order still changes.
  static MilestoneSwap? swap(
    List<MilestoneView> ordered,
    String milestoneId,
    MoveDirection direction,
  ) {
    final index = ordered.indexWhere((milestone) => milestone.id == milestoneId);
    final neighborIndex = direction == MoveDirection.up ? index - 1 : index + 1;
    if (index < 0 || neighborIndex < 0 || neighborIndex >= ordered.length) {
      return null;
    }
    final moved = ordered[index];
    final neighbor = ordered[neighborIndex];
    if (moved.sortOrder != neighbor.sortOrder) {
      return MilestoneSwap(
        moved: (milestone: moved, sortOrder: neighbor.sortOrder),
        neighbor: (milestone: neighbor, sortOrder: moved.sortOrder),
      );
    }
    final shifted = direction == MoveDirection.up
        ? (moved: neighbor.sortOrder, neighbor: neighbor.sortOrder + 1)
        : (moved: neighbor.sortOrder + 1, neighbor: neighbor.sortOrder);
    return MilestoneSwap(
      moved: (milestone: moved, sortOrder: shifted.moved),
      neighbor: (milestone: neighbor, sortOrder: shifted.neighbor),
    );
  }

  /// Position of the "오늘" divider: before the first milestone that starts after [today]
  /// (docs/02 SCR-PLAN mobile timeline). Equals the length when all have started.
  static int todayDividerIndex(List<MilestoneView> ordered, LocalDate today) {
    for (var index = 0; index < ordered.length; index++) {
      if (LocalDate.parse(ordered[index].startDate).isAfter(today)) {
        return index;
      }
    }
    return ordered.length;
  }
}

/// Translates milestone ids held by the client after a replan (docs/05 §7.8 `milestoneIdMapping`).
///
/// Every milestone gets a new id in the new plan version; ids the client still holds (for example
/// the milestone the user was editing) are mapped so the UI can find them again.
final class MilestoneIdMapper {
  MilestoneIdMapper(List<MilestoneIdMappingView> mapping)
    : _newIds = {for (final entry in mapping) entry.previousId: entry.newId};

  final Map<String, String> _newIds;

  /// New id of [previousId], or null when that milestone was removed or never existed.
  String? newIdOf(String previousId) => _newIds[previousId];
}
