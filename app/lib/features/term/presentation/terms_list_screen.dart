import 'dart:async';

import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/cursor_list.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/empty_state.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/core/widgets/skill_multi_picker.dart';
import 'package:devpilot_app/features/skill/data/skill_models.dart';
import 'package:devpilot_app/features/skill/data/skill_repository.dart';
import 'package:devpilot_app/features/term/data/term_models.dart';
import 'package:devpilot_app/features/term/data/term_repository.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// 검색어를 400ms 모았다 보낸다 (docs/02 §3.17). 한 글자마다 요청을 보내면 목록이 깜빡이기만 한다.
const _debounce = Duration(milliseconds: 400);

/// SCR-TERMS (docs/02 §3.17, docs/05 §20.5). 말이 헷갈릴 때 찾는 자리다.
///
/// <b>"칼럼"으로 찾아도 "컬럼"이 나온다</b> — 서버가 별칭까지 훑고, 화면은 늘 대표 표기를 보인다(docs/19 §3.10).
class TermsListScreen extends ConsumerStatefulWidget {
  const TermsListScreen({super.key, this.query, this.skillId});

  final String? query;
  final String? skillId;

  @override
  ConsumerState<TermsListScreen> createState() => _TermsListScreenState();
}

class _TermsListScreenState extends ConsumerState<TermsListScreen> {
  late final TextEditingController _searchController = TextEditingController(text: widget.query);
  CursorList<TermSummaryView>? _list;
  Object? _error;
  Timer? _debounceTimer;

  /// 검색 중에는 기존 목록을 지우지 않는다 — 앱 바 아래 진행 막대만 둔다 (docs/02 §3.3).
  bool _searching = false;

  String? get _query {
    final text = _searchController.text.trim();
    return text.isEmpty ? null : text;
  }

  @override
  void initState() {
    super.initState();
    unawaited(_loadFirstPage());
  }

