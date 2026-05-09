---
name: galaxy-async
description: 提交后台异步任务到内置线程池：lambda 友好的 runAsync/supplyAsync/callSync、批量 invokeAll/invokeAny、延迟与周期 schedule、失败重试 submitWithRetry、ThreadLocal 上下文传递 wrapWithContext、线程池 stats 监控。当用户写"异步任务"、"线程池"、"async"、"CompletableFuture"、"后台执行"、"@Async 替代"、"延迟任务"、"定时任务"、"重试"、"并发批量"相关代码时使用。
---

# Galaxy Async

## 何时使用

下游项目需要**提交异步任务到后台执行**，覆盖：

- fire-and-forget（异常冒泡到 CF；带 `Consumer<Throwable>` 重载会自动打日志 + 触发回调）
- 异步带返回值，链式 `CompletableFuture` 组合
- 同步阻塞等结果（带超时）
- 一组任务并发跑，等全部完成或第一个完成
- 延迟 / 周期任务（替代 `@Scheduled`）
- 失败重试 + 固定间隔 backoff
- 异步任务里仍要有日志 traceId（MDC）/ 当前请求上下文
- 想拿当前线程池监控指标

不要为此手写 `@Async` + `ThreadPoolTaskExecutor` —— 库已经提供了 `CommonAsyncProcessor` 单例 Bean。

## 引入

`io.github.gaoshq7:common-boot:1.0.2`。`@Autowired` 注入即用。

```java
@Autowired
private CommonAsyncProcessor asyncProcessor;
```

## 核心 API（推荐使用）

### 异步执行（lambda 友好）

| 方法 | 用法 | 备注 |
|------|------|------|
| `runAsync(Runnable) → CompletableFuture<Void>` | `runAsync(() -> sendEmail())` | 简单异步；异常会冒泡到 CF |
| `runAsync(Runnable, Consumer<Throwable>) → CompletableFuture<Void>` | `runAsync(task, e -> alert(e))` | 自带错误回调；**异常被 onError 消费，外层 CF 正常完成（不再异常完成）**；onError 收到原始业务异常（已 unwrap CompletionException） |
| `supplyAsync(Callable<T>) → CompletableFuture<T>` | `supplyAsync(() -> queryById(id))` | 带返回值；checked exception 自动包装 |
| `supplyAsync(Callable<T>, Consumer<T>, Consumer<Throwable>) → CompletableFuture<T>` | `supplyAsync(task, this::onOk, this::onErr)` | 替代旧 AsyncProcessor；**onSuccess 总会触发**（即使结果 null）；**passthrough**——外层 CF 仍异常完成可继续 `.exceptionally(...)` 链式处理；onError 收到原始业务异常 |
| `callSync(Callable<T>, long, TimeUnit) → T` | `callSync(() -> heavy(), 3, TimeUnit.SECONDS)` | 同步阻塞 + 超时；超时/异常包装为 `IllegalStateException` |

### Future 风格（标准入口）

| 方法 | 说明 |
|------|------|
| `submit(Runnable) → Future<?>` | 标准 Future 提交，自己处理 |
| `submit(Runnable, T) → Future<T>` | 同上 + 固定返回值 |
| `submit(Callable<T>) → Future<T>` | 同上 |

### 批量

| 方法 | 说明 |
|------|------|
| `invokeAll(Collection<Callable<T>>, long, TimeUnit) → List<T>` | 等所有完成；超时未完成的 cancel 并抛 `IllegalStateException` |
| `invokeAny(Collection<Callable<T>>, long, TimeUnit) → T` | 第一个成功就返回，其余 cancel |

### 调度（延迟/周期）

| 方法 | 说明 |
|------|------|
| `schedule(Runnable, long, TimeUnit) → ScheduledFuture<?>` | 延迟一次执行 |
| `scheduleAtFixedRate(Runnable, long initial, long period, TimeUnit)` | 固定频率（按启动时间间隔） |
| `scheduleWithFixedDelay(Runnable, long initial, long delay, TimeUnit)` | 固定延迟（按结束到下次启动） |

注：调度由内部 scheduler（2 线程）触发，时间到时把任务 dispatch 到主线程池跑，不阻塞 scheduler。

### 重试

