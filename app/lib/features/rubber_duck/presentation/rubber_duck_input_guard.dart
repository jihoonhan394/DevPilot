import 'package:flutter_riverpod/flutter_riverpod.dart';

/// True while SCR-RUBBER-DUCK holds an explanation that was not sent (docs/02 §6.8). One rubber
/// duck screen is open at a time.
final class RubberDuckInputGuard extends Notifier<bool> {
  @override
  bool build() => false;

  void set({required bool dirty}) {
    if (state != dirty) {
      state = dirty;
    }
  }
}

final rubberDuckInputGuardProvider = NotifierProvider<RubberDuckInputGuard, bool>(
  RubberDuckInputGuard.new,
);
