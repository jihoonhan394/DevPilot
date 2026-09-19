import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/onboarding/data/onboarding_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// `POST /onboarding` (docs/05 §4.1). Throws `ApiException`.
abstract interface class OnboardingRepository {
  Future<OnboardingResponse> completeOnboarding(
    OnboardingRequest request, {
    required IdempotencyKey idempotencyKey,
  });
}

final class ApiOnboardingRepository implements OnboardingRepository {
  ApiOnboardingRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<OnboardingResponse> completeOnboarding(
    OnboardingRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async => OnboardingResponse.fromJson(
    await _apiClient.postJson(
      '/onboarding',
      body: request.toJson(),
      idempotencyKey: idempotencyKey,
    ),
  );
}

final onboardingRepositoryProvider = Provider<OnboardingRepository>(
  (ref) => ApiOnboardingRepository(ref.watch(apiClientProvider)),
);
