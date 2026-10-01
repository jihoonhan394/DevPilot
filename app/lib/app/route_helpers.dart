import 'dart:async';

import 'package:devpilot_app/app/not_found_screen.dart';
import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/auth/auth_controller.dart';
import 'package:devpilot_app/core/widgets/confirm_dialog.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart';
import 'package:go_router/go_router.dart';

/// Path parameters are UUIDs; anything else shows SCR-NOT-FOUND (docs/02 §2.3).
final _uuidPattern = RegExp(
  r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
);

/// `/rubber-duck/new?targetType=&targetId=|conceptKey=&skillCode=&taskId=` (docs/02 §2.3). A
/// missing or unknown target type, or a target that does not fit it, is SCR-NOT-FOUND.
Widget rubberDuckStartScreen(GoRouterState state) {
  final query = state.uri.queryParameters;
  final type = RubberDuckTargetType.fromWire(query[AppRoutes.targetTypeParameter]);
  final targetId = uuidOrNull(query[AppRoutes.targetIdParameter]);
  final conceptKey = query[AppRoutes.conceptKeyParameter];
  final concept = type == RubberDuckTargetType.concept;
  if (type == null || (concept ? conceptKey == null : targetId == null)) {
    return const NotFoundScreen();
  }
  final extra = state.extra;
  return RubberDuckScreen(
    route: (
      sessionId: null,
      targetType: type,
      targetId: concept ? null : targetId,
      conceptKey: concept ? conceptKey : null,
      skillCode: query[AppRoutes.skillCodeParameter],
    ),
    taskId: uuidOrNull(query[AppRoutes.taskIdParameter]),
    preview: extra is RubberDuckTargetPreview ? extra : null,
  );
}

/// A query value that must be a UUID (`taskId`, `skillId`); anything else is ignored.
String? uuidOrNull(String? value) => value != null && _uuidPattern.hasMatch(value) ? value : null;

Widget withUuid(GoRouterState state, String name, Widget Function(String id) build) {
  final value = state.pathParameters[name];
  return value != null && _uuidPattern.hasMatch(value) ? build(value) : const NotFoundScreen();
}

/// Leaves at once when nothing is dirty, otherwise asks first (docs/02 §6.8). A sign-out or an
/// expired session always leaves: the screen cannot save without a session.
FutureOr<bool> confirmLeave(BuildContext context, ProviderListenable<bool> hasUnsavedChanges) {
  final container = ProviderScope.containerOf(context, listen: false);
  if (container.read(authStateProvider) is SignedOut || !container.read(hasUnsavedChanges)) {
    return true;
  }
  return confirmDiscardChanges(context);
}
