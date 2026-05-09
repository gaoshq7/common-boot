package cn.gsq.common.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CommonAsyncProcessor 标准 JUnit 5 测试用例。
 * 用真实线程池验证：直接 new 一个 ThreadPoolExecutor + ScheduledExecutorService，
 * 不依赖 Spring 上下文，开箱即跑。
 */
class CommonAsyncProcessorTest {

    private ThreadPoolExecutor service;
    private ScheduledExecutorService scheduler;
    private CommonAsyncProcessor processor;

    @BeforeEach
    void setUp() {
        service = new ThreadPoolExecutor(
                2, 4, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("test-async-" + t.getId());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r);
            t.setName("test-scheduler-" + t.getId());
            t.setDaemon(true);
            return t;
        });
        processor = new CommonAsyncProcessor(service, scheduler);
    }

    @AfterEach
    void tearDown() {
        processor.destroy();
    }

    // ============================================================
    // ===== runAsync
    // ============================================================

    @Nested
    @DisplayName("runAsync(Runnable)")
    class RunAsyncTests {

        @Test
        @DisplayName("任务正常执行，返回的 CompletableFuture 完成")
        void should_complete_when_task_succeeds() throws Exception {
            AtomicBoolean executed = new AtomicBoolean(false);

            CompletableFuture<Void> future = processor.runAsync(() -> executed.set(true));
            future.get(2, TimeUnit.SECONDS);

            assertThat(executed).isTrue();
            assertThat(future).isDone();
            assertThat(future).isNotCompletedExceptionally();
        }

        @Test
        @DisplayName("任务抛异常，CompletableFuture exceptionally 完成（异常冒泡）")
        void should_complete_exceptionally_when_task_throws() {
            CompletableFuture<Void> future = processor.runAsync(() -> {
                throw new RuntimeException("boom");
            });

            assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RuntimeException.class)
                    .hasMessageContaining("boom");
        }

        @Test
        @DisplayName("带 onError 的版本：任务异常时 onError 被调用，外层 CF 不会异常完成")
        void should_invoke_onError_and_swallow_exception() throws Exception {
            AtomicReference<Throwable> caught = new AtomicReference<>();

            CompletableFuture<Void> future = processor.runAsync(() -> {
                throw new IllegalStateException("test-fail");
            }, caught::set);

            future.get(2, TimeUnit.SECONDS);

            assertThat(caught.get())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("test-fail");
        }

        @Test
        @DisplayName("onError 自身抛异常被吃掉打日志，不污染外层 CF")
        void should_isolate_onError_exception() throws Exception {
            CompletableFuture<Void> future = processor.runAsync(
                    () -> { throw new RuntimeException("biz"); },
                    e -> { throw new RuntimeException("onError 也炸了"); }
            );

            // 即使 onError 炸了，外层 CF 也应该正常完成（whenComplete 完成态）
            future.get(2, TimeUnit.SECONDS);
            assertThat(future).isDone();
        }

    }

    // ============================================================
    // ===== supplyAsync —— 重点验证之前的 P0 bug 已修
    // ============================================================

    @Nested
    @DisplayName("supplyAsync(Callable)")
    class SupplyAsyncTests {

        @Test
        @DisplayName("正常返回值")
        void should_return_value() throws Exception {
            CompletableFuture<Integer> future = processor.supplyAsync(() -> 42);
            assertThat(future.get(2, TimeUnit.SECONDS)).isEqualTo(42);
        }

        @Test
        @DisplayName("Checked exception 被包装为 CompletionException")
        void should_wrap_checked_exception() {
            CompletableFuture<String> future = processor.supplyAsync(() -> {
                throw new IOException("io error");
            });

            assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IOException.class)
                    .hasRootCauseMessage("io error");
        }

        @Test
        @DisplayName("三参数版：成功路径调 onSuccess，不调 onError")
        void should_invoke_onSuccess_only_on_success() throws Exception {
            AtomicReference<String> okValue = new AtomicReference<>();
            AtomicReference<Throwable> errValue = new AtomicReference<>();

            processor.supplyAsync(() -> "hello", okValue::set, errValue::set)
                    .get(2, TimeUnit.SECONDS);

            assertThat(okValue.get()).isEqualTo("hello");
            assertThat(errValue.get()).isNull();
        }

        @Test
        @DisplayName("三参数版：失败路径调 onError，不调 onSuccess")
        void should_invoke_onError_only_on_failure() throws Exception {
            AtomicReference<String> okValue = new AtomicReference<>();
            AtomicReference<Throwable> errValue = new AtomicReference<>();

            CompletableFuture<String> future = processor.supplyAsync(() -> {
                throw new RuntimeException("biz fail");
            }, okValue::set, errValue::set);

            // future 会异常完成，但回调已被触发
            assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class);

            assertThat(okValue.get()).isNull();
            assertThat(errValue.get())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("biz fail");
        }

        @Test
        @DisplayName("回归：actuator 返回 null，onSuccess 仍然被调用（修复了旧 AsyncProcessor 的 null 陷阱）")
        void should_invoke_onSuccess_even_when_result_is_null() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> okValue = new AtomicReference<>("not-touched");

            processor.supplyAsync(
                    () -> (String) null,
                    v -> { okValue.set(v); latch.countDown(); },
                    e -> latch.countDown()
            ).get(2, TimeUnit.SECONDS);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(okValue.get()).isNull();   // 被显式置为 null（说明 onSuccess 真的跑了）
        }

        @Test
        @DisplayName("回归：onSuccess 自身抛异常会被吃掉打日志，不会被误判为 actuator 失败而调 onError")
        void should_isolate_onSuccess_exception_from_onError() throws Exception {
            AtomicReference<Throwable> errValue = new AtomicReference<>();

            CompletableFuture<String> future = processor.supplyAsync(
                    () -> "ok",
                    v -> { throw new RuntimeException("onSuccess 自己炸了"); },
                    errValue::set
            );

            future.get(2, TimeUnit.SECONDS);

            // onSuccess 抛异常不应该走到 onError —— 这是修复的关键 bug
            assertThat(errValue.get()).isNull();
        }

    }

    // ============================================================
    // ===== callSync
    // ============================================================

    @Nested
    @DisplayName("callSync(Callable, timeout, unit)")
    class CallSyncTests {

        @Test
        @DisplayName("正常返回值")
        void should_return_value() {
            String r = processor.callSync(() -> "synced", 2, TimeUnit.SECONDS);
            assertThat(r).isEqualTo("synced");
        }

        @Test
        @DisplayName("超时抛 IllegalStateException(原因 = TimeoutException)")
        void should_throw_on_timeout() {
            assertThatThrownBy(() ->
                    processor.callSync(() -> {
                        Thread.sleep(2000);
                        return "late";
                    }, 100, TimeUnit.MILLISECONDS)
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("超时")
                    .hasCauseInstanceOf(TimeoutException.class);
        }

        @Test
        @DisplayName("任务抛异常，包装为 IllegalStateException，原异常在 cause")
        void should_wrap_task_exception() {
            assertThatThrownBy(() ->
                    processor.callSync(() -> {
                        throw new IllegalArgumentException("bad arg");
                    }, 2, TimeUnit.SECONDS)
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .hasRootCauseMessage("bad arg");
        }

    }

    // ============================================================
    // ===== invokeAll / invokeAny
    // ============================================================

    @Nested
    @DisplayName("invokeAll / invokeAny")
    class InvokeBatchTests {

        @Test
        @DisplayName("invokeAll: 全部完成，按提交顺序返回结果")
        void should_return_all_results_in_order() {
            List<java.util.concurrent.Callable<Integer>> tasks = IntStream.rangeClosed(1, 5)
                    .mapToObj(i -> (java.util.concurrent.Callable<Integer>) () -> {
                        Thread.sleep(50);
                        return i * 10;
                    })
                    .collect(Collectors.toList());

            List<Integer> results = processor.invokeAll(tasks, 3, TimeUnit.SECONDS);

            assertThat(results).containsExactly(10, 20, 30, 40, 50);
        }

        @Test
        @DisplayName("invokeAll: 子任务超时未完成会被 cancel 并抛 IllegalStateException")
        void should_throw_when_any_task_timeout() {
            List<java.util.concurrent.Callable<String>> tasks = Arrays.asList(
                    () -> "fast",
                    () -> { Thread.sleep(2000); return "slow"; }
            );

            assertThatThrownBy(() -> processor.invokeAll(tasks, 200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("超时");
        }

        @Test
        @DisplayName("invokeAny: 第一个完成的就返回")
        void should_return_first_winner() {
            List<java.util.concurrent.Callable<String>> tasks = Arrays.asList(
                    () -> { Thread.sleep(500); return "slow"; },
                    () -> "fast"
            );

            String r = processor.invokeAny(tasks, 2, TimeUnit.SECONDS);
            assertThat(r).isEqualTo("fast");
        }

    }

    // ============================================================
    // ===== schedule
    // ============================================================

    @Nested
    @DisplayName("schedule / scheduleAtFixedRate")
    class ScheduleTests {

        @Test
        @DisplayName("schedule: 延迟执行一次")
        void should_run_after_delay() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            long t0 = System.currentTimeMillis();
            AtomicReference<Long> firedAt = new AtomicReference<>();

            processor.schedule(() -> {
                firedAt.set(System.currentTimeMillis());
                latch.countDown();
            }, 200, TimeUnit.MILLISECONDS);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            long elapsed = firedAt.get() - t0;
            assertThat(elapsed).isGreaterThanOrEqualTo(180);   // 留 20ms 容差
        }

        @Test
        @DisplayName("scheduleAtFixedRate: 周期执行多次，单次任务异常不打断后续触发")
        void should_continue_after_task_exception() throws Exception {
            AtomicInteger count = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(3);

            processor.scheduleAtFixedRate(() -> {
                int n = count.incrementAndGet();
                latch.countDown();
                if (n == 1) {
                    throw new RuntimeException("first one fails");   // 应被 safeRun 吃掉
                }
            }, 0, 100, TimeUnit.MILLISECONDS);

            assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(count.get()).isGreaterThanOrEqualTo(3);
        }

    }

    // ============================================================
    // ===== submitWithRetry
    // ============================================================

    @Nested
    @DisplayName("submitWithRetry")
    class RetryTests {

        @Test
        @DisplayName("前两次失败、第三次成功")
        void should_succeed_after_retries() throws Exception {
            AtomicInteger attempts = new AtomicInteger();

            CompletableFuture<String> future = processor.submitWithRetry(() -> {
                int n = attempts.incrementAndGet();
                if (n < 3) throw new RuntimeException("fail attempt " + n);
                return "ok-on-" + n;
            }, 5, Duration.ofMillis(50));

            assertThat(future.get(3, TimeUnit.SECONDS)).isEqualTo("ok-on-3");
            assertThat(attempts.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("达到最大重试次数仍失败，CF 用原异常 completeExceptionally")
        void should_fail_after_max_attempts() {
            AtomicInteger attempts = new AtomicInteger();

            CompletableFuture<String> future = processor.submitWithRetry(() -> {
                attempts.incrementAndGet();
                throw new IllegalStateException("permanent");
            }, 3, Duration.ofMillis(20));

            assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("permanent");

            assertThat(attempts.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("backoff 间隔起作用")
        void should_respect_backoff() throws Exception {
            AtomicInteger attempts = new AtomicInteger();
            long t0 = System.currentTimeMillis();

            CompletableFuture<String> future = processor.submitWithRetry(() -> {
                if (attempts.incrementAndGet() < 3) throw new RuntimeException("retry");
                return "done";
            }, 5, Duration.ofMillis(150));

            future.get(3, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - t0;

            // 重试 2 次 → 至少经历 2 * 150ms = 300ms
            assertThat(elapsed).isGreaterThanOrEqualTo(280);
        }

    }

    // ============================================================
    // ===== wrapWithContext
    // ============================================================

    @Nested
    @DisplayName("wrapWithContext")
    class WrapContextTests {

        @Test
        @DisplayName("Runnable 包装后异步执行能拿到主线程的 MDC")
        void should_propagate_mdc_for_runnable() throws Exception {
            MDC.clear();
            MDC.put("traceId", "abc-123");
            try {
                AtomicReference<String> seen = new AtomicReference<>();
                CountDownLatch latch = new CountDownLatch(1);

                Runnable wrapped = processor.wrapWithContext(() -> {
                    seen.set(MDC.get("traceId"));
                    latch.countDown();
                });

                processor.runAsync(wrapped);

                assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(seen.get()).isEqualTo("abc-123");
            } finally {
                MDC.clear();
            }
        }

        @Test
        @DisplayName("Callable 包装后异步执行能拿到主线程的 MDC")
        void should_propagate_mdc_for_callable() throws Exception {
            MDC.clear();
            MDC.put("user", "alice");
            try {
                java.util.concurrent.Callable<String> wrapped = processor.wrapWithContext(
                        () -> MDC.get("user")
                );

                String r = processor.supplyAsync(wrapped).get(2, TimeUnit.SECONDS);

                assertThat(r).isEqualTo("alice");
            } finally {
                MDC.clear();
            }
        }

        @Test
        @DisplayName("包装任务执行完不会污染主线程的 MDC（恢复原值）")
        void should_not_pollute_caller_mdc() throws Exception {
            MDC.clear();
            MDC.put("traceId", "outer");
            try {
                Runnable wrapped = processor.wrapWithContext(() -> {
                    MDC.put("traceId", "inner");
                });
                // 在主线程直接同步跑 wrapped，验证恢复
                wrapped.run();

                assertThat(MDC.get("traceId")).isEqualTo("outer");
            } finally {
                MDC.clear();
            }
        }

    }

    // ============================================================
    // ===== PoolStats / awaitQuiescence
    // ============================================================

    @Nested
    @DisplayName("getStats / awaitQuiescence")
    class MonitorTests {

        @Test
        @DisplayName("getStats 返回真实指标")
        void should_return_real_stats() throws Exception {
            CountDownLatch hold = new CountDownLatch(1);
            CountDownLatch ready = new CountDownLatch(2);

            for (int i = 0; i < 2; i++) {
                processor.runAsync(() -> {
                    ready.countDown();
                    try { hold.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
            }

            ready.await(2, TimeUnit.SECONDS);

            CommonAsyncProcessor.PoolStats stats = processor.getStats();
            assertThat(stats.getActiveThreads()).isEqualTo(2);
            assertThat(stats.getPoolSize()).isGreaterThanOrEqualTo(2);

            hold.countDown();   // 放任务跑完，避免阻塞 destroy
        }

        @Test
        @DisplayName("awaitQuiescence: 任务都完成后能等到静默")
        void should_await_quiescence() throws Exception {
            for (int i = 0; i < 5; i++) {
                processor.runAsync(() -> {
                    try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                });
            }

            boolean quiet = processor.awaitQuiescence(2, TimeUnit.SECONDS);
            assertThat(quiet).isTrue();
        }

    }

    // ============================================================
    // ===== destroy
    // ============================================================

    @Nested
    @DisplayName("destroy")
    class DestroyTests {

        @Test
        @DisplayName("destroy 后主线程池与 scheduler 都关闭")
        void should_shutdown_both_pools() {
            CommonAsyncProcessor local = new CommonAsyncProcessor(
                    new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                            new LinkedBlockingQueue<>()),
                    Executors.newScheduledThreadPool(1)
            );

            local.destroy();

            assertThat(local.getService().isShutdown()).isTrue();
            assertThat(local.getScheduler().isShutdown()).isTrue();
        }

    }

    // ============================================================
    // ===== 旧 @Deprecated API 回归（确保兼容性）
    // ============================================================

    @Nested
    @DisplayName("Deprecated API 兼容性回归")
    @SuppressWarnings("deprecation")
    class DeprecatedRegressionTests {

        @Test
        @DisplayName("submitTask(SimpleProcessor): 异常自动捕获不会冒到调用线程")
        void simple_processor_should_swallow_exception() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);

            processor.submitTask((CommonAsyncProcessor.SimpleProcessor) () -> {
                try {
                    throw new RuntimeException("boom");
                } finally {
                    latch.countDown();
                }
            });

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            // 没崩说明被吞 + 打日志了；调用方线程未受影响
        }

        @Test
        @DisplayName("submitTask(AsyncProcessor): 成功路径调 callback")
        void async_processor_success_path() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> callbackVal = new AtomicReference<>();

            processor.submitTask(new CommonAsyncProcessor.AsyncProcessor<String>() {
                @Override public String declaration() { return "fallback"; }
                @Override public String actuator()    { return "ok"; }
                @Override public void   callback(String t) { callbackVal.set(t); latch.countDown(); }
                @Override public void   error(String t, Throwable e) { latch.countDown(); }
            });

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(callbackVal.get()).isEqualTo("ok");
        }

        @Test
        @DisplayName("submitTask(AsyncProcessor): 失败路径调 error，传入 declaration 兜底值")
        void async_processor_error_path() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> fallbackSeen = new AtomicReference<>();
            AtomicReference<Throwable> errSeen = new AtomicReference<>();

            processor.submitTask(new CommonAsyncProcessor.AsyncProcessor<String>() {
                @Override public String declaration() { return "FALLBACK"; }
                @Override public String actuator()    { throw new RuntimeException("fail"); }
                @Override public void   callback(String t) { latch.countDown(); }
                @Override public void   error(String t, Throwable e) {
                    fallbackSeen.set(t);
                    errSeen.set(e);
                    latch.countDown();
                }
            });

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(fallbackSeen.get()).isEqualTo("FALLBACK");
            assertThat(errSeen.get())
                    .satisfiesAnyOf(
                            e -> assertThat(e).hasMessage("fail"),
                            e -> assertThat(e).isInstanceOf(CompletionException.class).hasRootCauseMessage("fail")
                    );
        }

        @Test
        @DisplayName("submitTask(AsyncProcessor): result == null 时 callback 不触发（保留旧行为）")
        void async_processor_null_result_skips_callback() throws Exception {
            CountDownLatch invoked = new CountDownLatch(1);
            AtomicBoolean callbackCalled = new AtomicBoolean(false);

            processor.submitTask(new CommonAsyncProcessor.AsyncProcessor<String>() {
                @Override public String declaration() { return null; }
                @Override public String actuator()    { invoked.countDown(); return null; }
                @Override public void   callback(String t) { callbackCalled.set(true); }
                @Override public void   error(String t, Throwable e) {}
            });

            assertThat(invoked.await(2, TimeUnit.SECONDS)).isTrue();
            // 等一会儿确保 whenComplete 也跑过了
            Thread.sleep(200);
            assertThat(callbackCalled).isFalse();
        }

        @Test
        @DisplayName("submitTask(SyncProcessor): 同步阻塞拿到结果")
        void sync_processor_returns_value() {
            String r = processor.submitTask(
                    (CommonAsyncProcessor.SyncProcessor<String>) () -> "synced"
            );
            assertThat(r).isEqualTo("synced");
        }

        @Test
        @DisplayName("submitTask(ExceptionProcessor): 异常路径触发 error 回调")
        void exception_processor_error_path() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> errSeen = new AtomicReference<>();

            processor.submitTask(new CommonAsyncProcessor.ExceptionProcessor() {
                @Override public void actuator() { throw new RuntimeException("ex-flow"); }
                @Override public void error(Throwable e) { errSeen.set(e); latch.countDown(); }
            });

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(errSeen.get()).hasRootCauseMessage("ex-flow");
        }

    }

}
