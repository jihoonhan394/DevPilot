import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/auth/auth_controller.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/time/time_zone_support.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/core/widgets/action_error.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/confirm_dialog.dart';
import 'package:devpilot_app/core/widgets/install_guide_sheet.dart';
import 'package:devpilot_app/core/widgets/labeled_dropdown.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/time_zone_picker.dart';
import 'package:devpilot_app/features/plan/data/learning_goal_models.dart';
import 'package:devpilot_app/features/plan/data/learning_goal_repository.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/features/settings/presentation/settings_controller.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// Goal summary line of SCR-SETTINGS; a failure only hides the line (docs/02 SCR-SETTINGS "데이터").
final settingsGoalProvider = FutureProvider.autoDispose<LearningGoalView>(
  (ref) => ref.watch(learningGoalRepositoryProvider).fetchGoal(),
);

const _minuteChoices = [0, 15, 30, 45, 60, 90, 120, 180, 240, 300, 360, 480, 600, 720];
const _dayStartHours = [0, 1, 2, 3, 4, 5, 6];

/// SCR-SETTINGS: display name, goal link, study time, day start and time zone, app install guide,
/// sign-out (docs/02 §3.14). AI usage (S3), calendar (S5) and data/delete (S6) come later.
class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  Future<void> _save(BuildContext context, WidgetRef ref) async {
    final l10n = AppLocalizations.of(context);
    final controller = ref.read(settingsControllerProvider.notifier);
    if (ref.read(settingsControllerProvider).changesDayBoundary) {
      final confirmed = await showConfirmDialog(
        context,
        title: l10n.settingsDayBoundaryConfirmTitle,
        body: l10n.settingsDayBoundaryConfirmBody,
        cancelLabel: l10n.commonCancel,
        confirmLabel: l10n.settingsDayBoundaryConfirmChange,
        confirmKey: const Key('settings.dayBoundaryConfirmButton'),
      );
      if (!confirmed) {
        return;
      }
    }
    final outcome = await controller.save();
    if (!context.mounted) {
      return;
    }
    switch (outcome) {
      case SettingsSaved():
        showToast(context, l10n.settingsSaved);
      case SettingsReloaded():
        showToast(context, l10n.errorConcurrentModification);
      case SettingsSaveFailed(:final error):
        await presentActionError(context, ref, error);
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    if (ref.watch(meProvider).value == null) {
      // Signing out: the router is about to leave this screen.
      return const Scaffold();
    }
    final form = ref.watch(settingsControllerProvider);
    return Scaffold(
      appBar: AppBar(title: Semantics(header: true, child: Text(l10n.settingsTitle))),
      body: ScreenBody(
        maxWidth: AppBreakpoints.formCardWidth,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SectionTitle(l10n.settingsProfile),
            const SizedBox(height: AppSpacing.sm),
            _DisplayNameField(form: form),
            if (form.me.onboardingCompleted) const _GoalTile(),
            const SizedBox(height: AppSpacing.section),
            SectionTitle(l10n.settingsStudy),
            const SizedBox(height: AppSpacing.sm),
            _StudyTimeFields(form: form),
            const SizedBox(height: AppSpacing.xl),
            FilledButton(
              key: const Key('settings.saveButton'),
              onPressed: form.isDirty && form.isValid && !form.isSaving
                  ? () => _save(context, ref)
                  : null,
              child: Text(l10n.settingsSave),
            ),
            const SizedBox(height: AppSpacing.section),
            const Divider(),
            const InstallSettingsSection(),
            const Divider(),
            ListTile(
              key: const Key('settings.logoutButton'),
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.logout),
              title: Text(l10n.settingsLogout),
              onTap: () => ref.read(authStateProvider.notifier).signOut(),
            ),
          ],
        ),
      ),
    );
  }
}

class _DisplayNameField extends ConsumerStatefulWidget {
  const _DisplayNameField({required this.form});

  final SettingsForm form;

  @override
  ConsumerState<_DisplayNameField> createState() => _DisplayNameFieldState();
}

class _DisplayNameFieldState extends ConsumerState<_DisplayNameField> {
  late final _controller = TextEditingController(text: widget.form.displayName);

