import 'package:devpilot_app/app/session_redirect.dart';
import 'package:devpilot_app/core/auth/auth_controller.dart';
import 'package:flutter_test/flutter_test.dart';

/// Redirect rules of docs/02 §2.4 as a pure function.
void main() {
  const signedIn = SignedIn('access-token');
  const signedOut = SignedOut();
  const onboarded = ProfileReady(onboardingCompleted: true, deletionRequested: false);
  const notOnboarded = ProfileReady(onboardingCompleted: false, deletionRequested: false);

  String? redirect(AuthState auth, String location, {ProfileStatus profile = onboarded}) =>
      redirectFor(auth: auth, profile: profile, location: Uri.parse(location));

  group('rule 1: no session', () {
    test('shouldSendSignedOutUserToLoginWhenRouteRequiresAuth', () {
      expect(redirect(signedOut, '/'), '/login');
      expect(redirect(signedOut, '/plan'), '/login?from=%2Fplan');
      expect(redirect(signedOut, '/settings'), '/login?from=%2Fsettings');
      expect(redirect(signedOut, '/onboarding/goal'), '/login?from=%2Fonboarding%2Fgoal');
    });

    test('shouldAddReasonWhenSessionExpiredOrAccountWasDeleted', () {
      expect(
        redirect(const SignedOut(reason: SignOutReason.expired), '/'),
        '/login?reason=expired',
      );
      expect(
        redirect(const SignedOut(reason: SignOutReason.deleted), '/projects'),
        '/login?from=%2Fprojects&reason=deleted',
      );
    });

    test('shouldStayWhenSignedOutUserIsOnPublicOrUnknownRoute', () {
      expect(redirect(signedOut, '/login'), isNull);
      expect(redirect(signedOut, '/not-allowed'), isNull);
      expect(redirect(signedOut, '/does-not-exist'), isNull);
    });
  });

  group('rule 2: session on /login', () {
    test('shouldLeaveLoginForStartPageWhenSignedIn', () {
      expect(redirect(signedIn, '/login'), '/plan');
      expect(redirect(signedIn, '/login?reason=expired'), '/plan');
    });

    test('shouldReturnToInAppFromPathOnly', () {
      expect(redirect(signedIn, '/login?from=%2Fprojects%3Ftab%3D1'), '/projects?tab=1');
      expect(redirect(signedIn, '/login?from=%2F%2Fevil.example'), '/plan');
      expect(redirect(signedIn, '/login?from=https%3A%2F%2Fevil.example'), '/plan');
      expect(redirect(signedIn, '/login?from=%2Flogin'), '/plan');
    });
  });

  test('rule 3: shouldStayWhileProfileIsLoadingOrFailed', () {
    expect(redirect(signedIn, '/plan', profile: const ProfileLoading()), isNull);
    expect(redirect(signedIn, '/plan', profile: ProfileFailed(Exception('offline'))), isNull);
  });

  test('rule 4: shouldShowNotAllowedWhenProfileIsRejected', () {
    expect(redirect(signedIn, '/plan', profile: const ProfileNotAllowed()), '/not-allowed');
    expect(redirect(signedIn, '/not-allowed', profile: const ProfileNotAllowed()), isNull);
  });

  test('rule 5: shouldWaitForSignOutWhenDeletionWasRequested', () {
    const deleted = ProfileReady(onboardingCompleted: true, deletionRequested: true);
    expect(redirect(signedIn, '/plan', profile: deleted), isNull);
  });

  group('rules 6 and 7: onboarding', () {
    test('shouldSendNotOnboardedUserToFirstStep', () {
      expect(redirect(signedIn, '/', profile: notOnboarded), '/onboarding/goal');
      expect(redirect(signedIn, '/plan', profile: notOnboarded), '/onboarding/goal');
      expect(redirect(signedIn, '/projects', profile: notOnboarded), '/onboarding/goal');
      expect(redirect(signedIn, '/onboarding/plan', profile: notOnboarded), '/onboarding/goal');
    });

    test('shouldLetNotOnboardedUserStayOnInputStepsAndSettings', () {
      expect(redirect(signedIn, '/onboarding/time', profile: notOnboarded), isNull);
      expect(redirect(signedIn, '/onboarding/project', profile: notOnboarded), isNull);
      expect(redirect(signedIn, '/settings', profile: notOnboarded), isNull);
    });

    test('shouldSendOnboardedUserFromInputStepsToStartPage', () {
      expect(redirect(signedIn, '/onboarding/goal'), '/plan');
      expect(redirect(signedIn, '/onboarding/level'), '/plan');
      expect(redirect(signedIn, '/onboarding/plan'), isNull);
    });

    test('shouldSendRootToStartPageAndKeepOtherRoutes', () {
      expect(redirect(signedIn, '/'), '/plan');
      expect(redirect(signedIn, '/plan/versions/9e2b1c7a-0f0e-4d7b-8e59-0c3e1f6b2a01'), isNull);
      expect(redirect(signedIn, '/not-allowed'), '/plan');
    });
  });
}
