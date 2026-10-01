import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/error_message_mapper.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final l10n = lookupAppLocalizations(const Locale('ko'));

  test('shouldUseArbMessageWhenCodeIsMapped', () {
    const notAllowed = ApiException(
      code: ApiErrorCode.userNotAllowed,
      status: 403,
      detail: '서버 문구',
    );
    const conflict = ApiException(code: ApiErrorCode.concurrentModification, status: 409);

    // docs/02 §5.1 error.<CODE> copy wins over the server detail.
    expect(messageFor(notAllowed, l10n), '초대된 계정이 아니에요.');
    expect(messageFor(conflict, l10n), '다른 곳에서 먼저 바뀌었어요. 최신 내용으로 다시 불러왔어요.');
    expect(
      messageFor(const ApiException(code: ApiErrorCode.clientTimeout), l10n),
      l10n.errorNetworkError,
    );
  });

  test('shouldUseServerDetailWhenCodeIsNotMapped', () {
    const error = ApiException(
      code: 'SUBMISSION_LIMIT_REACHED',
      status: 409,
      detail: '이 풀이에서 제출 5회를 모두 사용했어요.',
    );

    expect(messageFor(error, l10n), '이 풀이에서 제출 5회를 모두 사용했어요.');
  });

  test('shouldUseGenericMessageWhenNeitherCodeNorDetailIsKnown', () {
    expect(messageFor(const ApiException(code: 'SOMETHING_NEW'), l10n), l10n.errorGeneric);
    expect(messageFor(StateError('not an api error'), l10n), l10n.errorGeneric);
  });
}
