import 'dart:async';

import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/app_theme.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_markdown_plus/flutter_markdown_plus.dart';
import 'package:markdown/markdown.dart' as md;
import 'package:url_launcher/url_launcher.dart';

/// Body text that may contain Markdown (docs/02 §2.4). Problems, hints, evaluation feedback, review
/// cards and concept readings are written in Markdown, so a plain `Text` shows ``` and `#` as
/// letters.
///
/// What it deliberately does not do:
///
/// * **No images.** Neither app nor server fetches what the content points at (docs/07 §4.2). An
///   image becomes its alt text so the sentence still reads.
/// * **No raw HTML.** The parser runs without the inline HTML syntax, so `<script>` stays literal
///   text. The same widget draws AI output and text the user typed.
/// * **Links only open a new tab.** Tapping one calls `launchUrl` with an http(s) URL; the app
///   never loads the page itself.
///
/// Selection belongs to the [SelectionArea] of the screen body, so [MarkdownBody.selectable] stays
/// false — a nested selection scope would stop one drag from spanning the screen.
class MarkdownText extends StatelessWidget {
  const MarkdownText(this.data, {super.key, this.style, this.textKey});

  /// Markdown source. Text without any markup renders as a paragraph.
  final String data;

  /// Style of paragraphs. Defaults to `bodyMedium`.
  final TextStyle? style;

  /// Key of the rendered block, for tests that look the content up by key.
  final Key? textKey;

  /// gitHubFlavored without `InlineHtmlSyntax`.
  static final _extensions = md.ExtensionSet(
    md.ExtensionSet.gitHubFlavored.blockSyntaxes,
    md.ExtensionSet.gitHubFlavored.inlineSyntaxes
        .where((syntax) => syntax is! md.InlineHtmlSyntax)
        .toList(),
  );

  @override
  Widget build(BuildContext context) {
    final base = style ?? Theme.of(context).textTheme.bodyMedium;
    return KeyedSubtree(
      key: textKey,
      child: MarkdownBody(
        data: data,
        selectable: false,
        styleSheet: _styleSheet(context, base),
        imageBuilder: (uri, title, alt) => Text(alt ?? title ?? uri.toString(), style: base),
        builders: {'pre': _CodeBlockBuilder()},
        onTapLink: (text, href, title) => _open(href),
        extensionSet: _extensions,
      ),
    );
  }

  static void _open(String? href) {
    final uri = href == null ? null : Uri.tryParse(href);
    if (uri == null || !(uri.isScheme('https') || uri.isScheme('http'))) {
      return;
    }
    unawaited(launchUrl(uri, webOnlyWindowName: '_blank'));
  }

  static MarkdownStyleSheet _styleSheet(BuildContext context, TextStyle? base) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return MarkdownStyleSheet.fromTheme(theme).copyWith(
      p: base,
      listBullet: base,
      a: base?.copyWith(color: scheme.primary, decoration: TextDecoration.underline),
      code: AppTheme.codeTextStyle.copyWith(
        backgroundColor: scheme.surfaceContainerHighest,
        color: scheme.onSurface,
      ),
      // _CodeBlockBuilder draws the block itself; keep the wrapper from painting a second card.
      codeblockDecoration: const BoxDecoration(),
      codeblockPadding: EdgeInsets.zero,
      blockquoteDecoration: BoxDecoration(
        color: scheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(AppRadius.sm),
      ),
      blockSpacing: AppSpacing.sm,
      h1: theme.textTheme.titleLarge,
      h2: theme.textTheme.titleMedium,
      h3: theme.textTheme.titleSmall,
      h4: theme.textTheme.titleSmall,
      h5: theme.textTheme.titleSmall,
      h6: theme.textTheme.titleSmall,
    );
  }
}

/// Draws a fenced code block as a [CodeBlock].
class _CodeBlockBuilder extends MarkdownElementBuilder {
  @override
  bool isBlockElement() => true;

  @override
  Widget? visitElementAfterWithContext(
    BuildContext context,
    md.Element element,
    TextStyle? preferredStyle,
    TextStyle? parentStyle,
  ) {
    final code = _firstElement(element);
    return CodeBlock(
      code: (code ?? element).textContent.replaceAll(RegExp(r'\n+$'), ''),
      language: _languageOf(code),
    );
  }

  static md.Element? _firstElement(md.Element element) {
    for (final child in element.children ?? const <md.Node>[]) {
      if (child is md.Element) {
        return child;
      }
    }
    return null;
  }

  /// ```` ```java ```` becomes `class="language-java"` on the inner `code` element.
  static String? _languageOf(md.Element? code) {
    final classes = code?.attributes['class'];
    if (classes == null) {
      return null;
    }
    const prefix = 'language-';
    for (final name in classes.split(' ')) {
      if (name.startsWith(prefix) && name.length > prefix.length) {
        return name.substring(prefix.length);
      }
    }
    return null;
  }
}

/// Code on a tinted card with the language and a copy button. Long lines scroll sideways instead of
/// wrapping, so a wide example never widens the screen (docs/02 A-9).
class CodeBlock extends StatefulWidget {
  const CodeBlock({super.key, required this.code, this.language});

  final String code;
  final String? language;

  @override
  State<CodeBlock> createState() => _CodeBlockState();
}

class _CodeBlockState extends State<CodeBlock> {
  final _scroll = ScrollController();

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final language = widget.language;
    return Container(
      margin: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
      decoration: BoxDecoration(
        color: scheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(AppRadius.sm),
        border: Border.all(color: scheme.outlineVariant),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              const SizedBox(width: AppSpacing.sm),
              Expanded(
                child: Text(
                  language ?? '',
                  style: theme.textTheme.labelSmall,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              IconButton(
                key: const Key('code.copyButton'),
                tooltip: l10n.commonErrorCopy,
                iconSize: 18,
                visualDensity: VisualDensity.compact,
                onPressed: _copy,
                icon: const Icon(Icons.copy),
              ),
            ],
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(AppSpacing.sm, 0, AppSpacing.sm, AppSpacing.sm),
            child: Scrollbar(
              controller: _scroll,
              thumbVisibility: true,
              child: SingleChildScrollView(
                controller: _scroll,
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.only(bottom: AppSpacing.xs),
                child: Text(widget.code, style: AppTheme.codeTextStyle, softWrap: false),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _copy() async {
    await Clipboard.setData(ClipboardData(text: widget.code));
    if (mounted) {
      showToast(context, AppLocalizations.of(context).commonErrorCopied);
    }
  }
}
