import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:flutter_test/flutter_test.dart';

/// One key per user action, kept for retries of the same body (docs/02 §6.6, docs/08 §8.4).
void main() {
  late int generated;
  late IdempotencyKeyCache cache;

  setUp(() {
    generated = 0;
    cache = IdempotencyKeyCache(generate: () => IdempotencyKey('key-${++generated}'));
  });

  test('shouldGenerateUuidV4ByDefault', () {
    final key = IdempotencyKey.generate();

    expect(
      key.value,
      matches(RegExp(r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$')),
    );
    expect(key.value, matches(RegExp(r'^[A-Za-z0-9_-]{8,100}$')));
  });

  test('shouldReuseKeyWhenSameBodyIsRetriedAfterNetworkFailure', () {
    final first = cache.keyFor({'name': '주문 시스템', 'stack': null});
    cache.settle(const ApiException(code: ApiErrorCode.networkError));

    expect(cache.keyFor({'stack': null, 'name': '주문 시스템'}), first);
  });

  test('shouldReuseKeyAfterServerErrorOrInProgress', () {
    final first = cache.keyFor({'a': 1});
    cache.settle(const ApiException(code: ApiErrorCode.internalError, status: 500));
    expect(cache.keyFor({'a': 1}), first);

    cache.settle(const ApiException(code: ApiErrorCode.idempotencyInProgress, status: 409));
    expect(cache.keyFor({'a': 1}), first);
  });

  test('shouldUseNewKeyWhenBodyChanges', () {
    final first = cache.keyFor({'name': 'a'});
    cache.settle(const ApiException(code: ApiErrorCode.clientTimeout));

    expect(cache.keyFor({'name': 'b'}), isNot(first));
  });

  test('shouldUseNewKeyForTheNextActionAfterSuccessOrClientError', () {
    final first = cache.keyFor({'a': 1});
    cache.settle(null);
    final second = cache.keyFor({'a': 1});
    expect(second, isNot(first));

    cache.settle(const ApiException(code: ApiErrorCode.validationFailed, status: 400));
    expect(cache.keyFor({'a': 1}), isNot(second));
  });

  test('shouldBuildKeySortedCompactJson', () {
    expect(
      IdempotencyKeyCache.canonicalJson({
        'b': [
          {'y': 1, 'x': 2},
        ],
        'a': null,
      }),
      '{"a":null,"b":[{"x":2,"y":1}]}',
    );
  });
}
