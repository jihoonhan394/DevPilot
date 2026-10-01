import 'package:flutter/material.dart';

/// 2 px progress line shown while data that is already on screen is being re-read
/// (docs/02 §3.3 "Loading (재조회)"). Keeps its height when hidden so the layout does not jump.
class RefreshingBar extends StatelessWidget {
  const RefreshingBar({super.key, required this.visible});

  final bool visible;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 2,
      child: visible
          ? const SelectionContainer.disabled(child: LinearProgressIndicator(minHeight: 2))
          : null,
    );
  }
}
