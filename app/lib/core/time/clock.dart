import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Current time source. Tests override it; code never calls `DateTime.now()` directly.
///
/// Dates the user sees come from the server's plan-day (`GET /me.today`), not from this clock.
/// It only stamps client-side events such as the time shown in an error report.
final clockProvider = Provider<DateTime Function()>((ref) => DateTime.now);