| 方法 | 说明 |
|------|------|
| `submitWithRetry(Callable<T>, int maxAttempts, Duration backoff) → CompletableFuture<T>` | 失败重试 N 次（含首次），每次失败后等 backoff 再试 |

### ThreadLocal 上下文传递

| 方法 | 说明 |
|------|------|
| `wrapWithContext(Runnable) → Runnable` | 装饰任务，运行时持有调用方的 MDC + RequestAttributes |
| `wrapWithContext(Callable<T>) → Callable<T>` | 同上，带返回值版本 |

### 监控与等待

| 方法 | 说明 |
|------|------|
| `getStats() → PoolStats` | 主线程池快照（活跃线程、队列、已完成、最大线程等） |
| `awaitQuiescence(long, TimeUnit) → boolean` | 等所有任务跑完（不关闭线程池），返回是否在超时前安静下来 |

### 容器关闭

实现 `DisposableBean.destroy()`，Spring 容器关闭时自动 `shutdown` 主线程池与 scheduler，最多等 10 秒后 `shutdownNow`。

## 典型用法

### 1. 简单异步（fire-and-forget）

```java
asyncProcessor.runAsync(() -> sendEmail(to, body));   // 异常自动冒泡到 CF
```

或自带错误处理：

```java
asyncProcessor.runAsync(() -> riskyOp(),
        e -> alertService.send("operation failed", e));
```

### 2. 异步带回调

```java
asyncProcessor.supplyAsync(
        () -> remoteService.queryUser(id),
        user -> cache.put(id, user),
        e    -> log.error("查询用户失败", e)
);
```

### 3. 链式组合

```java
CompletableFuture<Order> orderFuture = asyncProcessor.supplyAsync(() -> queryOrder(id));
CompletableFuture<User>  userFuture  = orderFuture.thenApply(o -> userDao.find(o.userId));
userFuture.thenAccept(u -> log.info("user: {}", u.name));
```

### 4. 同步等结果

```java
String result = asyncProcessor.callSync(() -> heavyCompute(), 3, TimeUnit.SECONDS);
// 超时或异常会抛 IllegalStateException，原异常在 e.getCause()
```

### 5. 批量并发

```java
List<Callable<UserInfo>> tasks = ids.stream()
        .map(id -> (Callable<UserInfo>) () -> remoteService.queryUser(id))
        .collect(toList());

List<UserInfo> users = asyncProcessor.invokeAll(tasks, 5, TimeUnit.SECONDS);
```

### 6. 延迟 / 周期

```java
asyncProcessor.schedule(() -> cache.evictExpired(), 30, TimeUnit.SECONDS);
asyncProcessor.scheduleAtFixedRate(() -> healthCheck(), 0, 1, TimeUnit.MINUTES);
```

### 7. 重试

```java
asyncProcessor.submitWithRetry(
        () -> remoteService.flakyCall(),
        3,
        Duration.ofMillis(500)
).thenAccept(result -> log.info("最终拿到: {}", result));
```

### 8. 上下文传递（保留 traceId）

```java
// web 请求线程：
Runnable task = asyncProcessor.wrapWithContext(() -> bizCall());
asyncProcessor.runAsync(task);
// 异步线程里 log 仍带 traceId、@Autowired 的 RequestContextHolder 仍可用
```

或一行解决：

```java
asyncProcessor.runAsync(asyncProcessor.wrapWithContext(() -> bizCall()));
```

### 9. 监控

```java
PoolStats stats = asyncProcessor.getStats();
log.info("active={}, queue={}, completed={}",
        stats.getActiveThreads(), stats.getQueueSize(), stats.getCompletedTaskCount());
```

## 默认线程池规格

| 参数 | 值 | 说明 |
|------|-----|------|
| 核心线程数 | `max(8, CPU * 2)` | 常驻 |
| 最大线程数 | **200** | 防止突发流量打爆 JVM |
| 队列 | `LinkedBlockingQueue(1024)` | 先入队、再扩线程 |
| 空闲存活 | 60 秒 | |
| 拒绝策略 | `CallerRunsPolicy` | 过载时调用线程兜底，提供天然背压 |
| 线程类型 | daemon | JVM 关闭时不阻塞退出 |
| 线程名前缀 | `galaxy-common-thread-` | |

