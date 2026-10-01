import 'package:devpilot_app/features/plan/data/plan_buildable_models.dart';
import 'package:devpilot_app/features/plan/data/plan_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// 지금 만들 수 있는 것 (docs/05 §7.10). 계산만 하는 조회라 새로 열 때마다 다시 받는다 — 근거 레벨은 과제 하나로도 바뀐다.
final buildableProvider = FutureProvider.autoDispose<BuildableView>(
  (ref) => ref.watch(planRepositoryProvider).fetchActiveBuildable(),
);
