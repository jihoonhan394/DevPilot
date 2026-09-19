package com.devpilot.testsupport;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Objects;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

/** {@code com.devpilot.audit} 로거 이벤트 수집 (docs/09 §4.1). */
public final class AuditLogCapture implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private AuditLogCapture(Logger logger) {
        this.logger = logger;
    }

    public static AuditLogCapture start() {
        Logger logger = (Logger) LoggerFactory.getLogger("com.devpilot.audit");
        AuditLogCapture capture = new AuditLogCapture(logger);
        capture.appender.start();
        logger.addAppender(capture.appender);
        return capture;
    }

    /** 잡힌 이벤트의 {@code event} 값 목록 (발생 순서). */
    public List<String> events() {
        return appender.list.stream()
                .map(
                        event ->
                                event.getKeyValuePairs() == null
                                        ? null
                                        : event.getKeyValuePairs().stream()
                                                .filter(pair -> "event".equals(pair.key))
                                                .map(pair -> String.valueOf(pair.value))
                                                .findFirst()
                                                .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    public List<KeyValuePair> fields(int index) {
        return appender.list.get(index).getKeyValuePairs();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
