package com.devpilot.common.job;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 스케줄 job 지표 (docs/03 §6, BL-FND-26): {@code devpilot.jobs.runs{job,result}}. 외부로 노출하지 않는다 —
 * actuator는 {@code health}만 연다(docs/10). 실행 1회가 1 증가다.
 */
@Component
public class JobMetrics {

    /** counter 이름. */
    public static final String RUNS = "devpilot.jobs.runs";

    private static final String JOB_TAG = "job";
    private static final String RESULT_TAG = "result";
    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";

    private final MeterRegistry meterRegistry;

    public JobMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /** 실행이 끝났다. {@code failed > 0}이면 실패로 센다. */
    public void record(String job, int failed) {
        counter(job, failed > 0 ? FAILURE : SUCCESS).increment();
    }

    /** 실행이 예외로 끝났다. */
    public void recordFailure(String job) {
        counter(job, FAILURE).increment();
    }

    /** 모든 job의 실패 실행 수 (일일 요약). */
    public long failureRuns() {
        double total = 0;
        for (Counter counter : meterRegistry.find(RUNS).tag(RESULT_TAG, FAILURE).counters()) {
            total += counter.count();
        }
        return (long) total;
    }

    private Counter counter(String job, String result) {
        return meterRegistry.counter(
                RUNS, List.of(Tag.of(JOB_TAG, job), Tag.of(RESULT_TAG, result)));
    }
}
