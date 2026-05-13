---
name: galaxy-event
description: 在同一个 Spring Boot 应用内通过自定义事件解耦模块通信——继承 GalaxyGeneralEvent 定义事件、用 @EventHandleClass + @EventHandleMethod(module=...) 按模块路由订阅、用 GalaxySpringUtil.publishEvent / tryPublishEvent 推送。当下游需要写"事件发布"、"event handler"、"模块解耦"、"GalaxyGeneralEvent"、"@EventHandleClass"、"@EventHandleMethod"、"publishEvent"相关代码时使用。
---

# Galaxy Event - 自定义事件的推送与接收

## 何时使用

下游项目（依赖 `io.github.gaoshq7:common-boot`）希望：

- 模块 A 把"某件事发生了"广播出去，模块 B/C/... 各自决定要不要响应——A 不持有 B/C 的引用
- 同一事件类型按"模块名"分流：handler 只接收自己关心的那部分
- 复用 Spring 容器，不引入额外的 MQ / EventBus

> ⚠️ 仅覆盖**自定义事件**主线。订阅 Spring 内置事件（`ContextRefreshedEvent` 等）、容器初始化钩子（`ApplicationEventLoad`）、全事件监听（`ApplicationEventClient`）不在本 skill 范围内。

## 全景

```
              ┌──────────────────────┐
              │ 你的代码：发布事件     │
              │ publishEvent(...)    │
              └──────────┬───────────┘
                         │
                         ▼
        ┌────────────────────────────────┐
        │ Spring ApplicationContext       │
        │  ↓ 转发给所有 ApplicationListener│
        └────────────────┬───────────────┘
                         ▼
        ┌────────────────────────────────┐
        │ GalaxySpringUtil.onApplicationEvent │
        │  ↓                              │
        │ EventHandleSelector.handleEvent │  ← 按事件类型 + module 路由
        └────────────────┬───────────────┘
                         ▼
              ┌──────────────────────┐
              │ 你的 @EventHandleClass│
              │ @EventHandleMethod    │
              └──────────────────────┘
```

## 标准用法

下游通常只做两件事：**(1) 定义事件并推送**，**(2) 定义 handler 并注册扫描包**。

### 1. 定义自定义事件

继承 `GalaxyGeneralEvent`，构造器把 `module`（路由键）和 `source`（事件负载）传给父类。

```java
package com.example.demo.event;

import cn.gsq.common.config.event.GalaxyGeneralEvent;

public class OrderPaidEvent extends GalaxyGeneralEvent {

    public OrderPaidEvent(Order order) {
        super("order", order);   // module = "order"，source = order 对象
    }
}
```

`GalaxyGeneralEvent` 自身提供：

| 字段 | 含义 |
|------|------|
| `date` | 事件发生时间（构造时填，`yyyy-MM-dd HH:mm:ss`） |
| `module` | 路由键，handler 用它过滤 |
| `source` | 事件负载（`Object`，你可以传任何 POJO） |

### 2. 推送事件

`GalaxySpringUtil` 暴露两个推送 API，按场景选用：

#### 2.1 `publishEvent(Object)` — 简洁，context 未就绪时**静默丢弃**

```java
import cn.gsq.common.config.GalaxySpringUtil;

GalaxySpringUtil.publishEvent(new OrderPaidEvent(order));
```

- 业务运行期（Web 请求处理中、`@PostConstruct` 之后、`ApplicationReadyEvent` 之后）使用——此时 context 必定已就绪
- context 未就绪时**没有任何输出**，事件直接丢失。**不要**在 `static` 块、`main` 方法、`setApplicationContext` 之前调用

#### 2.2 `tryPublishEvent(Object)` — 安全，context 未就绪时**返回 false 并打 warn**

```java
boolean ok = GalaxySpringUtil.tryPublishEvent(new OrderPaidEvent(order));
if (!ok) {
    // 自行决定降级路径：丢入队列等容器就绪后重发、写本地补偿、报错抛异常等
}
```

- 启动早期 / 不确定调用时机 / 想观测失败 → 选它
- 业务运行期 100% 确定 context 已起 → 两者等价，选 `publishEvent` 更简洁

### 3. 定义事件处理类

- 类上标 `@EventHandleClass`（标了的类会被**自动注册成单例 Bean**，无需自己加 `@Component`）
- 处理方法标 `@EventHandleMethod(module = "...")`
- 处理方法**有且仅有一个参数**，参数类型是事件类本身（必须是 `ApplicationEvent` 或其子类，`GalaxyGeneralEvent` 满足）
- 方法**可以是 `private`**（框架内部 `setAccessible(true)`）

```java
package com.example.demo.handler;

import cn.gsq.common.EventHandleClass;
import cn.gsq.common.EventHandleMethod;
import com.example.demo.event.OrderPaidEvent;

@EventHandleClass
public class OrderPaidHandler {

    @EventHandleMethod(module = "order")
    private void onPaid(OrderPaidEvent event) {
        Order order = (Order) event.getSource();
        // 写流水、发短信、扣库存……
    }
}
```

#### `module` 的路由语义

