import 'package:devpilot_app/features/skill/data/skill_models.dart';
import 'package:devpilot_app/features/skill/data/skill_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// `GET /skills/{skillId}` (docs/05 §6.4). 학습 단계 6칸과 `whyItMatters`가 여기서 온다.
///
/// 트리·내 상태 캐시(`skillTreeProvider`·`mySkillStatesProvider`)와 따로 둔다 — 단계 조회만 실패해도 나머지 화면은 그대로 쓴다
/// (docs/02 SCR-SKILL-DETAIL 상태).
final skillDetailProvider = FutureProvider.family<SkillDetailView, String>(
  (ref, skillId) => ref.watch(skillRepositoryProvider).fetchDetail(skillId),
);
