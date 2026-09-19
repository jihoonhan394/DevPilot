import 'package:devpilot_app/core/api/common_models.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'reading_models.freezed.dart';
part 'reading_models.g.dart';

// `GET /readings/{readingKey}` (docs/05 §19.7). There is no code body: the server never fetches
// the repository, the user reads it locally from `cloneHint` at `pinnedCommit`.

@freezed
abstract class CuratedRepoView with _$CuratedRepoView {
  const CuratedRepoView._();

  const factory CuratedRepoView({
    required String key,
    required String name,

    /// Opened by the user's browser only; the server never requests it.
    required String url,

    /// Folder inside the repository the path is relative to; "" when none.
    @Default('') String subPath,

    /// "UNSPECIFIED" when the repository states no license: read only, no copying.
    required String license,
    String? licenseNote,
    String? stack,
    String? why,
    required String cloneHint,
    String? pinnedCommit,
  }) = _CuratedRepoView;

  factory CuratedRepoView.fromJson(Map<String, Object?> json) => _$CuratedRepoViewFromJson(json);

  static const unspecifiedLicense = 'UNSPECIFIED';

  bool get readOnly => license == unspecifiedLicense;
}

@freezed
abstract class CuratedReadingView with _$CuratedReadingView {
  const CuratedReadingView._();

  const factory CuratedReadingView({
    required String key,
    required CuratedRepoView repo,
    required String path,
    required int startLine,
    required int endLine,
    @Default(<SkillRef>[]) List<SkillRef> skills,
    int? estimatedMinutes,
    required String question,
    @Default(<String>[]) List<String> lookFor,

    /// No longer proposed; the coordinates stay valid at `pinnedCommit`.
    @Default(false) bool retired,
  }) = _CuratedReadingView;

  factory CuratedReadingView.fromJson(Map<String, Object?> json) =>
      _$CuratedReadingViewFromJson(json);

  /// The file name at the end of [path], for the rubber duck target card.
  String get fileName => path.split('/').last;
}
