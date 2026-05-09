---
name: galaxy-spring-util
description: 运行时操作 Spring 容器，按类型/名称/注解获取 Bean，动态注册或删除 Bean，扫描包动态加载，读取 Environment，跨模块共享全局参数。当用户写"ApplicationContext"、"获取 Bean"、"动态注册 Bean"、"BeanFactory"、"读环境变量"、"全局参数"、"@AutoPropertiesClass" 相关代码时使用。
---

# Galaxy Spring Util

## 何时使用

下游项目在**非 Bean 上下文**（静态方法、第三方框架回调、main 方法等）需要操作 Spring 容器：

- 按类型 / 名称 / 注解获取 Bean
- 动态注册 / 删除 Bean
- 扫描包动态加载所有 `@Component`
- 读 `Environment` 配置
- 跨模块共享**不进容器**的全局参数（启动期就需要）
- 启动期通过 `@AutoPropertiesClass` 注入动态配置

## 引入

`io.github.gaoshq7:common-boot:1.0.2`。`GalaxySpringUtil` 是 `@Configuration` 类，由库自动扫描注册（`GalaxyApplicationBuilder` 构造器自动加 `cn.gsq.common` 路径）。所有方法都是**静态**的，直接 `GalaxySpringUtil.xxx(...)` 调用。

## 核心做法

```java
// 任意位置（不必是 Bean）
UserService svc = GalaxySpringUtil.getBean(UserService.class);
String env = GalaxySpringUtil.getEnvironment().getProperty("app.mode");

// 启动期写、运行时读的全局参数
GalaxySpringUtil.putGlobalArgument("license", "xxx");
String license = (String) GalaxySpringUtil.getGlobalArgument("license");
```

## 核心 API

### Bean 查询

| 方法 | 返回 | 说明 |
|------|------|------|
| `getBean(Class<T>)` | `T` | 按类型，无则 null（不抛异常） |
| `getBean(String name)` | `T`（自动转型） | 按名称，无则 null |
| `getBean(String name, Class<T>)` | `T` | 按名+类型，无则 null |
| `getBeans(Class<T>)` | `List<T>` | 按类型获取所有同类 Bean |
| `getBeanNamesByAnno(Class<? extends Annotation>)` | `Collection<T>` | 按类上注解，**要求所有匹配的类有共同父类 T** |

### Bean 动态注册 / 删除

| 方法 | 说明 |
|------|------|
| `registerBean(String name, Class<T>, Object... constructorArgs)` | 注册 BeanDefinition（同名已存在则跳过） |
| `registerSingleton(Class<T>) → T` | 创建实例（自动注入依赖）+ 注册单例，名字 = 类名首字母大写 |
| `registerSingleton(String name, Object obj) → int` | 注册已有实例为单例，返回容器内单例总数 |
| `removeBeanByName(String name)` | 删除 BeanDefinition |
| `dynamicLoadPackage(String basePackage, Function<BeanDefinition, String> nameFn)` | 扫描包内 `@Component` 子类批量注册 |

### 环境与事件

| 方法 | 返回 |
|------|------|
| `getEnvironment()` | `Environment` |
| `getContext()` | `ApplicationContext` |
| `updateApplicationContext(ApplicationContext)` | 切换上下文（高级用法） |
| `publishEvent(ApplicationEvent)` / `publishEvent(Object)` | 发布事件（见 `galaxy-event`） |

### 全局参数（不进容器）

| 方法 | 等价于 |
|------|--------|
| `putGlobalArgument(String, Object)` | `GalaxyApplicationBuilder.put(...)` |
| `getGlobalArgument(String)` | `GalaxyApplicationBuilder.get(...)` |

存储是 `ConcurrentHashMap`，**线程安全**，**无需 Spring 容器就绪**——可在 main 函数 / 启动早期写入。

### 启动期动态配置

通过 `@AutoPropertiesClass` + `@AutoPropertiesMethod` 标注的方法，在 `GalaxyApplicationBuilder.addLoadProperties(...)` 时被调用，返回的 Map 注入 Spring `properties`。

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

## 典型用法

### 取 Bean

```java
UserService userService = GalaxySpringUtil.getBean(UserService.class);
List<EventHandler> handlers = GalaxySpringUtil.getBeans(EventHandler.class);
DataSource ds = GalaxySpringUtil.getBean("primaryDataSource", DataSource.class);
```

### 按注解收集

```java
// 所有标了 @MyPlugin 的 Bean，且这些类都实现 Plugin 接口
Collection<Plugin> plugins = GalaxySpringUtil.getBeanNamesByAnno(MyPlugin.class);
```

### 运行时注册新 Bean

```java
// 简单：基于 class，自动 autowire 依赖
NotificationService notif = GalaxySpringUtil.registerSingleton(NotificationService.class);

// 带名+实例：手动构造好的对象
RedissonClient redisson = Redisson.create(config);
GalaxySpringUtil.registerSingleton("redissonClient", redisson);

// 带名+class+构造参数：交给 Spring 实例化
GalaxySpringUtil.registerBean("specialClient", HttpClient.class, "https://api.example.com", 30);
```

### 动态加载包

```java
// 把 com.example.plugins 下所有 @Component 注册进容器，bean 名用类全名
GalaxySpringUtil.dynamicLoadPackage("com.example.plugins", BeanDefinition::getBeanClassName);
```

### 删除 Bean

```java
GalaxySpringUtil.removeBeanByName("oldBean");
```

### 跨模块共享参数（非 Spring）

```java
// main 早期
GalaxyApplicationBuilder.put("startupTime", System.currentTimeMillis());

// 任意配置类（启动期、Spring 容器还没好）
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

// 运行时（Spring 容器就绪后）
String t = (String) GalaxySpringUtil.getGlobalArgument("startupTime");
```

## 注意事项

- **静态字段 `context` 是 `volatile`**，但首次赋值在 `setApplicationContext` 回调时——容器初始化前调 `getBean` 会 NPE。早期初始化逻辑放 `ApplicationEventLoad` 或 `@PreLoadClass`。
- **`getBean` 等查询方法不抛异常**，仅打日志（被注释了）。返回 null 自己判。
- **`registerBean` 不会注入依赖**——只注册 BeanDefinition；要注入依赖用 `registerSingleton(Class)`，它走 `AutowireCapableBeanFactory.createBean(...)`。
- **`registerSingleton(Class)` 的 bean 名规则**：`StrUtil.upperFirst(tClass.getSimpleName())`——例如 `NotificationService` → `"NotificationService"`（首字母已经大写时不变）。
- **`removeBeanByName` 仅删 BeanDefinition**，已注入的引用仍有效（GC 之前不会回收）。
- **`getBeanNamesByAnno`** 命名误导——它**不返回 bean 名**，返回 Bean 实例集合，且要求所有匹配类有共同父类 T。
- **`updateApplicationContext` 慎用**——会**全局**替换静态 `context`，所有后续 `getBean` 都走新上下文。仅在多 ApplicationContext 切换场景使用。
- 全局参数 `put/get` **不会**通过 Spring 事件通知——你得自己设计同步机制（如配合 `@EventHandleClass` 发更新事件）。

更多：完整方法签名、`@AutoPropertiesClass` 反射约束、`dynamicLoadPackage` 内部细节见 [reference.md](reference.md)。
