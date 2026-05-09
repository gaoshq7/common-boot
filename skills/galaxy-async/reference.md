# Galaxy Async — 完整参考

`cn.gsq.common.config.CommonAsyncProcessor`（实现 `DisposableBean`，由 `CommonAutoConfig.buildConsumerQueueThreadPool()` 自动注册）

## 完整方法清单

```java
// 构造器
public CommonAsyncProcessor(ExecutorService service)
public CommonAsyncProcessor(ExecutorService service, ScheduledExecutorService scheduler)

// 底层访问
@Getter ExecutorService          getService()
@Getter ScheduledExecutorService getScheduler()

// === 推荐 API：异步执行 ===
public Future<?>                  submit(Runnable task)
public <T> Future<T>              submit(Runnable task, T result)
public <T> Future<T>              submit(Callable<T> task)

public CompletableFuture<Void>    runAsync(Runnable task)
public CompletableFuture<Void>    runAsync(Runnable task, Consumer<Throwable> onError)

public <T> CompletableFuture<T>   supplyAsync(Callable<T> task)
public <T> CompletableFuture<T>   supplyAsync(Callable<T> task,
                                              Consumer<? super T> onSuccess,
                                              Consumer<Throwable> onError)

public <T> T                      callSync(Callable<T> task, long timeout, TimeUnit unit)

// === 推荐 API：批量 ===
public <T> List<T>                invokeAll(Collection<? extends Callable<T>> tasks,
                                              long timeout, TimeUnit unit)
public <T> T                      invokeAny(Collection<? extends Callable<T>> tasks,
                                              long timeout, TimeUnit unit)

// === 推荐 API：调度 ===
public ScheduledFuture<?>         schedule(Runnable task, long delay, TimeUnit unit)
public ScheduledFuture<?>         scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit)
public ScheduledFuture<?>         scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit)

// === 推荐 API：重试 ===
public <T> CompletableFuture<T>   submitWithRetry(Callable<T> task, int maxAttempts, Duration backoff)

// === 推荐 API：上下文 ===
public Runnable                   wrapWithContext(Runnable task)
public <T> Callable<T>            wrapWithContext(Callable<T> task)

// === 推荐 API：监控 ===
public PoolStats                  getStats()
public boolean                    awaitQuiescence(long timeout, TimeUnit unit)

// === @Deprecated 旧 API（保留兼容性，不要在新代码中使用） ===
@Deprecated public <T> T          submitTask(SyncProcessor<T> processor)
@Deprecated public <T> T          submitTask(SyncProcessor<T> processor, long timeout, TimeUnit unit)
@Deprecated public void           submitTask(SimpleProcessor processor)
@Deprecated public <T> void       submitTask(AsyncProcessor<T> processor)
@Deprecated public void           submitTask(ExceptionProcessor processor)

// === 容器关闭 ===
@Override public void             destroy()
```

## 默认线程池配置

```java
// 主线程池
int core = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
ExecutorService service = new ThreadPoolExecutor(
        core,                                    // corePoolSize
        200,                                     // maxPoolSize
        60L, TimeUnit.SECONDS,                   // keepAlive
        new LinkedBlockingQueue<>(1024),         // 有界队列，提供天然背压
        new ThreadFactoryBuilder()
                .setNameFormat("galaxy-common-thread-%d")
                .setDaemon(true)
                .build(),
        new ThreadPoolExecutor.CallerRunsPolicy()  // 过载时调用线程兜底
);

// scheduler（CommonAsyncProcessor 内部默认创建，2 daemon 线程）
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2,
        new ThreadFactoryBuilder().setNameFormat("galaxy-scheduler-%d").setDaemon(true).build());
```

主线程池可通过自定义 Bean 替换；scheduler 可通过双参数构造器注入。

## 推荐 API 实现细节

### `runAsync(Runnable)` / `runAsync(Runnable, Consumer<Throwable>)`

```java
public CompletableFuture<Void> runAsync(Runnable task) {
    return CompletableFuture.runAsync(task, service);   // 异常自然冒泡到 CF
}

public CompletableFuture<Void> runAsync(Runnable task, Consumer<Throwable> onError) {
    return CompletableFuture.runAsync(task, service)
            .handle((unused, error) -> {              // ← 用 handle 而非 whenComplete
                if (error != null) {
                    Throwable cause = unwrap(error);  // ← 去掉 CompletionException 包装
                    log.error("...", cause);
                    safeAccept(onError, cause);       // 给到原始业务异常
                }
                return null;                          // ← 消费异常：外层 CF 正常完成
            });
}
```