调度器（scheduler）：2 个 daemon 线程，前缀 `galaxy-scheduler-`。

不满足时声明自己的 `CommonAsyncProcessor` Bean，`@ConditionalOnMissingBean` 会让位（详见 [reference.md](reference.md)）。

## ⚠️ 已弃用的 API（请尽快迁移）

下列 API 因 lambda 不友好（必须强转）、命名晦涩、语义陷阱等原因已 `@Deprecated`，仍可用，但请改用上方推荐方法：

| 旧 API | 推荐替代 | 弃用原因 |
|--------|----------|----------|
| `submitTask(SimpleProcessor)` | `runAsync(Runnable)` 或 `runAsync(Runnable, Consumer)` | lambda 必须强转 |
| `submitTask(SyncProcessor<T>)` | `callSync(Callable, long, TimeUnit)` | lambda 必须强转 + 无超时风险 |
| `submitTask(SyncProcessor<T>, long, TimeUnit)` | `callSync(Callable, long, TimeUnit)` | lambda 必须强转 |
| `submitTask(AsyncProcessor<T>)` | `supplyAsync(Callable, Consumer, Consumer)` | lambda 不友好；result == null 时 callback 不触发反直觉；命名晦涩 |
| `submitTask(ExceptionProcessor)` | `runAsync(Runnable, Consumer<Throwable>)` | lambda 必须强转 |
| 接口 `SimpleProcessor` | `Runnable` | — |
| 接口 `SyncProcessor<T>` | `Callable<T>` | — |
| 接口 `AsyncProcessor<T>` | `Callable<T>` + 两个 `Consumer` | — |
| 接口 `ExceptionProcessor` | `Runnable` + `Consumer<Throwable>` | — |

## 注意事项

- **ThreadLocal 不会自动传递**：`MDC`、`SecurityContextHolder`、`RequestContextHolder` 在异步线程都是空的。**用 `wrapWithContext(...)` 装饰任务**或主线程提前取出闭包传入。
- **`callSync` 必须带超时**：超时不会强制取消任务（任务还在线程池里跑），只是停止等待。要真取消用 `submit(Callable).get(timeout) + future.cancel(true)`。
- **回调式重载的两种语义不同，别混淆**：
  - `runAsync(Runnable, Consumer<Throwable>)` —— 异常**被消费**：onError 跑完后外层 CF 以 null **正常完成**。fire-and-forget 心智，不需要再 `.exceptionally(...)`。
  - `supplyAsync(Callable, Consumer, Consumer)` —— **passthrough**：onError 先跑，外层 CF 仍以原异常**异常完成**。可继续 `.thenApply / .exceptionally` 链式组合。
  - 两者的 onError 都会自动打 ERROR 日志，且**收到的是原始业务异常**（已 unwrap `CompletionException`）。
- **不带 Consumer 的版本（`runAsync(Runnable)` / `supplyAsync(Callable)`）异常冒泡到 `CompletableFuture`**：调用方需要 `.exceptionally(...)` 或 `.whenComplete(...)` 处理。
- **`invokeAll` 超时未完成的子任务被 cancel 并抛异常**：与 JDK `ExecutorService.invokeAll` 一致；要软超时用 `submit(Callable)` 自己控。
- **`submitWithRetry` 的 backoff 是固定间隔**：不是指数退避；需要指数退避请用 `resilience4j` 等专业库。
- **过载时 CallerRunsPolicy 会借用调用线程**：默认配置下，主池满 + 队列满 → 调用 `runAsync` 的线程会**自己跑这个任务**，提供天然背压。**不要在 web 请求线程里高并发提交极重任务**，否则请求线程被借走。
- **scheduler 是独立线程池**（2 个 daemon），仅做调度；任务到时间会 dispatch 到主线程池执行，不会因调度任务多而互相影响。
- **`getStats()` 在自定义 ExecutorService 不是 ThreadPoolExecutor 时返回 `PoolStats.UNKNOWN`**（全 -1）。
- **destroy 顺序**：先关 scheduler 再关主线程池，避免周期任务在主池关闭后还触发调度。

更多：完整方法签名、`CompletableFuture` 流程、PoolStats 字段、与旧 API 的实现对照见 [reference.md](reference.md)。
