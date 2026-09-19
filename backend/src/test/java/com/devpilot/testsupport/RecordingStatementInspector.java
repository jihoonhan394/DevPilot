package com.devpilot.testsupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * 실행 SQL 순서 확인용 Hibernate {@link StatementInspector} (docs/09 §8.2 I-02: SUPERSEDED update가 새 plan
 * insert보다 먼저). test profile이 {@code hibernate.session_factory.statement_inspector}로 등록한다. {@link
 * #start()}를 부른 스레드에서만 기록한다(MockMvc 요청은 테스트 스레드에서 실행된다).
 */
public class RecordingStatementInspector implements StatementInspector {

    private static final long serialVersionUID = 1L;
    private static final ThreadLocal<List<String>> RECORDED = new ThreadLocal<>();

    public static void start() {
        RECORDED.set(new ArrayList<>());
    }

    /** 기록을 끝내고 지금까지의 SQL(소문자)을 돌려준다. */
    public static List<String> stop() {
        List<String> statements = RECORDED.get();
        RECORDED.remove();
        return statements == null ? List.of() : List.copyOf(statements);
    }

    @Override
    public String inspect(String sql) {
        List<String> statements = RECORDED.get();
        if (statements != null) {
            statements.add(sql.toLowerCase(Locale.ROOT));
        }
        return sql;
    }
}
