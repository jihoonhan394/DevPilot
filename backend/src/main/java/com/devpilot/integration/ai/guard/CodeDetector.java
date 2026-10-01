package com.devpilot.integration.ai.guard;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 텍스트 안의 코드 탐지 (docs/17 §6.3 "탐지 알고리즘"). 코드 블록(fence)이 있거나 코드 줄이 3줄 이상이면 위반이다(HL-8). 한글이 포함된 줄은 대부분
 * 코드 줄로 보지 않는다. 순수 계산이고 스레드 안전하다.
 */
public final class CodeDetector {

    /** 위반으로 보는 코드 줄 수 (HL-8). */
    public static final int MAX_CODE_LINES = 3;

    private static final Pattern FENCE = Pattern.compile("(?m)^[ \\t]{0,3}(?:```|~~~)");
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`\\n]+`");
    private static final Pattern TRAILING_COMMENT = Pattern.compile("\\s+//.*$");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern LIST_MARKER = Pattern.compile("^(?:[-*+]|\\d+[.)])\\s+");
    private static final String NOT_HANGUL = "[^\\uAC00-\\uD7A3]";

    /** 코드 줄 패턴 L1~L11. */
    private static final List<Pattern> CODE_LINE =
            List.of(
                    Pattern.compile("^" + NOT_HANGUL + "*;$"),
                    Pattern.compile("^" + NOT_HANGUL + "*\\{$"),
                    Pattern.compile("^\\}" + NOT_HANGUL + "*$"),
                    Pattern.compile("^@[A-Z][A-Za-z0-9_]*(?:\\(.*\\))?$"),
                    Pattern.compile(
                            "^(?:public|protected|private|static|final|abstract)\\s+[A-Za-z_$][\\w$<>\\[\\],.?"
                                + " ]*[({=;]"),
                    Pattern.compile("^(?:import|package)\\s+[A-Za-z_][\\w.]*(?:\\.\\*)?;?$"),
                    Pattern.compile(
                            "^(?:if|for|while|switch|catch|try|else|do|synchronized)\\s*[({]"),
                    Pattern.compile(
                            "^(?i:select|insert|update|delete)\\s+"
                                    + NOT_HANGUL
                                    + "*\\b(?i:from|into|set|where)\\b"),
                    Pattern.compile("^(?:return|throw)\\s+" + NOT_HANGUL + "+$"),
                    Pattern.compile(
                            "^(?:val|var|let|const|final)\\s+[A-Za-z_$][\\w$]*(?:\\s*:\\s*[\\w<>?]+)?\\s*=\\s*"
                                    + NOT_HANGUL
                                    + "+$"),
                    Pattern.compile(
                            "^[A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*\\("
                                    + NOT_HANGUL
                                    + "*\\)[;.]?$"));

    private CodeDetector() {}

    /** 탐지 결과. */
    public static Detection detect(String text) {
        String normalized = text.replace("\r\n", "\n");
        boolean fence = FENCE.matcher(normalized).find();
        String withoutInline = INLINE_CODE.matcher(normalized).replaceAll(" ");
        int codeLines = 0;
        for (String line : withoutInline.split("\n", -1)) {
            String candidate = TRAILING_COMMENT.matcher(line).replaceAll("");
            candidate = STRING_LITERAL.matcher(candidate).replaceAll("\"\"");
            candidate = LIST_MARKER.matcher(candidate.strip()).replaceFirst("");
            if (!candidate.isEmpty() && isCodeLine(candidate)) {
                codeLines++;
            }
        }
        return new Detection(fence, codeLines);
    }

    private static boolean isCodeLine(String line) {
        for (Pattern pattern : CODE_LINE) {
            if (pattern.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param fence 코드 블록 시작 표시(```` ``` ````, {@code ~~~})가 있다
     * @param codeLines 코드 줄 수
     */
    public record Detection(boolean fence, int codeLines) {

        /** HL-8 위반: fence 또는 코드 줄 3줄 이상. */
        public boolean violation() {
            return fence || codeLines >= MAX_CODE_LINES;
        }

        /** {@code containsCode} 보정 기준: fence 또는 코드 줄 1줄 이상. */
        public boolean containsCode() {
            return fence || codeLines >= 1;
        }
    }
}
