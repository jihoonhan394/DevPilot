import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/error_message_mapper.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/inline_error.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/self_assessment_chips.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/skill/data/skill_models.dart';
import 'package:devpilot_app/features/skill/data/skill_repository.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// SCR-SELF-ASSESSMENT (`/settings/self-assessment`, docs/02 §3.20).
///
/// 온보딩에서 한 번 적고 끝이던 자기평가를 나중에 고친다. 그러지 못하면 "Spring을 2로 적었는데 사실 1이었다"를 깨달은 사람이
/// 진도 전체를 초기화해야 한다.
///
/// **바꾼 칸만 보낸다** (docs/05 §6.5). 열네 칸을 다 보내면 손대지 않은 값을 실수로 덮어쓴다.
class SelfAssessmentScreen extends ConsumerStatefulWidget {
  const SelfAssessmentScreen({super.key});

  @override
  ConsumerState<SelfAssessmentScreen> createState() => _SelfAssessmentScreenState();
}

class _SelfAssessmentScreenState extends ConsumerState<SelfAssessmentScreen> {
  /// 화면에서 고른 값. 저장 전까지 여기만 바뀐다.
  final _edited = <SkillCategory, int>{};
  bool _saving = false;
  Object? _saveError;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final states = ref.watch(mySkillStatesProvider);
    return Scaffold(
      appBar: AppBar(
        title: Semantics(header: true, child: Text(l10n.selfAssessmentTitle)),
      ),
      body: states.when(
        loading: () => const ScreenBody(child: SkeletonList(count: 4, lines: 3)),
        error: (error, _) => ScreenBody(
          child: ErrorView(error: error, onRetry: () => ref.invalidate(mySkillStatesProvider)),
        ),
        data: (data) => _body(saved(data)),
      ),
    );
  }

  /// category마다 가장 높은 자기평가 값. 전파 규칙상 한 category의 skill은 같은 값을 갖지만,
  /// 진단으로 일부가 달라졌을 수 있어 최댓값을 쓴다 (docs/06 §7.4).
  static Map<SkillCategory, int> saved(UserSkillStatesResponse data) {
    final levels = <SkillCategory, int>{};
    for (final item in data.items) {
      final level = item.selfAssessedLevel;
      if (level == null) {
        continue;
      }
      final category = item.skill.category;
      levels[category] = level > (levels[category] ?? 0) ? level : levels[category] ?? level;
    }
    return levels;
  }

  Widget _body(Map<SkillCategory, int> saved) {
    final l10n = AppLocalizations.of(context);
    final shown = {...saved, ..._edited};
    final changed = {
      for (final entry in _edited.entries)
        if (saved[entry.key] != entry.value) entry.key: entry.value,
    };
    final error = _saveError;
    return ScreenBody(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(l10n.selfAssessmentLead, style: Theme.of(context).textTheme.bodySmall),
          SelfAssessmentChips(
            keyPrefix: 'selfAssessment.level',
            levels: shown,
            onChanged: (category, level) => setState(() => _edited[category] = level),
          ),
          const SizedBox(height: AppSpacing.lg),
          if (error != null) InlineError(message: messageFor(error, l10n)),
          FilledButton(
            key: const Key('selfAssessment.saveButton'),
            onPressed: changed.isEmpty || _saving ? null : () => _save(changed),
            child: Text(l10n.commonSave),
          ),
          const SizedBox(height: AppSpacing.xs),
          Text(l10n.selfAssessmentNote, style: Theme.of(context).textTheme.bodySmall),
        ],
      ),
    );
  }

  Future<void> _save(Map<SkillCategory, int> changed) async {
    setState(() {
      _saving = true;
      _saveError = null;
    });
    try {
      await ref.read(skillRepositoryProvider).updateSelfAssessment(changed);
      if (!mounted) {
        return;
      }
      _edited.clear();
      ref.invalidate(mySkillStatesProvider);
      showToast(context, AppLocalizations.of(context).selfAssessmentSaved);
    } on Object catch (failure) {
      if (mounted) {
        setState(() => _saveError = failure);
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }
}
