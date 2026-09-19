import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// Opens the Material date picker limited to [firstDate]..[lastDate]. Null when cancelled.
Future<LocalDate?> pickLocalDate(
  BuildContext context, {
  required LocalDate? initialDate,
  required LocalDate firstDate,
  required LocalDate lastDate,
  String? helpText,
}) async {
  var initial = initialDate ?? firstDate;
  if (initial.isBefore(firstDate)) {
    initial = firstDate;
  } else if (initial.isAfter(lastDate)) {
    initial = lastDate;
  }
  final picked = await showDatePicker(
    context: context,
    initialDate: _toLocal(initial),
    firstDate: _toLocal(firstDate),
    lastDate: _toLocal(lastDate),
    helpText: helpText,
  );
  return picked == null ? null : LocalDate(picked.year, picked.month, picked.day);
}

// The picker works on local midnight DateTimes; only the date fields are read back.
DateTime _toLocal(LocalDate date) => DateTime(date.year, date.month, date.day);

/// A labelled field that opens the date picker for a plan date.
///
/// The picker is limited to [firstDate]..[lastDate] (the §3.2 range), so most invalid dates cannot
/// be chosen at all; [errorText] covers the rest (for example start after end).
class DateField extends StatelessWidget {
  const DateField({
    super.key,
    required this.label,
    required this.value,
    required this.firstDate,
    required this.lastDate,
    required this.onChanged,
    this.errorText,
    this.enabled = true,
  });

  final String label;
  final LocalDate? value;
  final LocalDate firstDate;
  final LocalDate lastDate;
  final ValueChanged<LocalDate> onChanged;
  final String? errorText;
  final bool enabled;

  Future<void> _pick(BuildContext context) async {
    final picked = await pickLocalDate(
      context,
      initialDate: value,
      firstDate: firstDate,
      lastDate: lastDate,
      helpText: label,
    );
    if (picked != null) {
      onChanged(picked);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final current = value;
    final text = current == null ? l10n.commonDateNotSet : formatLongDate(current, l10n);
    return Semantics(
      button: true,
      enabled: enabled,
      label: '$label, $text',
      excludeSemantics: true,
      onTap: enabled ? () => _pick(context) : null,
      child: InkWell(
        onTap: enabled ? () => _pick(context) : null,
        borderRadius: const BorderRadius.all(Radius.circular(8)),
        child: InputDecorator(
          decoration: InputDecoration(
            labelText: label,
            errorText: errorText,
            enabled: enabled,
            suffixIcon: const Icon(Icons.calendar_today_outlined),
          ),
          child: Text(text),
        ),
      ),
    );
  }
}
