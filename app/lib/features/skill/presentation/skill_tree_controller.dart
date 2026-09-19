import 'dart:convert';

import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/storage/key_value_store.dart';
import 'package:devpilot_app/features/skill/data/skill_models.dart';
import 'package:devpilot_app/features/skill/data/skill_repository.dart';
import 'package:devpilot_app/features/skill/domain/skill_overview.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Catalog and states together (`GET /skills/tree` + `GET /skills/me`, requested in parallel).
final skillOverviewDataProvider =
    FutureProvider.autoDispose<(SkillTreeResponse, UserSkillStatesResponse)>((ref) async {
      final tree = ref.watch(skillTreeProvider.future);
      final states = ref.watch(mySkillStatesProvider.future);
      return (await tree, await states);
    });

final class SkillTreeFilterController extends Notifier<SkillTreeFilter> {
  @override
  SkillTreeFilter build() => const SkillTreeFilter();

  void togglePriority(Priority priority) => state = state.togglePriority(priority);

  void toggleOnlyBelowTarget() => state = state.toggleOnlyBelowTarget();
}

final skillTreeFilterProvider =
    NotifierProvider.autoDispose<SkillTreeFilterController, SkillTreeFilter>(
      SkillTreeFilterController.new,
    );

/// Expanded categories, remembered in `localStorage` `devpilot.skills.expanded` (docs/02
/// SCR-SKILL-TREE "행동").
final class ExpandedCategoriesController extends Notifier<Set<SkillCategory>> {
  static const storageKey = 'devpilot.skills.expanded';

  @override
  Set<SkillCategory> build() {
    final raw = ref.read(keyValueStoreProvider).read(storageKey);
    if (raw == null) {
      return const {};
    }
    try {
      final Object? names = jsonDecode(raw);
      if (names is! List<Object?>) {
        return const {};
      }
      return {
        for (final category in SkillCategory.values)
          if (names.contains(category.name)) category,
      };
    } on FormatException {
      return const {};
    }
  }

  void toggle(SkillCategory category) {
    state = state.contains(category) ? ({...state}..remove(category)) : {...state, category};
    ref
        .read(keyValueStoreProvider)
        .write(storageKey, jsonEncode([for (final category in state) category.name]));
  }
}

final expandedCategoriesProvider =
    NotifierProvider<ExpandedCategoriesController, Set<SkillCategory>>(
      ExpandedCategoriesController.new,
    );
