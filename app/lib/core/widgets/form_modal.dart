import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:flutter/material.dart';

/// Input form modal: a bottom sheet below 600 px, a dialog of at most 480 px above (docs/02 §6.7).
Future<T?> showFormModal<T>(BuildContext context, {required WidgetBuilder builder}) {
  if (MediaQuery.sizeOf(context).width < AppBreakpoints.tablet) {
    return showModalBottomSheet<T>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (sheetContext) => Padding(
        // Keeps the form above the on-screen keyboard.
        padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(sheetContext).bottom),
        child: builder(sheetContext),
      ),
    );
  }
  return showDialog<T>(
    context: context,
    builder: (dialogContext) => Dialog(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 480),
        child: builder(dialogContext),
      ),
    ),
  );
}
