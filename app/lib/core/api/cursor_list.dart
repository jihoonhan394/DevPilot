import 'package:devpilot_app/core/api/cursor_page.dart';
import 'package:flutter/foundation.dart';

/// Pages of a cursor list loaded so far (docs/05 §1.5). The cursor is opaque to the client.
@immutable
final class CursorList<T> {
  const CursorList({
    required this.items,
    required this.nextCursor,
    this.isLoadingMore = false,
    this.loadMoreError,
  });

  factory CursorList.firstPage(CursorPage<T> page) =>
      CursorList(items: page.items, nextCursor: page.nextCursor);

  final List<T> items;

  /// Null on the last page.
  final String? nextCursor;
  final bool isLoadingMore;

  /// Failure of the last "load more"; the list shows a "다시 불러오기" row (docs/02 SCR-PROJECTS).
  final Object? loadMoreError;

  bool get hasMore => nextCursor != null;

  CursorList<T> loadingMore() =>
      CursorList(items: items, nextCursor: nextCursor, isLoadingMore: true);

  CursorList<T> append(CursorPage<T> page) =>
      CursorList(items: [...items, ...page.items], nextCursor: page.nextCursor);

  CursorList<T> failedToLoadMore(Object error) =>
      CursorList(items: items, nextCursor: nextCursor, loadMoreError: error);

  CursorList<T> withItems(List<T> newItems) =>
      CursorList(items: newItems, nextCursor: nextCursor, loadMoreError: loadMoreError);
}
