import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/domain/review_card_progress.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Desktop review shortcuts while no text field has focus (docs/02 A-8): `Space` reveals, `H`
/// shows the hint, `1`~`4` rate after revealing.
class ReviewShortcuts extends StatelessWidget {
  const ReviewShortcuts({
    super.key,
    required this.card,
    required this.enabled,
    required this.onHint,
    required this.onReveal,
    required this.onRate,
    required this.child,
  });

  final ReviewCardProgress card;
  final bool enabled;
  final VoidCallback onHint;
  final VoidCallback onReveal;
  final ValueChanged<ReviewRating> onRate;
  final Widget child;

  static final _ratingKeys = {
    LogicalKeyboardKey.digit1: ReviewRating.again,
    LogicalKeyboardKey.digit2: ReviewRating.hard,
    LogicalKeyboardKey.digit3: ReviewRating.good,
    LogicalKeyboardKey.digit4: ReviewRating.easy,
  };

  KeyEventResult _onKey(FocusNode node, KeyEvent event) {
    if (event is! KeyDownEvent || !enabled || _isTyping()) {
      return KeyEventResult.ignored;
    }
    final key = event.logicalKey;
    if (!card.revealed) {
      if (key == LogicalKeyboardKey.space) {
        onReveal();
        return KeyEventResult.handled;
      }
      if (key == LogicalKeyboardKey.keyH && card.canShowHint) {
        onHint();
        return KeyEventResult.handled;
      }
      return KeyEventResult.ignored;
    }
    final rating = _ratingKeys[key];
    if (rating == null) {
      return KeyEventResult.ignored;
    }
    onRate(rating);
    return KeyEventResult.handled;
  }

  /// Keys typed into the answer field belong to the field.
  static bool _isTyping() {
    final focused = FocusManager.instance.primaryFocus?.context;
    return focused != null &&
        (focused.widget is EditableText ||
            focused.findAncestorWidgetOfExactType<EditableText>() != null);
  }

  @override
  Widget build(BuildContext context) {
    return Focus(autofocus: true, onKeyEvent: _onKey, child: child);
  }
}
