import 'package:flutter/material.dart';

/// A dropdown with a visible label, reachable by keyboard and screen readers.
///
/// Wraps [DropdownButton] (whose `value` follows the state) instead of the form-field variant
/// whose `value` is deprecated in favour of an initial value only.
class LabeledDropdown<T> extends StatelessWidget {
  const LabeledDropdown({
    super.key,
    required this.label,
    required this.value,
    required this.items,
    required this.itemLabel,
    required this.onChanged,
    this.errorText,
  });

  final String label;
  final T value;
  final List<T> items;
  final String Function(T item) itemLabel;

  /// Null disables the dropdown.
  final ValueChanged<T>? onChanged;
  final String? errorText;

  @override
  Widget build(BuildContext context) {
    final change = onChanged;
    return InputDecorator(
      decoration: InputDecoration(
        labelText: label,
        errorText: errorText,
        contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      ),
      child: DropdownButtonHideUnderline(
        child: DropdownButton<T>(
          value: value,
          isExpanded: true,
          items: [
            for (final item in items)
              DropdownMenuItem<T>(value: item, child: Text(itemLabel(item))),
          ],
          onChanged: change == null
              ? null
              : (selected) {
                  if (selected != null) {
                    change(selected);
                  }
                },
        ),
      ),
    );
  }
}
