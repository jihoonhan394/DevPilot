import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/features/settings/data/me_repository.dart';
import 'package:devpilot_app/features/settings/data/me_response.dart';
import 'package:devpilot_app/features/settings/data/update_me_request.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Editable profile and study-time settings (docs/02 SCR-SETTINGS, S1 part).
@immutable
final class SettingsForm {
  const SettingsForm({
    required this.me,
    required this.displayName,
    required this.timezone,
    required this.dayStartHour,
    required this.weekdayStudyMinutes,
    required this.weekendStudyMinutes,
    this.isSaving = false,
    this.saveError,
  });

  factory SettingsForm.of(MeResponse me) => SettingsForm(
    me: me,
    displayName: me.displayName,
    timezone: me.timezone,
    dayStartHour: me.dayStartHour,
    weekdayStudyMinutes: me.weekdayStudyMinutes,
    weekendStudyMinutes: me.weekendStudyMinutes,
  );

  /// The saved profile the form started from.
  final MeResponse me;
  final String displayName;
  final String timezone;
  final int dayStartHour;
  final int weekdayStudyMinutes;
  final int weekendStudyMinutes;
  final bool isSaving;

  /// Last `VALIDATION_FAILED` (for example `TIMEZONE_INVALID`), shown under the field.
  final ApiException? saveError;

  bool get isDirty => toRequest().toJson().length > 1;

  bool get isValid => InputRules.isValidDisplayName(displayName);

  /// A time zone or day-start change moves the plan-day boundary: ask first (docs/02 SCR-SETTINGS).
  bool get changesDayBoundary => timezone != me.timezone || dayStartHour != me.dayStartHour;

  /// `PATCH /me` with only the changed fields (null = unchanged, docs/05 §3.2).
  UpdateMeRequest toRequest() {
    final trimmedName = displayName.trim();
    return UpdateMeRequest(
      displayName: trimmedName == me.displayName ? null : trimmedName,
      timezone: timezone == me.timezone ? null : timezone,
      dayStartHour: dayStartHour == me.dayStartHour ? null : dayStartHour,
      weekdayStudyMinutes: weekdayStudyMinutes == me.weekdayStudyMinutes
          ? null
          : weekdayStudyMinutes,
      weekendStudyMinutes: weekendStudyMinutes == me.weekendStudyMinutes
          ? null
          : weekendStudyMinutes,
      version: me.version,
    );
  }

  SettingsForm copyWith({
    String? displayName,
    String? timezone,
    int? dayStartHour,
    int? weekdayStudyMinutes,
    int? weekendStudyMinutes,
    bool? isSaving,
    ApiException? Function()? saveError,
  }) => SettingsForm(
    me: me,
    displayName: displayName ?? this.displayName,
    timezone: timezone ?? this.timezone,
    dayStartHour: dayStartHour ?? this.dayStartHour,
    weekdayStudyMinutes: weekdayStudyMinutes ?? this.weekdayStudyMinutes,
    weekendStudyMinutes: weekendStudyMinutes ?? this.weekendStudyMinutes,
    isSaving: isSaving ?? this.isSaving,
    saveError: saveError == null ? this.saveError : saveError(),
  );
}

sealed class SettingsSaveOutcome {
  const SettingsSaveOutcome();
}

final class SettingsSaved extends SettingsSaveOutcome {
  const SettingsSaved();
}

/// `CONCURRENT_MODIFICATION`: the form now shows the latest profile.
final class SettingsReloaded extends SettingsSaveOutcome {
  const SettingsReloaded();
}

final class SettingsSaveFailed extends SettingsSaveOutcome {
  const SettingsSaveFailed(this.error);

  final Object error;
}

/// SCR-SETTINGS form over `GET /me`, saved with `PATCH /me` (docs/05 §3.2, AC-17).
final class SettingsController extends Notifier<SettingsForm> {
  @override
  SettingsForm build() => SettingsForm.of(ref.watch(meProvider).requireValue);

  void _edit(SettingsForm Function(SettingsForm form) change) {
    if (!state.isSaving) {
      state = change(state).copyWith(saveError: () => null);
    }
  }

  void setDisplayName(String value) => _edit((form) => form.copyWith(displayName: value));

  void setTimezone(String value) => _edit((form) => form.copyWith(timezone: value));

  void setDayStartHour(int value) => _edit((form) => form.copyWith(dayStartHour: value));

  void setWeekdayStudyMinutes(int value) =>
      _edit((form) => form.copyWith(weekdayStudyMinutes: value));

  void setWeekendStudyMinutes(int value) =>
      _edit((form) => form.copyWith(weekendStudyMinutes: value));

  Future<SettingsSaveOutcome> save() async {
    final form = state;
    if (form.isSaving || !form.isDirty) {
      return const SettingsSaved();
    }
    state = form.copyWith(isSaving: true, saveError: () => null);
    try {
      final saved = await ref.read(meRepositoryProvider).updateMe(form.toRequest());
      // Rebuilds this controller from the saved profile (the form becomes clean).
      ref.read(meProvider.notifier).replace(saved);
      return const SettingsSaved();
    } on ApiException catch (error) {
      if (error.code == ApiErrorCode.concurrentModification) {
        await ref.read(meProvider.notifier).refresh();
        if (ref.mounted) {
          state = SettingsForm.of(ref.read(meProvider).requireValue);
        }
        return const SettingsReloaded();
      }
      if (ref.mounted) {
        state = form.copyWith(
          isSaving: false,
          saveError: () => error.code == ApiErrorCode.validationFailed ? error : null,
        );
      }
      return SettingsSaveFailed(error);
    }
  }
}

final settingsControllerProvider = NotifierProvider.autoDispose<SettingsController, SettingsForm>(
  SettingsController.new,
);

/// Leaving the screen with edits asks first (docs/02 §6.8).
final settingsHasUnsavedChangesProvider = Provider.autoDispose<bool>((ref) {
  // Without a profile (signing out) there is no form to lose.
  if (ref.watch(meProvider).value == null) {
    return false;
  }
  return ref.watch(settingsControllerProvider).isDirty;
});
