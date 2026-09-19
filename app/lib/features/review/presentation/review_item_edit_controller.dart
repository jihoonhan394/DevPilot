import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/review/data/review_item_models.dart';
import 'package:devpilot_app/features/review/data/review_item_repository.dart';
import 'package:devpilot_app/features/review/domain/review_item_form.dart';
import 'package:devpilot_app/features/review/presentation/due_reviews_provider.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

@immutable
final class ReviewItemEditState {
  const ReviewItemEditState({required this.form, this.saving = false, this.error});

  final ReviewItemForm form;
  final bool saving;

  /// The last save failure, shown next to its field or under the form.
  final ApiException? error;

  ReviewItemEditState copyWith({ReviewItemForm? form, bool? saving, ApiException? error}) =>
      ReviewItemEditState(
        form: form ?? this.form,
        saving: saving ?? this.saving,
        error: error,
      );
}

sealed class ReviewItemSaveOutcome {
  const ReviewItemSaveOutcome();
}

/// Saved. [existing] is true when `POST` found a card of the same concept (200).
final class ReviewItemSaved extends ReviewItemSaveOutcome {
  const ReviewItemSaved({this.existing = false});

  final bool existing;
}

/// `CONCURRENT_MODIFICATION` / `INVALID_STATE_TRANSITION` / `RESOURCE_NOT_FOUND` on edit: the
/// card changed elsewhere; the list is opened again.
final class ReviewItemStale extends ReviewItemSaveOutcome {
  const ReviewItemStale();
}

final class ReviewItemSaveFailed extends ReviewItemSaveOutcome {
  const ReviewItemSaveFailed(this.error);

  final ApiException error;
}

/// SCR-REVIEW-ITEM-EDIT state for a new card (`null`) or an existing one.
final class ReviewItemEditController extends Notifier<ReviewItemEditState> {
  ReviewItemEditController(this.item);

  final ReviewItemView? item;
  final _createKeys = IdempotencyKeyCache();

  static const _uuid = Uuid();

  @override
  ReviewItemEditState build() {
    final editing = item;
    return ReviewItemEditState(
      form: editing == null
          ? ReviewItemForm(conceptKey: 'MANUAL:${_uuid.v4().toUpperCase()}')
          : ReviewItemForm.edit(editing),
    );
  }

  void edit(ReviewItemForm Function(ReviewItemForm form) change) =>
      state = state.copyWith(form: change(state.form));

  Future<ReviewItemSaveOutcome> save() async {
    final form = state.form;
    state = state.copyWith(saving: true);
    final repository = ref.read(reviewItemRepositoryProvider);
    try {
      final editing = form.editing;
      if (editing != null) {
        await repository.updateItem(editing.id, form.toPatchRequest());
        state = ReviewItemEditState(form: form);
        return const ReviewItemSaved();
      }
      final request = form.toCreateRequest();
      final result = await repository.createItem(
        request,
        idempotencyKey: _createKeys.keyFor(request.toJson()),
      );
      _createKeys.settle(null);
      // The new card is due from the next plan-day; Review reads its list again.
      ref.invalidate(dueReviewsProvider);
      state = ReviewItemEditState(form: form);
      return ReviewItemSaved(existing: !result.created);
    } on ApiException catch (error) {
      _createKeys.settle(error);
      state = state.copyWith(saving: false, error: error);
      const staleCodes = {
        ApiErrorCode.concurrentModification,
        ApiErrorCode.invalidStateTransition,
        ApiErrorCode.resourceNotFound,
      };
      if (form.isEdit && staleCodes.contains(error.code)) {
        return const ReviewItemStale();
      }
      return ReviewItemSaveFailed(error);
    }
  }
}

final reviewItemEditControllerProvider = NotifierProvider.autoDispose
    .family<ReviewItemEditController, ReviewItemEditState, ReviewItemView?>(
      ReviewItemEditController.new,
    );

/// The route asks before leaving while the form holds unsaved input (docs/02 §6.8).
final class ReviewItemEditDirty extends Notifier<bool> {
  @override
  bool build() => false;

  void set({required bool dirty}) => state = dirty;
}

final reviewItemEditDirtyProvider = NotifierProvider<ReviewItemEditDirty, bool>(
  ReviewItemEditDirty.new,
);