**关键点**：双参数版用 `handle()` 而非 `whenComplete()`——后者是 passthrough，外层 CF 仍异常完成。`handle()` 返回 `null` 让外层 CF 以 null 正常完成，调用方不需要再 `.exceptionally(...)`。`onError` 收到的是 `unwrap` 后的原始业务异常，不是 `CompletionException` 包装版。

### `supplyAsync(Callable<T>)` / 三参数版

```java
public <T> CompletableFuture<T> supplyAsync(Callable<T> task) {
    return CompletableFuture.supplyAsync(toSupplier(task), service);
}

public <T> CompletableFuture<T> supplyAsync(Callable<T> task,
                                              Consumer<? super T> onSuccess,
                                              Consumer<Throwable> onError) {
    return CompletableFuture.supplyAsync(toSupplier(task), service)
            .whenComplete((result, error) -> {        // ← 用 whenComplete 保留 passthrough
                if (error != null) {
                    Throwable cause = unwrap(error);  // ← 去掉 CompletionException 包装
                    log.error("...", cause);
                    safeAccept(onError, cause);
                } else {
                    safeAccept(onSuccess, result);    // 即使 result==null 也会调
                }
            });
}
```

`toSupplier`：`Callable.call()` 抛 checked exception，包成 `CompletionException` 让 CF 内部 unwrap。

**与 `runAsync(Runnable, Consumer)` 的语义差异**：此重载用 `whenComplete()`（passthrough），外层 CF 仍以原异常**异常完成**——保留 `T` 类型不被强制变成 null，且调用方可继续 `.thenApply / .exceptionally(...)` 链式组合。`onError` 仅作"被通知"，不消费异常。

**与旧 `AsyncProcessor` 的关键区别**：
- `onSuccess` 总会被调用（包括 result == null 的情况），不再有 null 陷阱。
- 不再要求实现 `declaration() / actuator() / callback() / error()` 四个方法，两个 lambda 直接用。
- `onError` 收到原始业务异常，旧 API 收到的是 `CompletionException`。

### `callSync(Callable<T>, long, TimeUnit)`

```java
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
```

异常封装为 `IllegalStateException`，原异常在 `getCause()`。中断会**正确恢复中断标志**。

**注意**：超时**不会取消任务**——任务仍在线程池跑。需要硬取消请用 `submit(Callable).get(timeout) + cancel(true)`。

### `submitWithRetry(Callable<T>, int, Duration)`

```java
public <T> CompletableFuture<T> submitWithRetry(Callable<T> task, int maxAttempts, Duration backoff) {
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
            scheduler.schedule(() -> attemptRetry(...), backoff.toMillis(), TimeUnit.MILLISECONDS);
        }
    });
}
```

- 用 scheduler 做 backoff 间隔，不阻塞主线程池。
- 失败时 unwrap `CompletionException` 把原始异常 `completeExceptionally` 出去。
- backoff 是固定间隔；指数退避请用 resilience4j。

### `invokeAll` / `invokeAny`

```java
public <T> List<T> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
    try {
        List<Future<T>> futures = service.invokeAll(tasks, timeout, unit);
        List<T> results = new ArrayList<>(futures.size());
        for (Future<T> f : futures) {
            if (f.isCancelled()) throw new IllegalStateException("invokeAll 子任务超时被取消");
            results.add(f.get());
        }
        return results;
    } catch (...) { ... }
}
```

转发到 `ExecutorService.invokeAll/invokeAny`，统一异常封装。`invokeAll` 超时会 cancel 未完成的 Future。

### `schedule*`

```java
public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
    return scheduler.schedule(() -> service.execute(safeRun(task)), delay, unit);
}
```

**关键点**：
- 触发由 scheduler 完成，到时间后**把任务再 dispatch 到 service 主池**执行。这样 scheduler 自己只占用 2 个线程，业务任务再重也不会阻塞调度。
- `safeRun` 包了 try-catch + 日志，避免任务异常打断后续周期。

### `wrapWithContext`

```java
public <T> Callable<T> wrapWithContext(Callable<T> task) {
    Map<String, String> mdcSnapshot = MDC.getCopyOfContextMap();
    RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
    return () -> runWithContext(mdcSnapshot, attrs, task);
}
```

