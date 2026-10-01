package com.devpilot.common.async;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.UnitTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * docs/03 §5.3·§7 (BL-FND-23): 비동기 AI 작업의 최상위 예외는 리소스를 {@code FAILED(INTERNAL_ERROR)}로 바꾸고 밖으로 새지
 * 않는다.
 */
@UnitTest
class AsyncAiTaskTest {

    @Test
    void shouldMarkFailedWithInternalErrorWhenProcessThrows() {
        RecordingTask task = new RecordingTask(true);

        task.runSafely("resource-1");

        assertThat(task.failures).containsExactly("resource-1:INTERNAL_ERROR");
    }

    @Test
    void shouldNotMarkFailedWhenProcessCompletes() {
        RecordingTask task = new RecordingTask(false);

        task.runSafely("resource-2");

        assertThat(task.processed).containsExactly("resource-2");
        assertThat(task.failures).isEmpty();
    }

    private static final class RecordingTask extends AsyncAiTask<String> {

        private final boolean fail;
        private final List<String> processed = new ArrayList<>();
        private final List<String> failures = new ArrayList<>();

        RecordingTask(boolean fail) {
            this.fail = fail;
        }

        @Override
        protected void process(String event) {
            if (fail) {
                throw new IllegalStateException("unexpected");
            }
            processed.add(event);
        }

        @Override
        protected void markFailed(String event, AsyncFailureCode failureCode) {
            failures.add(event + ":" + failureCode);
        }
    }
}
