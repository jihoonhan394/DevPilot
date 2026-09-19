import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Inputs of SCR-TRAINING-ATTEMPT that may hold unsent text (docs/02 §6.8).
enum AttemptInput { explanation, submission }

/// Which attempt inputs hold text that was not sent yet. The route asks before leaving while any
/// is set (docs/02 §6.8 `UnsavedChangesGuard`). One attempt screen is open at a time.
final class AttemptInputGuard extends Notifier<Set<AttemptInput>> {
  @override
  Set<AttemptInput> build() => const {};

  void mark(AttemptInput input, {required bool dirty}) {
    if (dirty == state.contains(input)) {
      return;
    }
    state = dirty ? {...state, input} : ({...state}..remove(input));
  }

  void clear() => state = const {};
}

final attemptInputGuardProvider = NotifierProvider<AttemptInputGuard, Set<AttemptInput>>(
  AttemptInputGuard.new,
);

/// True while the attempt screen has unsent input.
final attemptHasUnsavedInputProvider = Provider<bool>(
  (ref) => ref.watch(attemptInputGuardProvider).isNotEmpty,
);