`runWithContext` 在执行任务前后保存/恢复线程的 MDC + RequestAttributes。

**注意**：调用 `wrapWithContext(...)` 必须**在你想捕获上下文的线程里**（通常是 web 请求线程），不能等到提交任务时才在异步线程里调（那时上下文已是空）。

### `getStats() → PoolStats`

```java
@Getter
public static final class PoolStats {
    public static final PoolStats UNKNOWN = new PoolStats(-1, -1, -1, -1L, -1L, -1, -1);
    int  activeThreads;          // 正在执行任务的线程数
    int  poolSize;               // 当前池子里的线程数
    int  largestPoolSize;        // 历史最大线程数
    long taskCount;              // 总提交过的任务数
    long completedTaskCount;     // 已完成的任务数
    int  queueSize;              // 当前队列堆积数
    int  queueRemainingCapacity; // 队列剩余容量
}

public PoolStats getStats() {
    if (service instanceof ThreadPoolExecutor) {
        ThreadPoolExecutor pool = (ThreadPoolExecutor) service;
        return new PoolStats(pool.getActiveCount(), pool.getPoolSize(), pool.getLargestPoolSize(),
                pool.getTaskCount(), pool.getCompletedTaskCount(),
                pool.getQueue().size(), pool.getQueue().remainingCapacity());
    }
    return PoolStats.UNKNOWN;
}
```

注：默认 Tomcat `ThreadPoolExecutor` 也继承 JDK 的，所以默认池可拿到完整指标。

### `awaitQuiescence(long, TimeUnit)`

```java
public boolean awaitQuiescence(long timeout, TimeUnit unit) {
    if (!(service instanceof ThreadPoolExecutor)) return false;
    ThreadPoolExecutor pool = (ThreadPoolExecutor) service;
    long deadline = System.nanoTime() + unit.toNanos(timeout);
    while (System.nanoTime() < deadline) {
        if (pool.getActiveCount() == 0 && pool.getQueue().isEmpty()) return true;
        Thread.sleep(50L);
    }
    return false;
}
```

50ms polling。仅观察，不调用 shutdown，等待完成后线程池仍可继续接任务。

### `destroy()`

```java
@Override
public void destroy() {
    shutdownPool(scheduler, "scheduler");   // 先关 scheduler 避免周期任务再触发
    shutdownPool(service, "service");
}

private void shutdownPool(ExecutorService pool, String name) {
    pool.shutdown();
    if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
        log.warn("...{}...", name);
        pool.shutdownNow();
    }
}
```

## 异常隔离矩阵

| 场景 | 行为 |
|------|------|
| `runAsync(Runnable)` 任务异常 | 冒泡到 CompletableFuture，需调用方处理 |
| `runAsync(Runnable, Consumer<Throwable>)` 任务异常 | ERROR 日志 + 调 onError（收到原异常）；**外层 CF 以 null 正常完成**（异常被消费） |
| `runAsync(...)` 的 onError 自身异常 | ERROR 日志，不污染外层 |
| `supplyAsync(Callable)` 任务异常 | 冒泡到 CompletableFuture |
| `supplyAsync(Callable, ok, err)` 任务异常 | ERROR 日志 + 调 err（收到原异常）；ok 不会被误调；**外层 CF 仍以原异常完成**（passthrough，可继续 `.exceptionally`） |
| `supplyAsync(...)` 的 ok / err 自身异常 | ERROR 日志，不污染外层 |
| `callSync` 任务异常 | 包装为 `IllegalStateException` 抛给调用方，原异常在 `getCause()` |
| `callSync` 中断 | 恢复中断标志 + 抛 `IllegalStateException` |
| `callSync` 超时 | 抛 `IllegalStateException`（任务不会被取消，仍在跑） |
| `invokeAll` 子任务超时 | cancel + 抛 `IllegalStateException` |
| `invokeAll` 子任务异常 | 包装为 `IllegalStateException` 抛给调用方 |
| `submit(Runnable/Callable)` 任务异常 | 封装在 `Future`，需 `future.get()` 才拿到 |
| `schedule*` 任务异常 | ERROR 日志（`safeRun`），不影响后续周期 |
| `submitWithRetry` 任务异常 | 重试 maxAttempts 次仍失败时 `completeExceptionally` 原异常 |

## 选型决策树

