---
name: galaxy-spring-util
description: 运行时操作 Spring 容器：null 安全的 findBean/getRequiredBean、按注解取实例、按类型取 Map、注册/删除 bean、给非 Spring 对象 autowire 注入、读取 yml 配置（getProperty 系列）、判 profile、发布事件、跨模块共享全局参数。当用户写"ApplicationContext"、"获取 Bean"、"动态注册 Bean"、"BeanFactory"、"读环境变量"、"读配置项"、"全局参数"、"@Autowired 第三方对象"、"@AutoPropertiesClass"、"profile" 相关代码时使用。
---

# Galaxy Spring Util

## 何时使用

下游项目在**非 Bean 上下文**（静态方法、第三方框架回调、main 方法等）需要操作 Spring 容器：

- 按类型 / 名称 / 注解获取 Bean（null 安全）
- 动态注册 / 删除 Bean
- 扫描包动态加载所有 `@Component`
- 读 yml/properties 配置
- 给非 Spring 创建的对象注入依赖（autowire）
- 跨模块共享**不进容器**的全局参数（启动期就需要）
- 启动期通过 `@AutoPropertiesClass` 注入动态配置
- 发布 Spring 事件

## 引入

`io.github.gaoshq7:common-boot:1.0.2`。`GalaxySpringUtil` 是 `@Configuration` 类，由库自动扫描注册（`GalaxyApplicationBuilder` 构造器自动加 `cn.gsq.common` 路径）。所有方法都是**静态**的，直接 `GalaxySpringUtil.xxx(...)` 调用。

## 核心做法

```java
// 任意位置（不必是 Bean）
UserService svc = GalaxySpringUtil.findBean(UserService.class)
        .orElseThrow(() -> new IllegalStateException("UserService 未注册"));
String mode = GalaxySpringUtil.getProperty("app.mode", "default");

// 启动期写、运行时读的全局参数
GalaxySpringUtil.putGlobalArgument("license", "xxx");
String license = (String) GalaxySpringUtil.getGlobalArgument("license");
```

## 核心 API（推荐使用）

### 状态查询

| 方法 | 返回 | 说明 |
|------|------|------|
| `isReady()` | `boolean` | context 是否就绪。早期初始化前调容器方法前先判 |
| `getRequiredContext()` | `ApplicationContext` | 未就绪抛 IllegalStateException（带清晰提示），不再 NPE |
| `containsBean(String)` | `boolean` | 含 BeanDefinition 与纯单例 |
| `containsBeanDefinition(String)` | `boolean` | **仅** BeanDefinition（不含 putSingleton 注册的） |

### Bean 查询（null 安全）

| 方法 | 返回 | 说明 |
|------|------|------|
| `findBean(Class<T>)` | `Optional<T>` | 找不到返回 empty，不再返回 null |
| `findBean(String, Class<T>)` | `Optional<T>` | 按名+类型 |
| `findBean(String)` | `Optional<Object>` | 避免 unchecked cast；调用方自己 cast |
| `getRequiredBean(Class<T>)` | `T` | 找不到抛 IllegalStateException（适合强依赖场景） |
| `getRequiredBean(String, Class<T>)` | `T` | 按名+类型，找不到抛 IllegalStateException |
| `findBeans(Class<T>)` | `List<T>` | 找不到返回空 list（**绝不 null**） |
| `getBeansAsMap(Class<T>)` | `Map<String, T>` | 保留 bean name |
| `getBeanNames(Class<?>)` | `String[]` | 真·返回 names |
| `getBeansByAnnotation(Class<? extends Annotation>)` | `Collection<T>` | 一行一次查询，替代旧 `getBeanNamesByAnno` |
| `getBeanNamesForAnnotation(...)` | `String[]` | 真·返回 names |

### Bean 元数据

| 方法 | 返回 |
|------|------|
| `isSingleton(String)` | `boolean` |
| `isPrototype(String)` | `boolean` |
| `getBeanType(String)` | `Class<?>` |
| `getBeanDefinition(String)` | `BeanDefinition`（纯单例返回 null） |

### 配置访问（替代 `getEnvironment().getProperty(...)` 啰嗦写法）

| 方法 | 返回 |
|------|------|
| `getProperty(String key)` | `String`（未配置返回 null） |
| `getProperty(String key, String defaultValue)` | `String` |
| `getProperty(String key, Class<T> type)` | `T`（如 `Integer.class`） |
| `getProperty(String key, Class<T> type, T defaultValue)` | `T` |
| `getActiveProfiles()` | `String[]` |
| `acceptsProfile(String expr)` | `boolean`（支持 `"dev | test"` 表达式） |

### 注入辅助（给非 Spring 对象注入依赖）

| 方法 | 返回 | 用途 |
|------|------|------|
| `autowireBean(Object)` | `void` | 给 new 出来的对象注入 `@Autowired` 字段 |
| `initializeBean(T, String)` | `T` | 走完整 lifecycle（含 `@PostConstruct`） |

### 安全注册 / 删除

