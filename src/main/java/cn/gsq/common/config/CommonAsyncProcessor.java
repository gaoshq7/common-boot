package cn.gsq.common.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Project : galaxy
 * Class : cn.gsq.common.config.CommonAsyncProcessor
 *
 * @author : gsq
 * @date : 2021-07-23 14:05
 * @note : It's not technology, it's art !
 **/
@Slf4j
public class CommonAsyncProcessor implements DisposableBean {

    /**
     * 关闭线程池时等待已提交任务完成的最长时间（秒）。
     */
    private static final long SHUTDOWN_AWAIT_SECONDS = 10L;

    @Getter
    private final ExecutorService service;

    /**
     * 内部维护的调度线程池，仅用于触发延迟/周期任务，时间到时把任务再 dispatch 到主 service 执行，
     * 避免占用 scheduler 线程。
     */
    @Getter
    private final ScheduledExecutorService scheduler;

    public CommonAsyncProcessor(ExecutorService service) {
        this(service, defaultScheduler());
    }

    public CommonAsyncProcessor(ExecutorService service, ScheduledExecutorService scheduler) {
        this.service = Objects.requireNonNull(service, "service");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    private static ScheduledExecutorService defaultScheduler() {
        return Executors.newScheduledThreadPool(2, new ThreadFactoryBuilder()
                .setNameFormat("galaxy-scheduler-%d")
                .setDaemon(true)
                .build());
    }

    // ============================================================
    // ===== 推荐使用的新 API（lambda 友好、CompletableFuture 风格）
    // ============================================================

    /**
     * 提交一个 Runnable，返回 Future（标准入口）。
     */
    public Future<?> submit(Runnable task) {
        return service.submit(task);
    }

    /**
     * 提交一个 Runnable + 固定返回值。
     */
    public <T> Future<T> submit(Runnable task, T result) {
        return service.submit(task, result);
    }

    /**
     * 提交一个 Callable，返回 Future（标准入口）。
     */
    public <T> Future<T> submit(Callable<T> task) {
        return service.submit(task);
    }

    /**
     * 异步执行 Runnable，返回 CompletableFuture，可链式 .thenRun / .whenComplete / .allOf 等。
     * actuator 内的异常会冒泡到 CompletableFuture，不再单独打日志（避免重复）。
     */
    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, service);
    }

    /**
     * 异步执行 Runnable，自带 onError 回调。
     * 异常被 onError 消费后，外层 CompletableFuture 会以 null 正常完成（不再异常完成），
     * onError 收到的是原始业务异常（已自动 unwrap CompletionException）。
     * onError 自身抛异常会被捕获打日志，不污染外层 Future。
     */
    public CompletableFuture<Void> runAsync(Runnable task, Consumer<Throwable> onError) {
        return CompletableFuture.runAsync(task, service)
                .handle((unused, error) -> {
                    if (error != null) {
                        Throwable cause = unwrap(error);
                        log.error("线程 {} 异步任务执行异常: {}",
                                Thread.currentThread().getName(), cause.getMessage(), cause);
                        safeAccept(onError, cause);
                    }
                    return null;
                });
    }

    /**
     * 异步执行 Callable，返回 CompletableFuture<T>。Checked exception 会被包装为 CompletionException。
     */
    public <T> CompletableFuture<T> supplyAsync(Callable<T> task) {
        return CompletableFuture.supplyAsync(toSupplier(task), service);
    }

    /**
     * 异步执行 Callable + 成功/失败回调。
     * 与 runAsync 双参数版不同：此重载保留 passthrough 行为——外层 CompletableFuture 仍会以原异常
     * 完成（异常路径），让调用方可以继续 .thenApply / .exceptionally 链式处理。回调先于链式触发。
     * onError 收到的是原始业务异常（已自动 unwrap CompletionException）。
     * 回调内部异常会被捕获打日志，不污染外层 Future。
     * 与旧 AsyncProcessor 的区别：onSuccess **总会**被调用（即使结果为 null），不再有 null 陷阱。
     */
    public <T> CompletableFuture<T> supplyAsync(Callable<T> task,
                                                 Consumer<? super T> onSuccess,
                                                 Consumer<Throwable> onError) {
        return CompletableFuture.supplyAsync(toSupplier(task), service)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        Throwable cause = unwrap(error);
                        log.error("线程 {} 异步任务执行异常: {}",
                                Thread.currentThread().getName(), cause.getMessage(), cause);
                        safeAccept(onError, cause);
                    } else {
                        safeAccept(onSuccess, result);
                    }
                });
    }

    /**
     * 异步提交、当前线程同步等待结果（必须带超时，避免死等）。
     * 任务异常 / 超时 / 中断都会包装为 IllegalStateException 抛给调用方。超时不会强制取消任务。
     */
    public <T> T callSync(Callable<T> task, long timeout, TimeUnit unit) {
        try {
            return submit(task).get(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待异步任务时被中断", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("异步任务执行异常", e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("异步任务等待超时", e);
        }
    }

    /**
     * 异步执行 + 失败重试。最多 maxAttempts 次（首次也算一次），每次失败后等待 backoff 再重试。
     */
    public <T> CompletableFuture<T> submitWithRetry(Callable<T> task, int maxAttempts, Duration backoff) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts 必须 >= 1");
        }
        Objects.requireNonNull(backoff, "backoff");
        CompletableFuture<T> result = new CompletableFuture<>();
        attemptRetry(task, maxAttempts, backoff, 0, result);
        return result;
    }

    private <T> void attemptRetry(Callable<T> task, int maxAttempts, Duration backoff,
                                   int attempt, CompletableFuture<T> result) {
        supplyAsync(task).whenComplete((value, error) -> {
            if (error == null) {
                result.complete(value);
            } else if (attempt + 1 >= maxAttempts) {
                result.completeExceptionally(unwrap(error));
            } else {
                log.warn("第 {}/{} 次重试，原因: {}", attempt + 1, maxAttempts, error.getMessage());
                scheduler.schedule(() -> attemptRetry(task, maxAttempts, backoff, attempt + 1, result),
                        backoff.toMillis(), TimeUnit.MILLISECONDS);
            }
        });
    }

    /**
     * 提交一组任务，等所有完成后按提交顺序返回结果。超时未完成的任务会被 cancel 并抛 IllegalStateException。
     */
    public <T> List<T> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
        try {
            List<Future<T>> futures = service.invokeAll(tasks, timeout, unit);
            List<T> results = new ArrayList<>(futures.size());
            for (Future<T> f : futures) {
                if (f.isCancelled()) {
                    throw new IllegalStateException("invokeAll 子任务超时被取消");
                }
                results.add(f.get());
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("invokeAll 等待时被中断", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("invokeAll 子任务异常", e.getCause());
        }
    }

    /**
     * 提交一组任务，第一个成功完成的就返回，其余取消。
     */
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
        try {
            return service.invokeAny(tasks, timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("invokeAny 等待时被中断", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("invokeAny 子任务全部失败", e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("invokeAny 等待超时", e);
        }
    }

    /**
     * 延迟执行任务。调度由内部 scheduler 触发，到时再 dispatch 到主 service 执行（不占用 scheduler 线程）。
     */
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return scheduler.schedule(() -> service.execute(safeRun(task)), delay, unit);
    }

    /**
     * 周期性执行（按固定频率：上一次启动到下一次启动的间隔为 period）。
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return scheduler.scheduleAtFixedRate(() -> service.execute(safeRun(task)), initialDelay, period, unit);
    }

    /**
     * 周期性执行（按固定延迟：上一次结束到下一次启动的间隔为 delay）。
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        return scheduler.scheduleWithFixedDelay(() -> service.execute(safeRun(task)), initialDelay, delay, unit);
    }

    /**
     * 装饰 Runnable，让它在异步执行时仍持有当前线程的 MDC（日志 traceId）与 RequestAttributes（请求上下文）。
     */
    public Runnable wrapWithContext(Runnable task) {
        Map<String, String> mdcSnapshot = MDC.getCopyOfContextMap();
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        return () -> {
            Map<String, String> origMdc = MDC.getCopyOfContextMap();
            RequestAttributes origAttrs = RequestContextHolder.getRequestAttributes();
            try {
                applyMdc(mdcSnapshot);
                if (attrs != null) {
                    RequestContextHolder.setRequestAttributes(attrs);
                }
                task.run();
            } finally {
                applyMdc(origMdc);
                if (origAttrs != null) {
                    RequestContextHolder.setRequestAttributes(origAttrs);
                } else {
                    RequestContextHolder.resetRequestAttributes();
                }
            }
        };
    }

    /**
     * 装饰 Callable，让它在异步执行时仍持有当前线程的 MDC 与 RequestAttributes。
     */
    public <T> Callable<T> wrapWithContext(Callable<T> task) {
        Map<String, String> mdcSnapshot = MDC.getCopyOfContextMap();
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        return () -> runWithContext(mdcSnapshot, attrs, task);
    }

    /**
     * 获取主线程池运行时统计信息（活跃线程、队列堆积、已完成任务等）。
     * 若底层 ExecutorService 不是 ThreadPoolExecutor，返回全 -1 的占位值。
     */
    public PoolStats getStats() {
        if (service instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor pool = (ThreadPoolExecutor) service;
            return new PoolStats(
                    pool.getActiveCount(),
                    pool.getPoolSize(),
                    pool.getLargestPoolSize(),
                    pool.getTaskCount(),
                    pool.getCompletedTaskCount(),
                    pool.getQueue().size(),
                    pool.getQueue().remainingCapacity()
            );
        }
        return PoolStats.UNKNOWN;
    }

    /**
     * 等待主线程池静默（无活跃任务、队列为空），最多等 timeout。常用于批处理脚本结尾。
     * 不调用 shutdown —— 仅观察，等待完成后线程池仍可继续接收任务。
     */
    public boolean awaitQuiescence(long timeout, TimeUnit unit) {
        if (!(service instanceof ThreadPoolExecutor)) {
            return false;
        }
        ThreadPoolExecutor pool = (ThreadPoolExecutor) service;
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (pool.getActiveCount() == 0 && pool.getQueue().isEmpty()) {
                return true;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    // ============================================================
    // ===== 兼容旧 API（@Deprecated，请迁移到上方新方法）
    // ============================================================

    /**
     * @deprecated lambda 不友好（必须强转），无超时风险。请改用 {@link #callSync(Callable, long, TimeUnit)}。
     */
    @Deprecated
    public <T> T submitTask(SyncProcessor<T> processor) {
        try {
            return submit(processor::actuator).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待异步任务时被中断", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("异步任务执行异常", e.getCause());
        }
    }

    /**
     * @deprecated lambda 不友好（必须强转）。请改用 {@link #callSync(Callable, long, TimeUnit)}。
     */
    @Deprecated
    public <T> T submitTask(SyncProcessor<T> processor, long timeout, TimeUnit unit) {
        return callSync(processor::actuator, timeout, unit);
    }

    /**
     * @deprecated lambda 不友好（必须强转）。请改用 {@link #runAsync(Runnable)} 或 {@link #runAsync(Runnable, Consumer)}。
     */
    @Deprecated
    public void submitTask(SimpleProcessor processor) {
        service.execute(() -> {
            try {
                processor.actuator();
            } catch (Throwable t) {
                log.error("线程 {} 异步任务执行异常: {}", Thread.currentThread().getName(), t.getMessage(), t);
            }
        });
    }

    /**
     * @deprecated lambda 不友好；result == null 时不调 callback 反直觉；命名晦涩。
     * 请改用 {@link #supplyAsync(Callable, Consumer, Consumer)}。
     */
    @Deprecated
    public <T> void submitTask(AsyncProcessor<T> processor) {
        T fallback = processor.declaration();
        CompletableFuture
                .supplyAsync(processor::actuator, service)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("线程 {} 异步任务执行异常: {}",
                                Thread.currentThread().getName(), error.getMessage(), error);
                        safeAsyncError(processor, fallback, error);
                    } else if (result != null) {
                        safeAsyncCallback(processor, result);
                    }
                });
    }

    /**
     * @deprecated lambda 不友好（必须强转）。请改用 {@link #runAsync(Runnable, Consumer)}。
     */
    @Deprecated
    public void submitTask(ExceptionProcessor processor) {
        CompletableFuture
                .runAsync(processor::actuator, service)
                .whenComplete((unused, error) -> {
                    if (error != null) {
                        log.error("线程 {} 异步任务执行异常: {}",
                                Thread.currentThread().getName(), error.getMessage(), error);
                        safeExceptionError(processor, error);
                    }
                });
    }

    // ============================================================
    // ===== 容器关闭钩子
    // ============================================================

    /**
     * Spring 容器关闭时优雅关闭主线程池与 scheduler。
     */
    @Override
    public void destroy() {
        shutdownPool(scheduler, "scheduler");
        shutdownPool(service, "service");
    }

    private void shutdownPool(ExecutorService pool, String name) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(SHUTDOWN_AWAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("CommonAsyncProcessor.{} 在 {}s 内未优雅关闭，触发 shutdownNow", name, SHUTDOWN_AWAIT_SECONDS);
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }
    }

    // ============================================================
    // ===== 内部工具方法
    // ============================================================

    private static <T> java.util.function.Supplier<T> toSupplier(Callable<T> task) {
        return () -> {
            try {
                return task.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        };
    }

    private static Throwable unwrap(Throwable t) {
        return t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
    }

    private static Runnable safeRun(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable t) {
                log.error("线程 {} 调度任务执行异常: {}", Thread.currentThread().getName(), t.getMessage(), t);
            }
        };
    }

    private static <T> T runWithContext(Map<String, String> mdc, RequestAttributes attrs, Callable<T> task) throws Exception {
        Map<String, String> origMdc = MDC.getCopyOfContextMap();
        RequestAttributes origAttrs = RequestContextHolder.getRequestAttributes();
        try {
            applyMdc(mdc);
            if (attrs != null) {
                RequestContextHolder.setRequestAttributes(attrs);
            }
            return task.call();
        } finally {
            applyMdc(origMdc);
            if (origAttrs != null) {
                RequestContextHolder.setRequestAttributes(origAttrs);
            } else {
                RequestContextHolder.resetRequestAttributes();
            }
        }
    }

    private static void applyMdc(Map<String, String> mdc) {
        if (mdc != null) {
            MDC.setContextMap(mdc);
        } else {
            MDC.clear();
        }
    }

    private static <T> void safeAccept(Consumer<? super T> consumer, T value) {
        if (consumer == null) {
            return;
        }
        try {
            consumer.accept(value);
        } catch (Throwable t) {
            log.error("回调执行异常: {}", t.getMessage(), t);
        }
    }

    private static <T> void safeAsyncCallback(AsyncProcessor<T> processor, T result) {
        try {
            processor.callback(result);
        } catch (Throwable t) {
            log.error("异步任务 callback 回调异常: {}", t.getMessage(), t);
        }
    }

    private static <T> void safeAsyncError(AsyncProcessor<T> processor, T fallback, Throwable cause) {
        try {
            processor.error(fallback, cause);
        } catch (Throwable t) {
            log.error("异步任务 error 回调异常: {}", t.getMessage(), t);
        }
    }

    private static void safeExceptionError(ExceptionProcessor processor, Throwable cause) {
        try {
            processor.error(cause);
        } catch (Throwable t) {
            log.error("异步任务 error 回调异常: {}", t.getMessage(), t);
        }
    }

    // ============================================================
    // ===== 内部数据类型
    // ============================================================

    /**
     * 主线程池运行时统计快照。
     */
    @Getter
    public static final class PoolStats {

        public static final PoolStats UNKNOWN = new PoolStats(-1, -1, -1, -1L, -1L, -1, -1);

        private final int activeThreads;
        private final int poolSize;
        private final int largestPoolSize;
        private final long taskCount;
        private final long completedTaskCount;
        private final int queueSize;
        private final int queueRemainingCapacity;

        public PoolStats(int activeThreads, int poolSize, int largestPoolSize,
                         long taskCount, long completedTaskCount,
                         int queueSize, int queueRemainingCapacity) {
            this.activeThreads = activeThreads;
            this.poolSize = poolSize;
            this.largestPoolSize = largestPoolSize;
            this.taskCount = taskCount;
            this.completedTaskCount = completedTaskCount;
            this.queueSize = queueSize;
            this.queueRemainingCapacity = queueRemainingCapacity;
        }

        @Override
        public String toString() {
            return String.format("PoolStats{active=%d, pool=%d, largest=%d, tasks=%d, completed=%d, queue=%d/%d}",
                    activeThreads, poolSize, largestPoolSize, taskCount, completedTaskCount,
                    queueSize, queueSize + Math.max(queueRemainingCapacity, 0));
        }

    }

    /**
     * @deprecated 请改用 {@link Runnable}（配合 {@link #runAsync(Runnable)}）。
     */
    @Deprecated
    public interface SimpleProcessor {

        void actuator();

    }

    /**
     * @deprecated 请改用 {@link Callable}（配合 {@link #callSync(Callable, long, TimeUnit)} 或 {@link #supplyAsync(Callable)}）。
     */
    @Deprecated
    public interface SyncProcessor<T> {

        T actuator();

    }

    /**
     * @deprecated 请改用 {@link Callable} + 两个 {@link Consumer}（配合 {@link #supplyAsync(Callable, Consumer, Consumer)}）。
     */
    @Deprecated
    public interface AsyncProcessor<T> {

        T declaration();

        T actuator();

        void callback(T t);

        void error(T t, Throwable e);

    }

    /**
     * @deprecated 请改用 {@link Runnable} + {@link Consumer}（配合 {@link #runAsync(Runnable, Consumer)}）。
     */
    @Deprecated
    public interface ExceptionProcessor {

        void actuator();

        void error(Throwable e);

    }

}
