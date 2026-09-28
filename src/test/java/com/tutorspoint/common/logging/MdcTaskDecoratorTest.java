package com.tutorspoint.common.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTaskDecoratorTest {

    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        MDC.clear();
        pool.shutdownNow();
    }

    @Test
    void theRequestIdTravelsToThePoolThreadAndDoesNotStayThere() throws Exception {
        MdcTaskDecorator decorator = new MdcTaskDecorator();
        AtomicReference<String> duringTask = new AtomicReference<>();
        AtomicReference<String> afterTask = new AtomicReference<>("not checked");

        MDC.put(RequestIdFilter.MDC_KEY, "req-12345678");
        Runnable decorated = decorator.decorate(() -> duringTask.set(MDC.get(RequestIdFilter.MDC_KEY)));
        MDC.clear();

        pool.submit(decorated).get(5, TimeUnit.SECONDS);
        pool.submit(() -> afterTask.set(MDC.get(RequestIdFilter.MDC_KEY))).get(5, TimeUnit.SECONDS);

        assertThat(duringTask.get()).isEqualTo("req-12345678");
        assertThat(afterTask.get()).as("the next task on the same thread").isNull();
    }

    @Test
    void anIdMustLookLikeAnIdToBeReused() {
        assertThat(RequestIdFilter.acceptableOrNew("0f8c2b9e-1111-4a7e-9d9d-2c1e5b6a7f80"))
                .isEqualTo("0f8c2b9e-1111-4a7e-9d9d-2c1e5b6a7f80");
        assertThat(RequestIdFilter.acceptableOrNew("short")).isNotEqualTo("short");
        assertThat(RequestIdFilter.acceptableOrNew("x".repeat(65))).hasSize(36);
        assertThat(RequestIdFilter.acceptableOrNew("abc\r\ndef-ghij")).doesNotContain("\n");
        assertThat(RequestIdFilter.acceptableOrNew(null)).hasSize(36);
    }
}