| 方法 | 返回 | 用途 |
|------|------|------|
| `registerBeanIfAbsent(String, Class, Object...)` | `boolean` | 同名已存在则跳过（用 BeanDefinition 检查，可靠） |
| `registerSingletonBean(Class<T>)` | `T` | 创建+注入依赖+注册单例。**bean name 用 lowerFirst**，对齐 Spring 默认约定 |
| `putSingleton(String, Object)` | `void` | 已有对象注册成单例。**不会**自动注入依赖（如需配合 `autowireBean`） |
| `unregisterBean(String)` | `boolean` | 同时清 BeanDefinition + 已实例化单例。对纯单例也工作 |

### 事件

| 方法 | 返回 |
|------|------|
| `tryPublishEvent(Object)` | `boolean`（context 未就绪返回 false 并打日志） |

### 包扫描动态加载（保留）

| 方法 | 说明 |
|------|------|
| `dynamicLoadPackage(String basePackage, Function<BeanDefinition, String> nameFn)` | 扫描包内 `@Component` 子类批量注册 |

### 上下文 / 环境 / 全局参数（保留）

| 方法 | 说明 |
|------|------|
| `getContext()` | 拿 `ApplicationContext`（context 未就绪返回 null） |
| `getEnvironment()` | 拿 `Environment` |
| `updateApplicationContext(ApplicationContext)` | 切换上下文（高级用法） |
| `putGlobalArgument(String, Object)` | 启动期就能写，不进容器，`ConcurrentHashMap` 线程安全 |
| `getGlobalArgument(String)` | 同上 |

## 典型用法

### 1. 取 Bean（null 安全）

```java
// Optional 风格——找不到不会 NPE
UserService svc = GalaxySpringUtil.findBean(UserService.class)
        .orElseThrow(() -> new IllegalStateException("UserService 未注册"));

// 找不到就用兜底
UserService svc2 = GalaxySpringUtil.findBean(UserService.class)
        .orElse(defaultUserService);

// 强依赖：找不到直接抛
UserService svc3 = GalaxySpringUtil.getRequiredBean(UserService.class);

// 多 bean 不丢 name
Map<String, EventHandler> byName = GalaxySpringUtil.getBeansAsMap(EventHandler.class);

// 按注解取实例（一次查询）
Collection<Plugin> plugins = GalaxySpringUtil.getBeansByAnnotation(MyPlugin.class);
```

### 2. 读配置项（替代 `getEnvironment().getProperty(...)`）

```java
String mode = GalaxySpringUtil.getProperty("app.mode", "production");   // 带默认值
int port    = GalaxySpringUtil.getProperty("server.port", Integer.class, 8080);
boolean dev = GalaxySpringUtil.acceptsProfile("dev | test");            // 支持表达式
```

### 3. 给第三方对象注入 `@Autowired`

```java
// 反序列化拿到的对象，让它的 @Autowired 字段被注入
MyDto dto = mapper.readValue(json, MyDto.class);
GalaxySpringUtil.autowireBean(dto);
// 此时 dto.userService 已经是容器里的 UserService
```

### 4. 注册 / 删除 Bean（安全版）

```java
// 同名已存在则跳过（不抛异常）
boolean ok = GalaxySpringUtil.registerBeanIfAbsent("specialClient", HttpClient.class,
        "https://api.example.com", 30);

// 用 lowerFirst 命名（"notificationService"），@Autowired 能直接拿到
NotificationService notif = GalaxySpringUtil.registerSingletonBean(NotificationService.class);

// 已有对象注册（如 Redisson 这种第三方 client）
RedissonClient redisson = Redisson.create(config);
GalaxySpringUtil.autowireBean(redisson);                    // 如果它有 @Autowired 字段
GalaxySpringUtil.putSingleton("redissonClient", redisson);

// 完整删除（含纯单例）
GalaxySpringUtil.unregisterBean("oldBean");
```

### 5. 启动早期判 context 就绪

```java
public static void earlyInit() {
    if (!GalaxySpringUtil.isReady()) {
        log.info("Spring 还没就绪，跳过");
        return;
    }
    // ... 安全使用 GalaxySpringUtil.findBean / getProperty ...
}
```

### 6. 动态加载包（保留）

```java
GalaxySpringUtil.dynamicLoadPackage("com.example.plugins",
        BeanDefinition::getBeanClassName);   // bean name 用类全名
```

### 7. 跨模块共享参数（非 Spring）

```java
// main 早期
GalaxyApplicationBuilder.put("startupTime", System.currentTimeMillis());

// @AutoPropertiesClass 启动期注入 Spring properties
@AutoPropertiesClass
public class Cfg {
    @AutoPropertiesMethod
    public static Map<String, Object> load() {
        long t = (long) GalaxyApplicationBuilder.get("startupTime");
        Map<String, Object> map = new HashMap<>();
        map.put("app.startupTime", t);
        return map;
    }
}

// 运行时读
Long t = (Long) GalaxySpringUtil.getGlobalArgument("startupTime");
```

### 8. 启动期动态配置

