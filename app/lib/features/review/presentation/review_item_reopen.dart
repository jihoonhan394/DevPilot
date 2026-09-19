import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

/// `/review/items/:reviewItemId` without the card as `extra` (a reload or a typed URL): there is
/// no single-card API, so the list opens again with `review.edit.reopen` (docs/02 SCR-REVIEW-ITEM-EDIT).
class ReviewItemReopen extends StatefulWidget {
  const ReviewItemReopen({super.key});

  @override
  State<ReviewItemReopen> createState() => _ReviewItemReopenState();
}

class _ReviewItemReopenState extends State<ReviewItemReopen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) {
        return;
      }
      context.go(AppRoutes.reviewItems);
      showToast(context, AppLocalizations.of(context).reviewEditReopen);
    });
  }

  @override
  Widget build(BuildContext context) => const Scaffold();
}
