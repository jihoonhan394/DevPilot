import 'package:devpilot_app/features/tip/data/tip_models.dart';
import 'package:devpilot_app/features/tip/data/tip_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// 오늘의 팁 (docs/05 §20.2). 404면 오늘 보여 줄 팁이 없다는 뜻이라 카드 자리를 비운다 (docs/02 SCR-TODAY).
final todayTipProvider = FutureProvider<DailyTipView>(
  (ref) => ref.watch(tipRepositoryProvider).fetchToday(),
);

/// 팁 1건 (docs/05 §20.4a). 지난 팁도, 은퇴한 팁도 본문이 온다.
final tipProvider = FutureProvider.family<DailyTipView, String>(
  (ref, tipKey) => ref.watch(tipRepositoryProvider).fetch(tipKey),
);
