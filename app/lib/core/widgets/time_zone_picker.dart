import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/form_modal.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// Searchable IANA time zone list (docs/02 `TimezonePicker`). Returns the chosen zone, or null.
Future<String?> showTimeZonePicker(
  BuildContext context, {
  required List<String> zones,
  required String current,
}) => showFormModal<String>(
  context,
  builder: (_) => _TimeZonePicker(zones: zones, current: current),
);

class _TimeZonePicker extends StatefulWidget {
  const _TimeZonePicker({required this.zones, required this.current});

  final List<String> zones;
  final String current;

  @override
  State<_TimeZonePicker> createState() => _TimeZonePickerState();
}

class _TimeZonePickerState extends State<_TimeZonePicker> {
  String _query = '';

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final query = _query.trim().toLowerCase();
    final zones = [
      for (final zone in widget.zones)
        if (query.isEmpty || zone.toLowerCase().contains(query)) zone,
    ];
    return SizedBox(
      height: MediaQuery.sizeOf(context).height * 0.75,
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(l10n.commonTimeZone, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: AppSpacing.sm),
            TextField(
              key: const Key('timeZonePicker.searchField'),
              decoration: InputDecoration(
                labelText: l10n.commonSearch,
                prefixIcon: const Icon(Icons.search),
              ),
              onChanged: (value) => setState(() => _query = value),
            ),
            Expanded(
              child: ListView.builder(
                itemCount: zones.length,
                itemBuilder: (context, index) {
                  final zone = zones[index];
                  return ListTile(
                    key: Key('timeZonePicker.option.$zone'),
                    title: Text(zone),
                    selected: zone == widget.current,
                    trailing: zone == widget.current ? const Icon(Icons.check) : null,
                    onTap: () => Navigator.of(context).pop(zone),
                  );
                },
              ),
            ),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton(
                onPressed: () => Navigator.of(context).pop(),
                child: Text(l10n.commonCancel),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
