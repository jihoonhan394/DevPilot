import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/features/today/domain/today_rules.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// The generate form values: available minutes and energy.
@immutable
final class TodayInput {
  const TodayInput({required this.minutes, required this.energy});

  final int minutes;
  final EnergyLevel energy;
}

/// Keeps the generate form values in a provider so a resize that swaps the navigation frame does
/// not reset them (docs/02 §2.1). Defaults: the day's study time and `NORMAL` (U-2).
final class TodayInputController extends Notifier<TodayInput> {
  @override
  TodayInput build() {
    final me = ref.read(meProvider).requireValue;
    return TodayInput(
      minutes: TodayRules.defaultMinutes(
        today: LocalDate.parse(me.today),
        weekdayMinutes: me.weekdayStudyMinutes,
        weekendMinutes: me.weekendStudyMinutes,
      ),
      energy: EnergyLevel.normal,
    );
  }

  void setMinutes(int minutes) => state = TodayInput(minutes: minutes, energy: state.energy);

  void setEnergy(EnergyLevel energy) => state = TodayInput(minutes: state.minutes, energy: energy);
}

final todayInputProvider = NotifierProvider.autoDispose<TodayInputController, TodayInput>(
  TodayInputController.new,
);