- `module = "order"` → **仅**当 `event.getModule().equals("order")` 时触发
- `module = ""`（默认值，可省略）→ 同类型事件**全部**触发，不论 module
- 同一个事件类可以被多个 handler 监听；不同 handler 互不影响

例子：同一份 `OrderPaidEvent`，三个 handler 不同的接收策略

```java
@EventHandleClass
public class Handlers {

    @EventHandleMethod(module = "order")       // 只收 module == "order"
    private void a(OrderPaidEvent e) { ... }

    @EventHandleMethod(module = "billing")     // 只收 module == "billing"（这条永远不触发，因为 OrderPaidEvent 的 module 写死了 "order"）
    private void b(OrderPaidEvent e) { ... }

    @EventHandleMethod                         // module 留空 → 任何 module 都收
    private void c(OrderPaidEvent e) { ... }
}
```

### 4. 注册 handler 包扫描

启动类构造 `GalaxyApplicationBuilder` 时调用 `.addEventHandlePaths(...)`，把 handler 所在包名传进去（**必须**，否则扫描不到，事件会无人响应）。

```java
import cn.gsq.common.GalaxyApplicationBuilder;

public class DemoApplication {

    public static void main(String[] args) {
        new GalaxyApplicationBuilder(DemoApplication.class)
                .addEventHandlePaths("com.example.demo.handler")
                .run(args);
    }
}
```

可以传多个包：`.addEventHandlePaths("com.example.a.handler", "com.example.b.handler")`。

## 注意事项

- **加载时机**：handler 扫描在 `GalaxySpringUtil` 的 `ApplicationContextAware.setApplicationContext` 回调里**启动期一次性完成**（早于 `ApplicationReadyEvent`）。前提是 `addEventHandlePaths(...)` 在 `builder.run(args)` **之前**调用——这是硬约束。
- **handler 是单例 Bean**：标 `@EventHandleClass` 的类被自动注册为容器单例，默认 bean name 是 `lowerFirst(SimpleName)`（如 `OrderPaidHandler` → `"orderPaidHandler"`），与 Spring `@Component` 默认约定一致。`@Autowired` 按类型自动注入；如果你要 `@Qualifier("xxx")` 按名引用，按 `lowerFirst` 写。
- **同 SimpleName 会冲突**：不同包下两个 handler 类如果 `SimpleName` 相同（如 `a.Foo` 和 `b.Foo`），后一个注册会失败、被框架 log + 跳过，**该 handler 不生效**。请保证 handler 类名唯一。
- **handler 是同一容器内的单例**：可以 `@Autowired` 注入其它 Bean、用 `@Value` 读配置。但**不要**在 handler 里持有可变状态——同一实例会被并发调用。
- **事件类型按 `Class` 精确匹配**：handler 监听 `OrderPaidEvent` **不会**自动接收它的子类事件。如果你有 `VipOrderPaidEvent extends OrderPaidEvent`，要单独写一个 handler 方法。
- **handler 方法签名**：必须是「一个参数 + 参数是 `ApplicationEvent` 子类」。其它写法（无参、多参、参数不是事件）会在加载时抛 `RuntimeException("处理函数不符合规范...")`，框架 log + 跳过该方法（其它 handler 不受影响）。
- **同步执行**：handler 在发布事件的同一个线程内被反射调用。耗时操作（DB 写入、远程调用）请自己 `@Async` 或丢线程池，否则会阻塞调用方。
- **异常被吞**：handler 抛异常会被框架 `log.error` 后吞掉，**不会**回传给发布方、也**不会**阻断其它 handler。需要"全部成功才提交"的场景请别用事件，改用直接调用 + 事务。
- **module 路由只对 `GalaxyGeneralEvent` 生效**——本 skill 不覆盖其它情况，可忽略。
- **不要发布 `null`**：`publishEvent(null)` / `tryPublishEvent(null)` 会让 Spring 抛 NPE。
- **包扫描不重复**：同一个包重复 `addEventHandlePaths` 只生效一次。
- **容器关闭会清状态**：`ContextClosedEvent` 触发时框架自动清空 handler 注册表，集成测试 `@DirtiesContext` 或 DevTools 热重启不会残留旧 handler 引用。

## 速查

| 元素 | 全限定名 | 作用 |
|------|---------|------|
| 事件父类 | `cn.gsq.common.config.event.GalaxyGeneralEvent` | 继承它并传 `module` / `source` |
| 处理类注解 | `cn.gsq.common.EventHandleClass` | 标 class，自动注册为单例 Bean |
| 处理方法注解 | `cn.gsq.common.EventHandleMethod` | 标 method，`module` 参数控制路由 |
| 推送（简洁） | `cn.gsq.common.config.GalaxySpringUtil#publishEvent(Object)` | context 未就绪静默丢失 |
| 推送（安全） | `cn.gsq.common.config.GalaxySpringUtil#tryPublishEvent(Object)` | context 未就绪返回 false + 打 warn |
| 注册扫描包 | `cn.gsq.common.GalaxyApplicationBuilder#addEventHandlePaths(String...)` | 启动期调用一次 |
