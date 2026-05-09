# Galaxy Async — 完整参考

`cn.gsq.common.config.CommonAsyncProcessor`（构造器接收 `ExecutorService`，由 `CommonAutoConfig.buildConsumerQueueThreadPool()` 自动注册）

## 完整方法清单

```java
// 构造器
public CommonAsyncProcessor(ExecutorService service)

// 直接访问底层线程池（用于自定义提交方式）
@Getter ExecutorService getService()

// 内部 submit（protected，外部不可直接用）
protected Future<?>          submit(Runnable task)
protected <T> Future<T>      submit(Runnable task, T result)
protected <T> Future<T>      submit(Callable<T> task)

// 公开 submitTask
public <T> T  submitTask(SyncProcessor<T> processor)        // 同步阻塞
public void   submitTask(SimpleProcessor processor)         // 简单异步
public <T> void submitTask(AsyncProcessor<T> processor)     // 异步 + 回调 + 异常
public void   submitTask(ExceptionProcessor processor)      // 异步 + 异常
```

## 底层实现（`CompletableFuture` 流程）

### `submitTask(SyncProcessor<T>)`

```java
Future<T> future = service.submit(processor::actuator);
return future.get();   // 阻塞
```

`@SneakyThrows` 把 `InterruptedException` / `ExecutionException` 当 unchecked 抛出。

### `submitTask(SimpleProcessor)`

```java
service.submit(processor::actuator);
```

异常被 `Future` 吞掉。

### `submitTask(AsyncProcessor<T>)`

```java
T fallback = processor.declaration();
CompletableFuture
    .supplyAsync(processor::actuator, service)
    .whenComplete((result, error) -> {
        if (result != null) processor.callback(result);
    })
    .exceptionally(error -> {
        log.error("线程 {} 异常: {}", Thread.currentThread().getName(), error.getMessage(), error);
        processor.error(fallback, error);
        return fallback;
    });
```

注意：`whenComplete` 在**正常或异常**都会触发，但只有 `result != null` 才调 `callback`——这意味着如果 `actuator()` 抛异常，`whenComplete` 拿到的 `result` 是 `null`，`callback` 不会被误调。`error` 由 `exceptionally` 触发。

### `submitTask(ExceptionProcessor)`

```java
CompletableFuture
    .supplyAsync(processor::adorn, service)   // adorn() 默认实现：调 actuator() 后 return ""
    .exceptionally(error -> {
        processor.error(error);
        return null;
    });
```

`adorn()` 是接口的 `default` 方法：`actuator(); return "";`。`CompletableFuture.supplyAsync` 需要 `Supplier`，所以包了一层。

## 选型决策树

```
需要异步执行
├─ 不要返回值 / 不要错误回调  → SimpleProcessor
├─ 要等异步结果（同步阻塞）   → SyncProcessor<T>
├─ 要异步成功回调 + 失败处理 + 兜底值  → AsyncProcessor<T>
└─ 只关心失败                 → ExceptionProcessor
```

## 自定义线程池

库的默认 Bean：

```java
@Bean
@ConditionalOnMissingBean(CommonAsyncProcessor.class)
public CommonAsyncProcessor buildConsumerQueueThreadPool() {
    ExecutorService pool = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new ThreadFactoryBuilder().setNameFormat("galaxy-common-thread-%d").build(),
            new ThreadPoolExecutor.AbortPolicy());
    return new CommonAsyncProcessor(pool);
}
```

如果默认线程池不合适（例如希望有界队列、固定核心线程），声明自己的 Bean：

```java
@Bean
public CommonAsyncProcessor customAsyncProcessor() {
    ExecutorService pool = new ThreadPoolExecutor(
            10, 100, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            new ThreadFactoryBuilder().setNameFormat("my-async-%d").build(),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );
    return new CommonAsyncProcessor(pool);
}
```

`@ConditionalOnMissingBean` 会让默认 Bean 让位。
