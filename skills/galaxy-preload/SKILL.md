---
name: galaxy-preload
description: 在 Spring Boot 应用启动完成（ApplicationReadyEvent）后，按指定顺序执行一组初始化函数。当用户写"启动后初始化"、"预加载"、"应用就绪后执行"、"@PostConstruct 替代品"、"按顺序初始化多个组件"相关代码时使用。
---

# Galaxy Preload

## 何时使用

下游项目需要在 Spring Boot 应用**启动完成后**做一批初始化工作，并且希望：

- 控制执行顺序（多个初始化类之间、同一类的多个方法之间）
- 比 `@PostConstruct` 更晚（容器全部就绪、所有 Bean 都可注入）
- 比 `ApplicationRunner` 更结构化（按注解扫描，不必手写 Bean）

## 引入

`io.github.gaoshq7:common-boot:1.0.2`。

## 核心做法

1. 在初始化类上加 `@PreLoadClass(类执行顺序)`
2. 在该类的方法上加 `@PreLoadMethod(方法执行顺序)`，方法**无参、返回 void**（可以是 `private`）
3. 启动类注册扫描包：`builder.addPreClassPaths("com.example.init")`，或 yml 里没有专属配置项——只能通过 `addPreClassPaths` 或 `AbstractInformationLoader` 扩展

触发时机：`ApplicationReadyEvent`（应用启动完成）。

被 `@PreLoadClass` 标注的类会**自动注册成单例 Bean**（无需 `@Component`/`@Service`），可在其方法内 `@Autowired` 其他 Bean。

## 核心 API

| 注解 | 元素 |
|------|------|
| `@PreLoadClass(int value)` | 类上，`value` 越小越先执行，默认 0 |
| `@PreLoadMethod(int value)` | 方法上，`value` 越小越先执行，默认 0 |

## 典型用法

```java
package com.example.init;

import cn.gsq.common.PreLoadClass;
import cn.gsq.common.PreLoadMethod;
import org.springframework.beans.factory.annotation.Autowired;

@PreLoadClass(1)        // 第 1 个执行的初始化类
public class CacheWarmup {

    @Autowired
    private UserService userService;

    @PreLoadMethod(1)   // 该类内第 1 个执行的方法
    private void warmupUserCache() {
        userService.preloadHotUsers();
    }

    @PreLoadMethod(2)
    private void warmupConfigCache() {
        // ...
    }
}
```

启动类注册扫描路径：

```java
public static void main(String[] args) {
    new GalaxyApplicationBuilder(MyApplication.class)
            .addPreClassPaths("com.example.init")
            .run(args);
}
```

## 注意事项

- **方法签名必须是无参、返回 void**——否则会被静默跳过，仅打 `error` 日志（"函数加载类 X 的 Y 函数不符合规范：无参数、无返回值！"）。
- 方法**可以是 `private`**，框架用反射调用并设置 `setAccessible(true)`。
- **每个方法只会被执行一次**（按 `Method` 对象去重，跨包重复扫描也不会重复）。
- **每个包只会被扫描一次**（按包名去重）。
- 触发时机：`ApplicationReadyEvent` 之后；如果你的逻辑要更早（容器初始化完成时），用 `ApplicationEventLoad`（见 `galaxy-startup`）。
- 类执行顺序由 `@PreLoadClass.value()` 决定；同一类内方法顺序由 `@PreLoadMethod.value()` 决定。两者独立排序。
- 如果不想在每个项目里写 `addPreClassPaths`，可用 `AbstractInformationLoader.initMethodsSupply()` 把包路径声明在框架扩展点里——见 `galaxy-startup` 的 reference。
