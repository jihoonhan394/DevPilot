import 'package:devpilot_app/core/api/common_models.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'reading_models.freezed.dart';
part 'reading_models.g.dart';

// `GET /readings/{readingKey}` (docs/05 §19.7). One endpoint answers both kinds because
// `learning_task.reading_key` holds both: READ_CODE points at a repository file range, READING at
// an official document page. There is no body of either: the server never fetches the repository
// or the document URL.

/// Which kind of material the key points at (docs/05 §19.7). Clients read this instead of
/// guessing from the `READ.` / `DOC.` prefix.
enum ReadingKind {
  @JsonValue('CODE')
  code,
  @JsonValue('CONCEPT')
  concept,
}

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

/// `kind = CODE`: the file and line range to read in the user's own IDE (docs/19 §3.8).
@freezed
abstract class CodeReadingView with _$CodeReadingView {
  const CodeReadingView._();

  const factory CodeReadingView({
    required CuratedRepoView repo,
    required String path,
    required int startLine,
    required int endLine,
    required String question,
    @Default(<String>[]) List<String> lookFor,
  }) = _CodeReadingView;

  factory CodeReadingView.fromJson(Map<String, Object?> json) => _$CodeReadingViewFromJson(json);

  /// The file name at the end of [path], for the rubber duck target card.
  String get fileName => path.split('/').last;
}

/// `kind = CONCEPT`: the official document page a READING task points at (docs/19 §3.13). The
/// title stays as the document writes it — never translated or shortened (docs/02 SCR-TODAY).
@freezed
abstract class ConceptReadingView with _$ConceptReadingView {
  const factory ConceptReadingView({
    required String title,

    /// Opened in a new tab by the user's browser; neither app nor server fetches it.
    required String url,
    required String publisher,
    required String versionScope,
    required String whyRead,

    /// Exactly three, shown as "읽고 답할 3가지" (docs/06 §5.3). Nothing is sent back.
    @Default(<String>[]) List<String> checkPoints,

    /// The day a person last opened the link. Shown nowhere — content upkeep only (docs/19 §8.5).
    DateTime? verifiedAt,
  }) = _ConceptReadingView;

  factory ConceptReadingView.fromJson(Map<String, Object?> json) =>
      _$ConceptReadingViewFromJson(json);
}

@freezed
abstract class ReadingView with _$ReadingView {
  const ReadingView._();

  const factory ReadingView({
    required String key,
    required ReadingKind kind,
    @Default(<SkillRef>[]) List<SkillRef> skills,
    int? estimatedMinutes,

    /// No longer proposed; past tasks still resolve it (docs/19 §8.2).
    @Default(false) bool retired,

    /// Set when [kind] is [ReadingKind.code].
    CodeReadingView? code,

    /// Set when [kind] is [ReadingKind.concept].
    ConceptReadingView? concept,
  }) = _ReadingView;

  factory ReadingView.fromJson(Map<String, Object?> json) => _$ReadingViewFromJson(json);
}