  @override
  void didUpdateWidget(_DisplayNameField oldWidget) {
    super.didUpdateWidget(oldWidget);
    // A save or a reload replaces the form; show its value unless the user is typing it.
    if (widget.form.displayName != _controller.text) {
      _controller.text = widget.form.displayName;
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final name = widget.form.displayName;
    final valid = InputRules.isValidDisplayName(name);
    return TextField(
      key: const Key('settings.displayNameField'),
      controller: _controller,
      enabled: !widget.form.isSaving,
      maxLength: InputRules.displayNameMaxLength,
      decoration: InputDecoration(
        labelText: l10n.settingsDisplayName,
        errorText: valid
            ? widget.form.saveError?.fieldMessage(
                'displayName',
                fallback: l10n.errorValidationFailed,
              )
            : (name.trim().isEmpty
                  ? l10n.validationRequired
                  : l10n.validationMaxLength(InputRules.displayNameMaxLength)),
      ),
      onChanged: ref.read(settingsControllerProvider.notifier).setDisplayName,
    );
  }
}

class _GoalTile extends ConsumerWidget {
  const _GoalTile();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final goal = ref.watch(settingsGoalProvider).value;
    final completion = LocalDate.tryParse(goal?.targetCompletionDate);
    final checkpoint = LocalDate.tryParse(goal?.checkpointDate);
    final summary = completion == null
        ? null
        : checkpoint == null
        ? l10n.settingsGoalSummaryNoCheckpoint(formatLongDate(completion, l10n))
        : l10n.settingsGoalSummary(
            formatLongDate(completion, l10n),
            formatLongDate(checkpoint, l10n),
          );
    return ListTile(
      key: const Key('settings.goalTile'),
      contentPadding: EdgeInsets.zero,
      title: Text(l10n.settingsGoal),
      subtitle: summary == null ? null : Text(summary),
      trailing: const Icon(Icons.chevron_right),
      onTap: () => context.go(AppRoutes.learningGoal),
    );
  }
}

class _StudyTimeFields extends ConsumerWidget {
  const _StudyTimeFields({required this.form});

  final SettingsForm form;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final controller = ref.read(settingsControllerProvider.notifier);
    final enabled = !form.isSaving;
    List<int> choices(int current) => ({..._minuteChoices, current}.toList()..sort());
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        LabeledDropdown<int>(
          key: const Key('settings.weekdayDropdown'),
          label: l10n.settingsWeekday,
          value: form.weekdayStudyMinutes,
          items: choices(form.weekdayStudyMinutes),
          itemLabel: (minutes) => formatMinutes(minutes, l10n),
          onChanged: enabled ? controller.setWeekdayStudyMinutes : null,
        ),
        const SizedBox(height: AppSpacing.md),
        LabeledDropdown<int>(
          key: const Key('settings.weekendDropdown'),
          label: l10n.settingsWeekend,
          value: form.weekendStudyMinutes,
          items: choices(form.weekendStudyMinutes),
          itemLabel: (minutes) => formatMinutes(minutes, l10n),
          onChanged: enabled ? controller.setWeekendStudyMinutes : null,
        ),
        const SizedBox(height: AppSpacing.md),
        LabeledDropdown<int>(
          key: const Key('settings.dayStartDropdown'),
          label: l10n.settingsDayStart,
          value: form.dayStartHour,
          items: _dayStartHours,
          itemLabel: (hour) => formatDayStartHour(hour, l10n),
          onChanged: enabled ? controller.setDayStartHour : null,
        ),
        const SizedBox(height: AppSpacing.xs),
        Text(
          l10n.settingsDayStartHelp(formatDayStartHour(form.dayStartHour, l10n)),
          key: const Key('settings.dayStartHelp'),
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: AppSpacing.md),
        _TimeZoneRow(form: form, enabled: enabled),
      ],
    );
  }
}

class _TimeZoneRow extends ConsumerWidget {
  const _TimeZoneRow({required this.form, required this.enabled});

  final SettingsForm form;
  final bool enabled;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final error = form.saveError?.fieldMessage('timezone', fallback: l10n.errorValidationFailed);
    return InputDecorator(
      decoration: InputDecoration(labelText: l10n.settingsTimezone, errorText: error),
      child: Row(
        children: [
          Expanded(child: Text(form.timezone, key: const Key('settings.timezoneValue'))),
          TextButton(
            key: const Key('settings.timezoneChangeButton'),
            onPressed: enabled
                ? () async {
                    final zone = await showTimeZonePicker(
                      context,
                      zones: ref.read(timeZoneSupportProvider).availableTimeZones(),
                      current: form.timezone,
                    );
                    if (zone != null) {
                      ref.read(settingsControllerProvider.notifier).setTimezone(zone);
                    }
                  }
                : null,
            child: Text(l10n.onboardingTimeTimezoneChange),
          ),
        ],
      ),
    );
  }
}
