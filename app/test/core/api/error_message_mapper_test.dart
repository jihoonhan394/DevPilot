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
      code: 'TODAY_ALREADY_STARTED',
      status: 409,
      detail: '오늘 과제를 이미 시작했습니다.',
    );

    expect(messageFor(error, l10n), '오늘 과제를 이미 시작했습니다.');
  });

  test('shouldUseGenericMessageWhenNeitherCodeNorDetailIsKnown', () {
    expect(messageFor(const ApiException(code: 'SOMETHING_NEW'), l10n), l10n.errorGeneric);
    expect(messageFor(StateError('not an api error'), l10n), l10n.errorGeneric);
  });
}
