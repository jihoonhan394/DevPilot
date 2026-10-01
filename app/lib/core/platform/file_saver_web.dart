import 'dart:js_interop';

import 'package:web/web.dart' as web;

/// 웹 빌드: 본문을 파일로 내려받는다 (docs/02 SCR-PROJECT-DETAIL).
///
/// 파일 이름은 서버가 정한 것을 그대로 쓴다 — 클라이언트가 지어내면 서버가 붙인 날짜와 어긋난다.
void saveTextFile({required String fileName, required String text}) {
  final blob = web.Blob(
    [text.toJS].toJS,
    web.BlobPropertyBag(type: 'text/markdown;charset=utf-8'),
  );
  final url = web.URL.createObjectURL(blob);
  final anchor = web.document.createElement('a') as web.HTMLAnchorElement
    ..href = url
    ..download = fileName;
  anchor.click();
  web.URL.revokeObjectURL(url);
}