```
需要异步执行
├─ 不要返回值，要简单                    → runAsync(Runnable)
├─ 不要返回值，要错误处理                → runAsync(Runnable, Consumer<Throwable>)
├─ 要返回值，要链式组合                  → supplyAsync(Callable<T>) → CompletableFuture<T>
├─ 要返回值 + 成功/失败回调              → supplyAsync(Callable<T>, ok, err)
├─ 要等结果（同步阻塞 + 超时）           → callSync(Callable<T>, t, unit)
├─ 提交一组、等全部                       → invokeAll
├─ 提交一组、谁先成功用谁                 → invokeAny
├─ 失败重试                               → submitWithRetry
├─ 延迟一次 / 周期触发                    → schedule / scheduleAtFixedRate / scheduleWithFixedDelay
└─ 想自己控 Future（取消、批量等待等）    → submit(Runnable/Callable) → Future
```

## 旧 API 与新 API 迁移对照

```java
// 旧
asyncProcessor.submitTask((CommonAsyncProcessor.SimpleProcessor) () -> doSomething());
// 新
asyncProcessor.runAsync(() -> doSomething());

// 旧
String r = asyncProcessor.submitTask((CommonAsyncProcessor.SyncProcessor<String>) () -> compute());
// 新（必须带超时）
String r = asyncProcessor.callSync(() -> compute(), 3, TimeUnit.SECONDS);

// 旧
asyncProcessor.submitTask(new CommonAsyncProcessor.AsyncProcessor<User>() {
    public User declaration() { return null; }
    public User actuator() { return userDao.find(id); }
    public void callback(User u) { cache.put(u); }
    public void error(User f, Throwable e) { log.error("失败", e); }
});
// 新
asyncProcessor.supplyAsync(
        () -> userDao.find(id),
        u -> cache.put(u),       // 即使 u==null 也会调（旧 API 不会）
        e -> log.error("失败", e)
);

// 旧
asyncProcessor.submitTask(new CommonAsyncProcessor.ExceptionProcessor() {
    public void actuator() { riskyOp(); }
    public void error(Throwable e) { alert(e); }
});
// 新
asyncProcessor.runAsync(() -> riskyOp(), e -> alert(e));
```

## 自定义线程池

库的默认 Bean：

```java
@Bean
@ConditionalOnMissingBean(CommonAsyncProcessor.class)
public CommonAsyncProcessor buildConsumerQueueThreadPool() { ... }
```

声明自己的 Bean 即可让默认让位：

```java
@Bean
public CommonAsyncProcessor customAsyncProcessor() {
    ExecutorService pool = new ThreadPoolExecutor(
            16, 64, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(2048),
            new ThreadFactoryBuilder().setNameFormat("biz-async-%d").setDaemon(true).build(),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );
    return new CommonAsyncProcessor(pool);   // 单参数构造器，scheduler 用默认
}
```

也可同时自定义 scheduler：

```java
@Bean
public CommonAsyncProcessor customAsyncProcessor() {
    ExecutorService pool = ...;
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4, ...);
    return new CommonAsyncProcessor(pool, scheduler);
}
```

`CommonAsyncProcessor` 的 `destroy()` 会一并 shutdown 你传入的两个池子，无需手动管理。

## ThreadLocal 上下文传递（背景）

异步任务**不会自动继承**主线程的 ThreadLocal（MDC、SecurityContext、RequestAttributes）。在 web 请求里使用时，三种做法：

### 做法 1：`wrapWithContext`（推荐）

```java
asyncProcessor.runAsync(asyncProcessor.wrapWithContext(() -> bizCall()));
```

库内部自动捕获 + 还原 MDC 与 RequestAttributes。

### 做法 2：手动捕获 + 闭包传入

```java
String traceId = MDC.get("traceId");
RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
asyncProcessor.runAsync(() -> {
    MDC.put("traceId", traceId);
    RequestContextHolder.setRequestAttributes(attrs);
    try { bizCall(); }
    finally { MDC.clear(); RequestContextHolder.resetRequestAttributes(); }
});
```

### 做法 3：用 `wrapWithContext(Callable<T>)` 配合 `supplyAsync`

```java
asyncProcessor.supplyAsync(asyncProcessor.wrapWithContext(() -> queryUser(id)))
        .thenAccept(user -> log.info("ok"));   // 这里仍有 traceId
```

注意：链式 `.thenAccept(...)` 默认在完成线程上跑（通常仍在异步线程），如果要回到原线程上下文，把回调本身也包 `wrapWithContext`。
