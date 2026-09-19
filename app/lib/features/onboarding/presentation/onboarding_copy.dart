import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// The server plan-day. Onboarding screens are only reachable with a loaded profile.
LocalDate profileToday(WidgetRef ref) => LocalDate.parse(ref.watch(meProvider).requireValue.today);

/// Server message for [field] from the last failed `POST /onboarding`, if any.
String? submitFieldMessage(ApiException? error, String field, AppLocalizations l10n) =>
    error?.fieldMessage(field, fallback: l10n.errorValidationFailed);
