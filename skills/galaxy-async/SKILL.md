---
name: galaxy-async
description: 提交后台异步任务到内置线程池，支持简单异步、同步阻塞获取结果、带回调的异步、带异常处理的异步四种模式。当用户写"异步任务"、"线程池"、"async"、"CompletableFuture"、"后台执行"、"@Async 替代"相关代码时使用。
---

# Galaxy Async

## 何时使用

下游项目需要**提交异步任务到后台执行**：

- 简单 fire-and-forget（不关心结果）
- 需要等待异步结果（同步阻塞）
- 需要异步结果的回调，并对异常做处理
- 只关心"任务失败时怎么办"，结果无所谓

不要为此手写 `@Async` + `ThreadPoolTaskExecutor` —— 库已经提供了 `CommonAsyncProcessor` 单例 Bean。

## 引入

`io.github.gaoshq7:common-boot:1.0.2`。`@Autowired` 注入即用。

## 核心做法

```java
@Autowired
private CommonAsyncProcessor asyncProcessor;
```

然后用 4 个 `submitTask(...)` 重载之一，每个对应一种 Processor 接口。**`submit(Runnable/Callable)` 方法是 `protected`，外部无法直接用**——必须走 `submitTask`。

## 核心 API

| 方法 | 用法 | 何时选 |
|------|------|--------|
| `submitTask(SimpleProcessor)` | 实现 `void actuator()` | 不要返回值、不需要异常处理（异常会被 `Future` 吞掉） |
| `submitTask(SyncProcessor<T>) → T` | 实现 `T actuator()`，**调用方阻塞等结果** | 需要异步执行但同步拿值（注意：发起线程会卡住） |
| `submitTask(AsyncProcessor<T>)` | 实现 `declaration / actuator / callback / error` | 异步 + 成功回调 + 异常处理 + 失败兜底值 |
| `submitTask(ExceptionProcessor)` | 实现 `actuator / error` | 只关心失败，用例最简洁 |

### 接口定义

```java
public interface SimpleProcessor {
    void actuator();
}

public interface SyncProcessor<T> {
    T actuator();
}

public interface AsyncProcessor<T> {
    T declaration();              // 兜底值（失败时也会被 callback 用作回滚）
    T actuator();                 // 异步执行体
    void callback(T t);           // 成功回调（result 非 null 才触发）
    void error(T t, Throwable e); // 异常回调（t 为 declaration() 返回值）
}

public interface ExceptionProcessor {
    void actuator();
    void error(Throwable e);
}
```

## 典型用法

### 简单 fire-and-forget

```java
asyncProcessor.submitTask((CommonAsyncProcessor.SimpleProcessor) () -> {
    log.info("异步打日志");
});
```

### 同步阻塞获取结果

```java
String result = asyncProcessor.submitTask(
        (CommonAsyncProcessor.SyncProcessor<String>) () -> heavyComputation()
);
```

### 异步 + 回调 + 异常处理

```java
asyncProcessor.submitTask(new CommonAsyncProcessor.AsyncProcessor<String>() {
    @Override
    public String declaration() {
        return "FAILED";   // 失败时 callback 拿不到，但 error 第一个参数是它
    }
    @Override
    public String actuator() {
        return remoteService.call();
    }
    @Override
    public void callback(String result) {
        log.info("成功: {}", result);
    }
    @Override
    public void error(String fallback, Throwable e) {
        log.error("失败，兜底={}, 原因={}", fallback, e.getMessage());
    }
});
```

### 只关心失败

```java
asyncProcessor.submitTask(new CommonAsyncProcessor.ExceptionProcessor() {
    @Override
    public void actuator() {
        riskyOp();
    }
    @Override
    public void error(Throwable e) {
        alertService.send("操作失败", e);
    }
});
```

## 线程池规格

| 参数 | 值 |
|------|-----|
| 核心线程数 | 0 |
| 最大线程数 | `Integer.MAX_VALUE` |
| 队列 | `SynchronousQueue`（不缓冲） |
| 空闲存活 | 60 秒 |
| 拒绝策略 | `AbortPolicy` |
| 线程名前缀 | `galaxy-common-thread-` |

实现是 `org.apache.tomcat.util.threads.ThreadPoolExecutor`（Tomcat 改进版，支持线程数动态扩展）。

注：核心线程 0 + `SynchronousQueue` 意味着**几乎每个任务都会创建新线程**，60 秒内闲置自动回收。适合突发短任务，**不适合稳态高吞吐场景**——若有这类需求，自定义 `CommonAsyncProcessor` Bean 替换默认（`@ConditionalOnMissingBean` 允许覆盖）。

## 注意事项

- `submit(Runnable)`、`submit(Runnable, T)`、`submit(Callable<T>)` 是 **protected**，下游访问不到。需要 `Future` 直接控制时，自己注入 `asyncProcessor.getService()` 拿底层 `ExecutorService`。
- `SimpleProcessor` 的异常会**默默吞掉**（任务里抛错没人处理）——需要错误日志请用 `ExceptionProcessor`。
- `AsyncProcessor.callback` 仅在 `actuator()` 返回 **非 null** 时被调用；若你的成功结果可能是 `null`，要么改返回包装类型，要么换 `ExceptionProcessor`。
- `SyncProcessor` 是同步阻塞——发起线程会等到任务完成。**不要在 web 请求线程里用它处理慢任务**（请求线程会卡）。
- 自定义线程池：声明自己的 `CommonAsyncProcessor` Bean 即可（库的默认带 `@ConditionalOnMissingBean`，被你覆盖）。

更多：底层实现、`CompletableFuture` 流程见 [reference.md](reference.md)。
