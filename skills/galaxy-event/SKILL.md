---
name: galaxy-event
description: 模块间通过事件解耦通信，定义自定义事件类并按 module 路由订阅；订阅 Spring 内置事件（ContextRefreshed/Closed/RequestHandled 等）；监听全部应用事件。当用户写"事件发布"、"event listener"、"模块解耦"、"GalaxyGeneralEvent"、"@EventHandleClass"、"publishEvent"相关代码时使用。
---

# Galaxy Event

## 何时使用

下游项目需要：

- **模块 A 通知模块 B**，但不想直接耦合（A 发事件，B 订阅）
- 自定义事件类型，按"模块名（module）"分发
- 订阅 Spring 自带事件（`ContextRefreshedEvent`、`ContextClosedEvent`、`ServletRequestHandledEvent`、`ApplicationReadyEvent` 等）
- 在容器初始化完成那一刻执行钩子（`ApplicationEventLoad`）
- 监听**全部**应用事件做统一处理（`ApplicationEventClient`）

## 引入

`io.github.gaoshq7:common-boot:1.0.2`。

## 核心做法

### 路线 A：自定义事件 + 模块路由

1. **定义事件类**：继承 `GalaxyGeneralEvent`，构造器传 `module`（路由键）和 `source`（事件内容）
2. **定义处理类**：标 `@EventHandleClass`，方法上标 `@EventHandleMethod(module = "xxx")`，方法第一参数是事件类型
3. **注册扫描包**：启动类 `builder.addEventHandlePaths("com.example.events")`
4. **发布**：`GalaxySpringUtil.publishEvent(new MyEvent("moduleA", payload))`

`@EventHandleMethod.module`：

- 留空 `""`：该方法接收**所有** module 的同类事件
- 填具体值：只接收 `event.getModule().equals(module)` 的

### 路线 B：订阅 Spring 内置事件

同样用 `@EventHandleClass` + `@EventHandleMethod`，方法第一参数填 Spring 事件类型即可（如 `ContextRefreshedEvent`）。`module` 对非 `GalaxyGeneralEvent` 事件**不生效**——一律推送。

### 路线 C：上下文初始化钩子 / 全事件监听

启动类直接注册 lambda：

- `builder.addApplicationEventLoad(...)`：`WebApplicationContext` 初始化完成时触发一次
- `builder.addApplicationEventClient(event -> ...)`：监听**全部** `ApplicationEvent`

## 核心 API

| 类 / 注解 / 接口 | 用途 |
|------------------|------|
| `GalaxyGeneralEvent(String module, Object source)` | 自定义事件父类，字段：`date`、`module`、`source` |
| `@EventHandleClass` | 标注事件处理类（自动注册为单例 Bean） |
| `@EventHandleMethod(String module)` | 标注事件处理方法，第一参数为事件类型 |
| `GalaxySpringUtil.publishEvent(ApplicationEvent)` | 发布事件 |
| `GalaxySpringUtil.publishEvent(Object)` | 发布任意对象（Spring 4.2+ 支持） |
| `ApplicationEventLoad` | 容器初始化完成钩子（函数式接口：`applicationLoad()`） |
| `ApplicationEventClient` | 全事件监听器（函数式接口：`onApplicationEvent(ApplicationEvent)`） |

## 典型用法

### 自定义事件

```java
// 1. 事件
package com.example.event;

import cn.gsq.common.config.event.GalaxyGeneralEvent;

public class OrderPaidEvent extends GalaxyGeneralEvent {
    public OrderPaidEvent(Order order) {
        super("order", order);   // module = "order"
    }
}

// 2. 处理类
package com.example.handler;

import cn.gsq.common.EventHandleClass;
import cn.gsq.common.EventHandleMethod;
import com.example.event.OrderPaidEvent;

@EventHandleClass
public class OrderEventHandler {

    @EventHandleMethod(module = "order")
    private void onOrderPaid(OrderPaidEvent event) {
        Order order = (Order) event.getSource();
        // 发短信、写流水...
    }
}

// 3. 启动类注册扫描
new GalaxyApplicationBuilder(MyApplication.class)
        .addEventHandlePaths("com.example.handler")
        .run(args);

// 4. 发布
GalaxySpringUtil.publishEvent(new OrderPaidEvent(order));
```

### 订阅 Spring 内置事件

```java
@EventHandleClass
public class StartupHook {

    @EventHandleMethod   // module 不写，对内置事件无意义
    private void onContextReady(ApplicationReadyEvent event) {
        // 应用就绪
    }

    @EventHandleMethod
    private void onShutdown(ContextClosedEvent event) {
        // 关闭前
    }
}
```

### 容器初始化钩子 / 全事件监听

```java
new GalaxyApplicationBuilder(MyApplication.class)
        .addApplicationEventLoad(() -> System.out.println("WebApplicationContext 已就绪"))
        .addApplicationEventClient(event -> System.out.println("收到事件：" + event.getClass().getSimpleName()))
        .run(args);
```

## 注意事项

- **`@EventHandleMethod` 方法必须只有一个参数**，且参数类型是 `ApplicationEvent` 或其子类——否则加载时抛 `RuntimeException("处理函数不符合规范...")`。
- 方法**可以是 `private`**（反射 `setAccessible(true)`）。
- **`module` 路由仅对 `GalaxyGeneralEvent` 子类生效**。Spring 内置事件不是 `GalaxyGeneralEvent`，`module` 写不写都全部推送。
- 标 `@EventHandleClass` 的类会**自动注册成单例 Bean**，可 `@Autowired` 注入其他 Bean。
- **包扫描只执行一次**，第一次有事件触发时懒加载——若发布事件时 handler 包还未注册，事件会丢。务必启动期就 `addEventHandlePaths`。
- `addApplicationEventLoad` 触发时机：`GalaxySpringUtil.setApplicationContext(...)` 被回调时，相当于"`ApplicationContextAware` 注入完成"。早于 `ApplicationReadyEvent`。
- `addApplicationEventClient` 与 `@EventHandleClass` **不冲突**，会同时触发；前者听**所有**事件，后者按类型过滤。
- 不要订阅 `ApplicationFailedEvent` 走业务逻辑——库内对它做了拦截（直接打日志返回）。
