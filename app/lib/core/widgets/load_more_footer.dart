import 'package:devpilot_app/core/api/error_message_mapper.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// End of a cursor list: requests the next page when it scrolls into view, shows progress while
/// loading, and a "다시 불러오기" row after a failure (docs/02 §3.3, SCR-PROJECTS).
class LoadMoreFooter extends StatefulWidget {
  const LoadMoreFooter({
    super.key,
    required this.hasMore,
    required this.isLoading,
    required this.error,
    required this.onLoadMore,
  });

  final bool hasMore;
  final bool isLoading;
  final Object? error;
  final VoidCallback onLoadMore;

  @override
  State<LoadMoreFooter> createState() => _LoadMoreFooterState();
}

class _LoadMoreFooterState extends State<LoadMoreFooter> {
  @override
  void initState() {
    super.initState();
    _requestIfNeeded();
  }

  @override
  void didUpdateWidget(LoadMoreFooter oldWidget) {
    super.didUpdateWidget(oldWidget);
    _requestIfNeeded();
  }

  void _requestIfNeeded() {
    if (widget.hasMore && !widget.isLoading && widget.error == null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          widget.onLoadMore();
        }
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final error = widget.error;
    if (error != null) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: AppSpacing.md),
        child: Column(
          children: [
            Text(messageFor(error, l10n), textAlign: TextAlign.center),
            TextButton(
              key: const Key('common.loadMoreRetryButton'),
              onPressed: widget.onLoadMore,
              child: Text(l10n.commonLoadMoreRetry),
            ),
          ],
        ),
      );
    }
    if (widget.hasMore) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: AppSpacing.md),
        child: Center(child: CircularProgressIndicator()),
      );
    }
    return const SizedBox.shrink();
  }
}
