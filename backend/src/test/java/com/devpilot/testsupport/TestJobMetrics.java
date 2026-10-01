package com.devpilot.testsupport;

import com.devpilot.common.job.JobMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** 단위 테스트용 {@link JobMetrics} (메모리 registry, docs/09 §3.2). */
public final class TestJobMetrics {

    private TestJobMetrics() {}

    public static JobMetrics jobMetrics() {
        return new JobMetrics(new SimpleMeterRegistry());
    }
}
