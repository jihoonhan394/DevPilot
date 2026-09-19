import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/problem_details_interceptor.dart';
import 'package:dio/dio.dart';

/// Sends `Authorization: Bearer <token>` and reacts to session-level failures.
///
/// devtoken mode has no refresh token, so a 401 goes straight to the login screen (docs/07 §3.4).
/// `USER_NOT_ALLOWED`, `FORBIDDEN` and `ONBOARDING_REQUIRED` mean the user state changed on the
/// server; the profile is read again so the router can redirect (docs/02 §2.4, §5.1).
final class AuthTokenInterceptor extends Interceptor {
  AuthTokenInterceptor({
    required this._readAccessToken,
    required this._onSessionExpired,
    required this._onUserStateChanged,
  });

  /// `RequestOptions.extra` flag for endpoints outside authentication (`POST /dev/token`).
  static const skipAuthKey = 'devpilot.skipAuth';

  static const _userStateCodes = {
    ApiErrorCode.userNotAllowed,
    ApiErrorCode.forbidden,
    ApiErrorCode.onboardingRequired,
  };

  final String? Function() _readAccessToken;
  final void Function() _onSessionExpired;
  final void Function() _onUserStateChanged;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    if (_skipsAuth(options)) {
      handler.next(options);
      return;
    }
    final accessToken = _readAccessToken();
    if (accessToken == null) {
      // Without a session the request would only produce a 401, so it is not sent at all.
      handler.reject(
        DioException(
          requestOptions: options,
          error: const ApiException(code: ApiErrorCode.authenticationRequired),
        ),
      );
      return;
    }
    options.headers['Authorization'] = 'Bearer $accessToken';
    handler.next(options);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    if (!_skipsAuth(err.requestOptions) && err.response != null) {
      final code = ProblemDetailsInterceptor.toApiException(err).code;
      if (code == ApiErrorCode.authenticationRequired) {
        _onSessionExpired();
      } else if (_userStateCodes.contains(code)) {
        _onUserStateChanged();
      }
    }
    handler.next(err);
  }

  static bool _skipsAuth(RequestOptions options) {
    final Object? flag = options.extra[skipAuthKey];
    return flag == true;
  }
}
