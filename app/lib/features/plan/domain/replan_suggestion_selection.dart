import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/features/plan/data/plan_models.dart';
import 'package:flutter/foundation.dart';

/// The replan suggestions the user checked on SCR-REPLAN (docs/02 SCR-REPLAN "제안 영역 규칙").
/// Only checked items are sent; nothing is applied automatically (FR-05).
@immutable
final class ReplanSuggestionSelection {
  const ReplanSuggestionSelection({
    this.deferrals = const {},
    this.reductions = const {},
    this.restorations = const {},
    this.raises = const {},
  });

  static const empty = ReplanSuggestionSelection();

  /// `acceptedDeferrals`: skill codes.
  final Set<String> deferrals;

  /// `acceptedTargetReductions`, keyed by [targetKey].
  final Map<String, TargetChangeInput> reductions;

  /// `restoredDeferrals`: skill codes, from `RESTORE_DEFERRED` or the deferred-skill list.
  final Set<String> restorations;

  /// `acceptedTargetRaises`, keyed by [targetKey].
  final Map<String, TargetChangeInput> raises;

  static String targetKey(String skillCode, SkillAxis axis) => '$skillCode|${axis.name}';

  bool get isEmpty =>
      deferrals.isEmpty && reductions.isEmpty && restorations.isEmpty && raises.isEmpty;

  ReplanSuggestionSelection toggleDeferral(String skillCode) => ReplanSuggestionSelection(
    deferrals: _toggled(deferrals, skillCode),
    reductions: reductions,
    restorations: restorations,
    raises: raises,
  );

  ReplanSuggestionSelection toggleReduction(TargetChangeInput change) => ReplanSuggestionSelection(
    deferrals: deferrals,
    reductions: _toggledChange(reductions, change),
    restorations: restorations,
    raises: raises,
  );

  ReplanSuggestionSelection toggleRestoration(String skillCode) => ReplanSuggestionSelection(
    deferrals: deferrals,
    reductions: reductions,
    restorations: _toggled(restorations, skillCode),
    raises: raises,
  );

  ReplanSuggestionSelection toggleRaise(TargetChangeInput change) => ReplanSuggestionSelection(
    deferrals: deferrals,
    reductions: reductions,
    restorations: restorations,
    raises: _toggledChange(raises, change),
  );

  /// [request] with the four suggestion lists replaced by this selection, in check order.
  ReplanRequest applyTo(ReplanRequest request) => request.copyWith(
    acceptedDeferrals: [...deferrals],
    acceptedTargetReductions: [...reductions.values],
    restoredDeferrals: [...restorations],
    acceptedTargetRaises: [...raises.values],
  );

  /// Row identity (see [SuggestionIdentity]) of the item a server field path such as
  /// `acceptedTargetRaises[1].newTarget` points to, or null for other fields.
  String? identityOfField(String field) {
    final match = _fieldPattern.firstMatch(field);
    if (match == null) {
      return null;
    }
    final index = int.parse(match.group(2)!);
    String? at<T>(Iterable<T> items, String Function(T item) identity) =>
        index < items.length ? identity(items.elementAt(index)) : null;
    return switch (match.group(1)) {
      'acceptedDeferrals' => at(deferrals, SuggestionIdentity.defer),
      'acceptedTargetReductions' => at(reductions.values, SuggestionIdentity.reduce),
      'restoredDeferrals' => at(restorations, SuggestionIdentity.restore),
      _ => at(raises.values, SuggestionIdentity.raise),
    };
  }

  static final _fieldPattern = RegExp(
    r'^(acceptedDeferrals|acceptedTargetReductions|restoredDeferrals|acceptedTargetRaises)\[(\d+)\]',
  );

  static Set<String> _toggled(Set<String> values, String value) =>
      values.contains(value) ? ({...values}..remove(value)) : {...values, value};

  static Map<String, TargetChangeInput> _toggledChange(
    Map<String, TargetChangeInput> values,
    TargetChangeInput change,
  ) {
    final key = targetKey(change.skillCode, change.axis);
    return values.containsKey(key) ? ({...values}..remove(key)) : {...values, key: change};
  }
}

/// Stable ids of suggestion rows, used for keys and for inline errors under a row.
abstract final class SuggestionIdentity {
  static String defer(String skillCode) => 'defer.$skillCode';

  static String reduce(TargetChangeInput change) =>
      'reduce.${change.skillCode}.${change.axis.name}';

  static String restore(String skillCode) => 'restore.$skillCode';

  static String raise(TargetChangeInput change) => 'raise.${change.skillCode}.${change.axis.name}';
}
