import 'package:freezed_annotation/freezed_annotation.dart';

part 'cursor_page.freezed.dart';
part 'cursor_page.g.dart';

/// `CursorPage<T>` of every list endpoint (docs/05 §1.5, §2.1). The cursor is opaque.
@Freezed(genericArgumentFactories: true)
abstract class CursorPage<T> with _$CursorPage<T> {
  const factory CursorPage({required List<T> items, String? nextCursor}) = _CursorPage<T>;

  factory CursorPage.fromJson(Map<String, Object?> json, T Function(Object? json) fromJsonT) =>
      _$CursorPageFromJson(json, fromJsonT);
}
