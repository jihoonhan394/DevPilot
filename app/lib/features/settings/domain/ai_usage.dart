/// Month cost versus budget of `GET /me.aiUsage` (docs/05 §1.9.1), in whole cents so the display
/// needs no floating point. Money arrives as 2-decimal USD strings ("12.40").
final class AiMonthUsage {
  const AiMonthUsage({required this.costCents, required this.budgetCents});

  /// Null when either string is not a USD amount.
  static AiMonthUsage? parse({required String cost, required String budget}) {
    final costCents = _cents(cost);
    final budgetCents = _cents(budget);
    if (costCents == null || budgetCents == null) {
      return null;
    }
    return AiMonthUsage(costCents: costCents, budgetCents: budgetCents);
  }

  final int costCents;
  final int budgetCents;

  /// `floor(cost × 100 ÷ budget)`, 0 for a zero budget.
  int get percent => budgetCents <= 0 ? 0 : costCents * 100 ~/ budgetCents;

  /// Share of the bar, clamped to 0..1.
  double get fraction => budgetCents <= 0 ? 0 : (costCents / budgetCents).clamp(0, 1).toDouble();

  static final _amount = RegExp(r'^(\d+)(?:\.(\d{1,2}))?$');

  static int? _cents(String value) {
    final match = _amount.firstMatch(value.trim());
    if (match == null) {
      return null;
    }
    final dollars = int.parse(match.group(1)!);
    final fraction = (match.group(2) ?? '0').padRight(2, '0');
    return dollars * 100 + int.parse(fraction);
  }
}
