---
name: galaxy-startup
description: 编写 Spring Boot 应用启动 main 方法、用 GalaxyApplicationBuilder 替代 SpringApplication.run、自定义启动 banner、注册启动期扩展（拦截器、初始化包、事件包）。当用户写应用入口、main 方法、自定义 banner、SpringApplicationBuilder 相关代码时使用。
---

# Galaxy Startup

## 何时使用

下游项目编写**应用启动 main 方法**时加载本 SKILL：

- 用 `GalaxyApplicationBuilder` 替代 `SpringApplication.run(...)`
- 自定义启动 banner（文字 / 图片 / 默认 Galaxy 艺术字）
- 启动期注册：拦截器（`addInterceptor`）、初始化包（`addPreClassPaths`）、事件处理包（`addEventHandlePaths`）、容器钩子（`addApplicationEventLoad`/`addApplicationEventClient`）、扫描包扩展（`addLoadPackage`）、动态配置加载（`addLoadProperties`）
- 跨模块共享全局参数（`GalaxyApplicationBuilder.put/get`）

## 引入

`io.github.gaoshq7:common-boot:1.0.2`。

## 核心做法

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        new GalaxyApplicationBuilder(MyApplication.class)
                // 链式调用各 add 方法...
                .run(args);
    }
}
```

`GalaxyApplicationBuilder` 继承自 Spring 的 `SpringApplicationBuilder`，原生方法（`profiles(...)`、`bannerMode(...)`、`web(...)` 等）全部可用。

**自动行为**：

- 构造期就把 `cn.gsq.common` 加入扫描路径——下游不必重复
- 自动扫描 `cn.galaxy.loader` 包下的 `AbstractInformationLoader` 子类，应用其 `springBeansSupply / envArgsSupply / initMethodsSupply / eventHandleSupply` 提供的包路径

## 核心 API

| 方法 | 作用 |
|------|------|
| `new GalaxyApplicationBuilder(Class<?>... sources)` | 构造，传启动类 |
| `addLoadPackage(String pkg)` | 追加 `@SpringBootApplication.scanBasePackages` 或 `@ComponentScan.value` |
| `addLoadProperties(String pkg)` | 扫描 `@AutoPropertiesClass` + `@AutoPropertiesMethod`，把返回的 `Map<String,Object>` 注入 Spring properties |
| `addPreClassPaths(String... paths)` | 注册 `@PreLoadClass` 扫描路径（见 `galaxy-preload`） |
| `addEventHandlePaths(String... paths)` | 注册 `@EventHandleClass` 扫描路径（见 `galaxy-event`） |
| `addInterceptor(Class<? extends BaseInterceptor>)` | 注册拦截器（见 `galaxy-interceptor`） |
| `addApplicationEventLoad(ApplicationEventLoad)` | 上下文初始化完成钩子 |
| `addApplicationEventClient(ApplicationEventClient)` | 全事件监听 |
| `setCommentHandle(Consumer<Map<String,Object>>)` | `addLoadPackage` 修改注解后的回调 |
| `run(String... args)` → `ConfigurableApplicationContext` | 启动（继承自父类） |
| `GalaxyApplicationBuilder.put(String, Object)`（静态） | 写全局参数 |
| `GalaxyApplicationBuilder.get(String)`（静态） | 读全局参数 |
| `GalaxyApplicationBuilder.getEnvironment()`（静态） | 读 Spring `Environment` |

## 典型用法

### 最简启动

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        new GalaxyApplicationBuilder(MyApplication.class).run(args);
    }
}
```

### 全功能启动

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        new GalaxyApplicationBuilder(MyApplication.class)
                .addLoadPackage("com.example.module1")
                .addLoadPackage("com.example.module2")
                .addPreClassPaths("com.example.init")          // @PreLoadClass 扫描
                .addEventHandlePaths("com.example.handlers")   // @EventHandleClass 扫描
                .addInterceptor(AuthInterceptor.class)
                .addInterceptor(LogInterceptor.class)
                .addApplicationEventLoad(() -> log.info("容器就绪"))
                .addApplicationEventClient(event -> metrics.count(event.getClass()))
                .run(args);
    }
}
```

### 自定义 banner

`application.yml`：

```yaml
banner:
  msg: "classpath:banner.txt"   # 或 banner.png / banner.jpg / banner.gif
```

或直接 `banner.msg: "我的应用"` —— 不带 `classpath` 当文字打印。后缀是 `gif/jpg/png` 走 `ImageBanner`，其他走 `ResourceBanner`，找不到就当字面量 `println`。

### 跨模块共享参数

```java
// 模块 A 在启动期写入
GalaxyApplicationBuilder.put("license.key", "xxx-xxx");

// 模块 B 任意位置读取（不需要 Spring 容器）
String key = (String) GalaxyApplicationBuilder.get("license.key");
```

## 配置项

| 配置 key | 含义 | 默认 |
|----------|------|------|
| `banner.msg` | 启动 banner（文字 / classpath 文件路径） | 内置 Galaxy ASCII |
| `spring.application.name` | 应用 ID | — |

## 注意事项

- **`@SpringBootApplication`（或 `@ComponentScan`）必须存在**——否则 `addLoadPackage` 抛 `IllegalArgumentException`。
- **`@SpringBootApplication` 不要带 `scanBasePackages` 属性**——若带了，库会用反射往里追加；若没带，第一次追加会打 warn 说"默认扫描路径将被覆盖"，因为 Spring 默认逻辑（扫启动类所在包）会被显式列表替代。**推荐写法**：注解空着，把所有包通过 `addLoadPackage(...)` 注册。
- `addInterceptor` 注册的拦截器，类上仍然要标 `@InterceptorPattens`——否则 `InterceptorControl` 跳过它。
- `GalaxyApplicationBuilder` 的实例**最近一次** `run` 的会被静态 `getActiveApplication` 拿到——多模块场景下注意这一点。
- 想免去每个项目写一堆 `add*Paths`？实现 `AbstractInformationLoader` 放在 `cn.galaxy.loader` 包下，库会自动扫描并应用——见 `reference.md`。

更多：完整 API、`AbstractInformationLoader` 扩展点用法见 [reference.md](reference.md)。