```java
@AutoPropertiesClass
public class DynamicConfig {
    @AutoPropertiesMethod
    public static Map<String, Object> load() {
        Map<String, Object> map = new HashMap<>();
        map.put("custom.endpoint", System.getenv("MY_ENDPOINT"));
        return map;
    }
}
```

启动类：`builder.addLoadProperties("com.example.config")`。

约束：方法必须 `static`、无参、返回 `Map<String, Object>`。

## ⚠️ 已弃用的 API（请尽快迁移）

下列 API 因 silent fail / 命名错误 / 性能问题 / 类型不安全等原因已 `@Deprecated`，仍可用，但请改用上方推荐方法：

| 旧 API | 推荐替代 | 弃用原因 |
|--------|----------|----------|
| `getBean(Class<T>)` | `findBean(Class<T>)` 或 `getRequiredBean(Class<T>)` | silent fail（日志被注释）；context==null 直接 NPE；catch 范围太窄 |
| `getBean(String, Class<T>)` | `findBean(String, Class<T>)` 或 `getRequiredBean(String, Class<T>)` | 同上 |
| `getBean(String)` | `findBean(String)`（`Optional<Object>`）或 `findBean(String, Class<T>)` | 隐式 unchecked cast，调用方接收 ClassCastException |
| `getBeans(Class<T>)` | `findBeans(Class<T>)` 或 `getBeansAsMap(Class<T>)` | 找不到返回 null，调用方 `.stream()` NPE |
| `getBeanNamesByAnno(Class)` | `getBeansByAnnotation(...)` 或 `getBeanNamesForAnnotation(...)` | 命名误导（实际返回实例不返回名）；性能差（N+1 次查询）；unchecked cast |
| `registerBean(String, Class, Object...)` | `registerBeanIfAbsent(...)` | 用 `getBean` 检查存在性不可靠；返回 void 无法区分"成功"和"已存在被跳过" |
| `registerSingleton(Class<T>)` | `registerSingletonBean(Class<T>)` | bean name 用 `upperFirst`（如 "MyService"），与 Spring 默认 `lowerFirst` 不一致，导致 `@Autowired` 找不到 |
| `registerSingleton(String, Object)` | `putSingleton(String, Object)`（+ 配合 `autowireBean` 注入依赖） | 返回值"全局单例总数"无意义；context==null 时 NPE |
| `removeBeanByName(String)` | `unregisterBean(String)` | 仅删 BeanDefinition；对纯单例（仅 putSingleton 注册的）抛 NoSuchBeanDefinitionException |

## 注意事项

- **静态字段 `context` 是 `volatile`**，但首次赋值在 `setApplicationContext` 回调时——容器初始化前调 `findBean` 等方法**会安全返回 empty / 默认值**；调 `getRequiredBean` / `autowireBean` 等会抛 `IllegalStateException`（带清晰提示）。
- **早期初始化时机**：在 `static {}` 静态块或 main 函数中调 → 推荐用 `isReady()` 判一下；安全做法是放 `ApplicationEventLoad` 钩子或 `@PreLoadMethod`。
- **`registerBeanIfAbsent` 不会立即创建 bean**——只注册 BeanDefinition；要立即注入依赖用 `registerSingletonBean(Class)`，它走 `AutowireCapableBeanFactory.createBean(...)`。
- **`registerSingletonBean(Class)` 的 bean 名规则**：`StrUtil.lowerFirst(clazz.getSimpleName())`——`NotificationService` → `"notificationService"`（与 Spring `@Component` 默认命名一致）。
- **`putSingleton` 不会注入 `@Autowired`** —— 已存在对象的字段不会自动被注入；如需注入先调 `autowireBean(obj)`。
- **`unregisterBean` 同时清 definition + singleton**——已实例化的单例也会被销毁。
- **`getBeansByAnnotation` 替代旧 `getBeanNamesByAnno`**——名字真·返回实例集合，且只查容器一次。但泛型 `<T>` 仍需调用方保证标该注解的所有类有共同父类 `T`，否则接收时 ClassCastException。
- **`autowireBean` vs `initializeBean`**：前者只填 `@Autowired` 字段；后者还触发 `@PostConstruct` / `InitializingBean.afterPropertiesSet`。绝大多数场景用 `autowireBean` 即可。
- **`tryPublishEvent` 比 `publishEvent` 多一层 null 检查 + 日志**——context 未就绪时返回 false 而非静默丢失事件。
- **全局参数 `put/get` 不会通过 Spring 事件通知**——你得自己设计同步机制（如配合 `@EventHandleClass` 发更新事件）。
- **`onApplicationEvent` 内部分发事件无异常隔离**：如果你的 `ApplicationEventClient` / `@EventHandleMethod` 实现可能抛异常，**请在自己的代码里 try-catch**，否则会阻断后续 client 的执行（含 `ApplicationReadyEvent` 等系统级回调）。

更多：完整方法签名、`@AutoPropertiesClass` 反射约束、`dynamicLoadPackage` 内部细节、新旧 API 实现对照见 [reference.md](reference.md)。