  @override
  void dispose() {
    _debounceTimer?.cancel();
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(
        title: Semantics(header: true, child: Text(l10n.termsListTitle)),
        bottom: _searching
            ? const PreferredSize(
                preferredSize: Size.fromHeight(2),
                child: LinearProgressIndicator(minHeight: 2),
              )
            : null,
      ),
      body: ScreenBody(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _searchField(l10n),
            const SizedBox(height: AppSpacing.xs),
            Text(l10n.termsSearchHelp, style: Theme.of(context).textTheme.bodySmall),
            const SizedBox(height: AppSpacing.sm),
            _skillFilter(l10n),
            const SizedBox(height: AppSpacing.md),
            _body(l10n),
          ],
        ),
      ),
    );
  }

  Widget _searchField(AppLocalizations l10n) => TextField(
    key: const Key('terms.search'),
    controller: _searchController,
    textInputAction: TextInputAction.search,
    decoration: InputDecoration(
      prefixIcon: const Icon(Icons.search),
      hintText: l10n.termsSearchHint,
      suffixIcon: _searchController.text.isEmpty
          ? null
          : IconButton(
              key: const Key('terms.search.clear'),
              icon: const Icon(Icons.clear),
              tooltip: l10n.termsSearchClear,
              onPressed: _clearQuery,
            ),
    ),
    // IME 조합 중에는 보내지 않는다 (A-13) — 자모가 만들어지는 동안의 값은 검색어가 아니다
    onChanged: (_) => _scheduleSearch(),
    onSubmitted: (_) => _searchNow(),
  );

  Widget _skillFilter(AppLocalizations l10n) {
    final tree = ref.watch(skillTreeProvider).value;
    final skill = tree?.skills.where((node) => node.id == widget.skillId).firstOrNull;
    return Align(
      alignment: Alignment.centerLeft,
      child: ActionChip(
        key: const Key('terms.filter.skill'),
        avatar: const Icon(Icons.filter_list, size: 18),
        label: Text(l10n.termsListFilterSkill(skill?.name ?? l10n.trainingListFilterAll)),
        onPressed: tree == null ? null : () => unawaited(_pickSkill(tree, skill?.code, l10n)),
      ),
    );
  }

  Future<void> _pickSkill(
    SkillTreeResponse tree,
    String? current,
    AppLocalizations l10n,
  ) async {
    final codes = await showSkillMultiPicker(
      context,
      title: l10n.trainingListFilterTitle,
      skills: pickableSkills(tree, l10n),
      initialCodes: [?current],
      maxCount: 1,
    );
    if (codes == null || !mounted) {
      return;
    }
    final code = codes.firstOrNull;
    _go(skillId: tree.skills.where((node) => node.code == code).firstOrNull?.id);
  }

  Widget _body(AppLocalizations l10n) {
    final error = _error;
    if (error != null) {
      return ErrorView(error: error, onRetry: () => unawaited(_loadFirstPage()));
    }
    final list = _list;
    if (list == null) {
      return const SkeletonList(count: 5, lines: 2);
    }
    if (list.items.isEmpty) {
      return _EmptyList(query: _query, onClear: _clearQuery);
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final term in list.items) _TermRow(term: term),
        if (list.hasMore) ...[
          const SizedBox(height: AppSpacing.sm),
          OutlinedButton(
            key: const Key('terms.more'),
            onPressed: list.isLoadingMore ? null : () => unawaited(_loadMore()),
            child: Text(l10n.termsListMore),
          ),
        ],
      ],
    );
  }

  /// `q`·`skillId`는 라우트에 남긴다 — 뒤로가기·새로고침에도 검색이 살아 있어야 한다 (docs/02 §3.17).
  void _go({String? skillId}) => context.go(AppRoutes.termsFor(query: _query, skillId: skillId));

  void _scheduleSearch() {
    setState(() {});
    _debounceTimer?.cancel();
    _debounceTimer = Timer(_debounce, _searchNow);
  }

  void _searchNow() {
    _debounceTimer?.cancel();
    unawaited(_loadFirstPage(keepList: true));
  }

  void _clearQuery() {
    _searchController.clear();
    _searchNow();
  }

  Future<void> _loadFirstPage({bool keepList = false}) async {
    setState(() {
      _error = null;
      _searching = keepList;
      if (!keepList) {
        _list = null;
      }
    });
    final query = _query;
    try {
      final page = await ref.read(termRepositoryProvider).list(q: query, skillId: widget.skillId);
      // 그 사이에 검색어가 또 바뀌었으면 늦게 온 응답은 버린다
      if (mounted && query == _query) {
        setState(() {
          _list = CursorList.firstPage(page);
          _searching = false;
        });
      }
    } on Object catch (error) {
      if (mounted) {
        setState(() {
          _error = error;
          _searching = false;
        });
      }
    }
  }

  Future<void> _loadMore() async {
    final current = _list;
    if (current == null || !current.hasMore) {
      return;
    }
    setState(() => _list = current.loadingMore());
    try {
      final page = await ref
          .read(termRepositoryProvider)
          .list(q: _query, skillId: widget.skillId, cursor: current.nextCursor);
      if (mounted) {
        setState(() => _list = current.append(page));
      }
    } on Object catch (error) {
      if (mounted) {
        setState(() => _list = current.failedToLoadMore(error));
      }
    }
  }
}

class _EmptyList extends StatelessWidget {
  const _EmptyList({required this.query, required this.onClear});

  final String? query;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final text = query;
    if (text == null) {
      return EmptyState(icon: Icons.menu_book_outlined, message: l10n.termsListEmpty);
    }
    return EmptyState(
      icon: Icons.search_off,
      message: l10n.termsListEmptyQuery(text),
      actionLabel: l10n.termsSearchClear,
      onAction: onClear,
    );
  }
}

/// 어떤 말로 찾았든 <b>대표 표기</b>로 보인다 — 저장소가 한 표기만 쓰기 때문이다 (docs/19 §3.10).
class _TermRow extends StatelessWidget {
  const _TermRow({required this.term});

  final TermSummaryView term;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Card(
      margin: const EdgeInsets.only(bottom: AppSpacing.sm),
      child: InkWell(
        onTap: () => context.push(AppRoutes.term(term.termKey)),
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.md),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Text(term.representative, style: theme.textTheme.titleSmall),
                  const SizedBox(width: AppSpacing.sm),
                  Expanded(child: Text(term.english, style: theme.textTheme.bodySmall)),
                  Text(term.level.label(l10n), style: theme.textTheme.labelSmall),
                ],
              ),
              const SizedBox(height: AppSpacing.xs),
              Text(term.definition, maxLines: 2, overflow: TextOverflow.ellipsis),
              if (term.cardCreated) ...[
                const SizedBox(height: AppSpacing.xs),
                Text(l10n.termsListHasCard, style: theme.textTheme.labelSmall),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
