# Galaxy Spring Util — 完整参考

`cn.gsq.common.config.GalaxySpringUtil`（implements `ApplicationListener`, `ApplicationContextAware`，`@Configuration`）

## 完整方法清单

```java
// === 上下文 ===
public static ApplicationContext getContext()
public static ApplicationContext getRequiredContext()    // 未就绪抛 IllegalStateException
public static boolean            isReady()
public static Environment        getEnvironment()
public static void               updateApplicationContext(ApplicationContext ctx)

// === 全局参数（透传到 GalaxyApplicationBuilder.put/get） ===
public static void   putGlobalArgument(String key, Object value)
public static Object getGlobalArgument(String key)

// === 配置访问（Environment 包装） ===
public static String   getProperty(String key)
public static String   getProperty(String key, String defaultValue)
public static <T> T    getProperty(String key, Class<T> type)
public static <T> T    getProperty(String key, Class<T> type, T defaultValue)
public static String[] getActiveProfiles()
public static boolean  acceptsProfile(String profileExpression)

// === 事件 ===
public static void    publishEvent(ApplicationEvent event)
public static void    publishEvent(Object event)
public static boolean tryPublishEvent(Object event)      // context 未就绪返回 false + warn 日志

// === Bean 查询（推荐：null 安全） ===
public static <T> Optional<T>           findBean(Class<T> type)
public static <T> Optional<T>           findBean(String name, Class<T> type)
public static    Optional<Object>       findBean(String name)
public static <T> T                     getRequiredBean(Class<T> type)
public static <T> T                     getRequiredBean(String name, Class<T> type)
public static <T> List<T>               findBeans(Class<T> type)
public static <T> Map<String, T>        getBeansAsMap(Class<T> type)
public static String[]                  getBeanNames(Class<?> type)
public static <T> Collection<T>         getBeansByAnnotation(Class<? extends Annotation> anno)
public static String[]                  getBeanNamesForAnnotation(Class<? extends Annotation> anno)

public static boolean       containsBean(String name)
public static boolean       containsBeanDefinition(String name)
public static boolean       isSingleton(String name)
public static boolean       isPrototype(String name)
public static Class<?>      getBeanType(String name)
public static BeanDefinition getBeanDefinition(String name)

// === 注入辅助（给非 Spring 对象注入） ===
public static void  autowireBean(Object existingObject)
public static <T> T initializeBean(T existingObject, String beanName)

// === 安全注册 / 删除 ===
public static <T> boolean registerBeanIfAbsent(String beanName, Class<T> beanClass, Object... constructorArgs)
public static <T> T       registerSingletonBean(Class<T> clazz)             // lowerFirst 命名
public static void        putSingleton(String beanName, Object obj)
public static boolean     unregisterBean(String name)                       // definition + singleton 一并清

// === 包扫描 ===
public static void dynamicLoadPackage(String basePackage, Function<BeanDefinition, String> nameFn)

// === @Deprecated 旧 API（保留兼容性，不要在新代码中使用） ===
@Deprecated public static <T> T              getBean(Class<T> c)
@Deprecated public static <T> T              getBean(String name, Class<T> clazz)
@Deprecated public static <T> T              getBean(String name)
@Deprecated public static <T> List<T>        getBeans(Class<T> clazz)
@Deprecated public static <T> Collection<T>  getBeanNamesByAnno(Class<? extends Annotation> clazz)
@Deprecated public static <T> void           registerBean(String beanName, Class<T> beanClass, Object... constructorArgs)
@Deprecated public static <T> T              registerSingleton(Class<T> tClass)
@Deprecated public static int                registerSingleton(String beanName, Object object)
@Deprecated public static void               removeBeanByName(String name)
```

实例方法（不需要外部调用）：

```java
@Override public void setApplicationContext(ApplicationContext applicationContext)   // 回调
@Override public void onApplicationEvent(ApplicationEvent event)                     // 回调
```

## 静态 `context` 的赋值时机

`setApplicationContext` 是 Spring `ApplicationContextAware` 的回调——容器创建并准备好 BeanFactory 之后立即触发。`GalaxySpringUtil` 是 `@Configuration` 类，所以容器一启动就能拿到上下文，**但仍然有先后顺序**：

```
SpringApplication.run(...)
  ↓
prepareContext() / refresh()
  ↓
GalaxySpringUtil.setApplicationContext(ctx)   ← context 在此被赋值
  ↓ 同时触发
ApplicationEventLoad.applicationLoad()    ← addApplicationEventLoad 注册的钩子
  ↓
其他 Bean 初始化
  ↓
ApplicationReadyEvent
  ↓ 触发
CommonInitPackage.init()    ← @PreLoadClass / @PreLoadMethod 执行
```

也就是说：

- 在 `@PreLoadMethod` 里调 `findBean` / `getRequiredBean` **绝对安全**
- 在 `static {}` 静态块或 main 函数中：
  - 旧 `getBean*`：context 还是 null，直接 NPE
  - 新 `findBean*`：返回 Optional.empty（安全）
  - 新 `getRequiredBean*`：抛 `IllegalStateException`（带清晰提示）
  - 新 `isReady()`：返回 false（用于条件分支）
- 在普通 `@Bean` 方法或 `@Component` 构造器中 → 危险（依赖于容器初始化顺序）

## 推荐 API 实现细节

### `findBean` / `getRequiredBean`

```java
public static <T> Optional<T> findBean(Class<T> type) {
    if (context == null) return Optional.empty();
    try {
        return Optional.ofNullable(context.getBean(type));
    } catch (BeansException e) {        // ← 比旧 API 的 NoSuchBeanDefinitionException 范围更广
        log.debug("findBean({}) 失败: {}", type, e.getMessage());
        return Optional.empty();
    }
}

public static <T> T getRequiredBean(Class<T> type) {
    try {
        return getRequiredContext().getBean(type);
    } catch (BeansException e) {
        throw new IllegalStateException("找不到必需的 bean，类型: " + type.getName(), e);
    }
}
```

**关键改进**：
- catch `BeansException`（覆盖 `NoSuchBeanDefinitionException` / `BeanNotOfRequiredTypeException` / `NoUniqueBeanDefinitionException` / `BeanCreationException`）
- 失败有 debug 日志，不再是黑洞
- context==null 不会 NPE

### `getBeansByAnnotation` 一行实现

```java
@SuppressWarnings("unchecked")
public static <T> Collection<T> getBeansByAnnotation(Class<? extends Annotation> annotationType) {
    if (context == null) return Collections.emptyList();
    try {
        return (Collection<T>) context.getBeansWithAnnotation(annotationType).values();
    } catch (BeansException e) {
        log.debug("...", e);
        return Collections.emptyList();
    }
}
```

**与旧 `getBeanNamesByAnno` 对比**：

| 维度 | 旧 `getBeanNamesByAnno` | 新 `getBeansByAnnotation` |
|------|-------------------------|---------------------------|
| 容器查询次数 | 1（getBeansWithAnnotation）+ N（每个类 getBeans） | **1** |
| 命名 | 误导（returns 实例不 names） | 准确 |
| context==null | NPE | 返回空 list |
| 失败日志 | 无 | debug 级别 |

要拿名字数组用 `getBeanNamesForAnnotation(Class)`。

### `registerSingletonBean` —— bean name 命名修正

```java
public static <T> T registerSingletonBean(Class<T> clazz) {
    Objects.requireNonNull(clazz, "clazz");
    AutowireCapableBeanFactory bf = getRequiredContext().getAutowireCapableBeanFactory();
    T obj = bf.createBean(clazz);
    String beanName = StrUtil.lowerFirst(clazz.getSimpleName());   // ← 关键：lowerFirst
    putSingleton(beanName, obj);
    return obj;
}
```

| 旧 `registerSingleton(Class)` | 新 `registerSingletonBean(Class)` |
|-------------------------------|----------------------------------|
| `upperFirst` → `"MyService"` | `lowerFirst` → `"myService"` |
| `@Autowired private MyService` 找不到 | `@Autowired private MyService` ✅ 直接命中 |

### `unregisterBean` —— 完整删除

```java
public static boolean unregisterBean(String name) {
    if (StrUtil.isBlank(name) || context == null) return false;
    AutowireCapableBeanFactory bf = context.getAutowireCapableBeanFactory();
    if (!(bf instanceof DefaultListableBeanFactory)) return false;
    DefaultListableBeanFactory factory = (DefaultListableBeanFactory) bf;
    boolean removed = false;
    if (factory.containsBeanDefinition(name)) {
        factory.removeBeanDefinition(name);
        removed = true;
    }
    if (factory.containsSingleton(name)) {
        factory.destroySingleton(name);   // ← 也清单例缓存
        removed = true;
    }
    return removed;
}
```

| 旧 `removeBeanByName` | 新 `unregisterBean` |
|----------------------|---------------------|
| 仅 `removeBeanDefinition` | `removeBeanDefinition` + `destroySingleton` |
| 对纯单例抛 NoSuchBeanDefinitionException | 纯单例也工作 |
| 已实例化引用还在缓存 | 也清缓存 |
| 返回 void | 返回 boolean（true=至少清了一项） |

### `registerBeanIfAbsent` —— 可靠的存在性检查

```java
public static <T> boolean registerBeanIfAbsent(String beanName, Class<T> beanClass, Object... constructorArgs) {
    // ...
    if (factory.containsBeanDefinition(beanName) || factory.containsSingleton(beanName)) {
        return false;   // ← 已存在直接跳过
    }
    BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(beanClass);
    if (constructorArgs != null) {
        for (Object arg : constructorArgs) {
            builder.addConstructorArgValue(arg);
        }
    }
    factory.registerBeanDefinition(beanName, builder.getBeanDefinition());
    return true;
}
```

**与旧 `registerBean` 对比**：用 `containsBeanDefinition` + `containsSingleton` 双检查，比"`getBean(name) != null`"可靠（旧版的检查在 bean 创建失败时也会判定"不存在"，导致重复注册）。

### `autowireBean` / `initializeBean`

```java
public static void autowireBean(Object existingObject) {
    Objects.requireNonNull(existingObject, "existingObject");
    getRequiredContext().getAutowireCapableBeanFactory().autowireBean(existingObject);
}

public static <T> T initializeBean(T existingObject, String beanName) {
    Objects.requireNonNull(existingObject, "existingObject");
    return (T) getRequiredContext().getAutowireCapableBeanFactory()
            .initializeBean(existingObject, beanName);
}
```

| 方法 | 注入 `@Autowired` | 触发 `BeanPostProcessor` | 调 `@PostConstruct` / `InitializingBean` |
|------|------------------|-----------------------|---------------------------------------|
| `autowireBean` | ✅ | ❌ | ❌ |
| `initializeBean` | ✅（先调 autowire） | ✅ | ✅ |

### `getProperty` 系列

```java
public static <T> T getProperty(String key, Class<T> type, T defaultValue) {
    Environment env = getEnvironment();
    return env != null ? env.getProperty(key, type, defaultValue) : defaultValue;
}
```

直接转发到 `Environment.getProperty(...)`。Spring 内部用 `ConversionService` 做类型转换（Boolean、Integer、Long、Duration、URL 等都支持）。

### `acceptsProfile`

```java
public static boolean acceptsProfile(String profileExpression) {
    Environment env = getEnvironment();
    return env != null && env.acceptsProfiles(Profiles.of(profileExpression));
}
```

支持 Spring profile 表达式：`"dev"` / `"dev | test"` / `"!prod"` / `"prod & cluster"` 等。

## 旧 API 与新 API 迁移对照

```java
// 旧
UserService svc = GalaxySpringUtil.getBean(UserService.class);   // 找不到返回 null（无日志）
if (svc == null) ...
// 新
UserService svc = GalaxySpringUtil.findBean(UserService.class)
        .orElseThrow(() -> new IllegalStateException("UserService missing"));
// 或
UserService svc = GalaxySpringUtil.getRequiredBean(UserService.class);   // 不存在直接抛

// 旧
List<EventHandler> handlers = GalaxySpringUtil.getBeans(EventHandler.class);   // 可能 null
// 新
List<EventHandler> handlers = GalaxySpringUtil.findBeans(EventHandler.class);  // 必非 null

// 旧
Collection<Plugin> plugins = GalaxySpringUtil.getBeanNamesByAnno(MyPlugin.class);   // N+1 查询
// 新
Collection<Plugin> plugins = GalaxySpringUtil.getBeansByAnnotation(MyPlugin.class); // 1 次查询

// 旧
GalaxySpringUtil.registerBean("specialClient", HttpClient.class, "url", 30);   // 不知道是否成功
// 新
boolean ok = GalaxySpringUtil.registerBeanIfAbsent("specialClient", HttpClient.class, "url", 30);

// 旧
NotificationService notif = GalaxySpringUtil.registerSingleton(NotificationService.class);
// bean name = "NotificationService" → @Autowired 找不到，必须 @Qualifier("NotificationService")
// 新
NotificationService notif = GalaxySpringUtil.registerSingletonBean(NotificationService.class);
// bean name = "notificationService" → @Autowired private NotificationService ✅

// 旧
RedissonClient redisson = ...;
GalaxySpringUtil.registerSingleton("redissonClient", redisson);   // 返回 int 没意义
// 新
GalaxySpringUtil.putSingleton("redissonClient", redisson);
// 如果 redisson 内部有 @Autowired 字段，先注入：
// GalaxySpringUtil.autowireBean(redisson);
// GalaxySpringUtil.putSingleton("redissonClient", redisson);

// 旧
GalaxySpringUtil.removeBeanByName("oldBean");   // 对纯单例抛异常 / 已实例化的还在缓存
// 新
boolean removed = GalaxySpringUtil.unregisterBean("oldBean");   // 完整清理
```

## 内部回调全景：`onApplicationEvent`

```java
@Override
public void onApplicationEvent(ApplicationEvent event) {
    // 1. 启动失败：打日志直接 return
    if (event instanceof ApplicationFailedEvent) {
        log.error("Galaxy 核心启动失败: ...", e);
        return;
    }

    // 2. 全事件监听器（addApplicationEventClient 注册的）
    Set<ApplicationEventClient> clients = ...;
    for (ApplicationEventClient client : clients) client.onApplicationEvent(event);
    // ⚠️ 当前实现没有异常隔离：单个 client 抛异常会阻断后续所有 client + 系统事件处理
    //    若你的 ApplicationEventClient 实现可能抛错，请在自己的代码里 try-catch

    // 3. 模块化事件分发（@EventHandleClass / @EventHandleMethod）
    EventHandleSelector.handleEvent(event);

    // 4. ApplicationReadyEvent：触发 @PreLoadClass 加载
    if (event instanceof ApplicationReadyEvent) {
        CommonInitPackage.init();
        log.info("Galaxy 核心启动成功...");
        return;
    }

    // 5. ContextClosedEvent：日志
    if (event instanceof ContextClosedEvent) {
        log.info("Galaxy 核心将要关闭...");
        return;
    }

    // 6. ServletRequestHandledEvent.wasFailure：触发 LogHook
    if (event instanceof ServletRequestHandledEvent
            && ((ServletRequestHandledEvent) event).wasFailure()) {
        LogHook hook = DefaultSystemLog.getHook();
        if (hook != null) hook.servletLog(LogLevel.ERROR, requestHandledEvent.getDescription());
    }
}
```

## 三种 Bean "注册" 方式对比

| 方法 | 实例化方 | 注入依赖 | 容器管理生命周期 | 推荐度 |
|------|----------|----------|-------------------|---------|
| `registerBeanIfAbsent(name, class, args)` | Spring（基于 BeanDefinition + 构造参数） | ✅ | ✅ | 推荐 |
| `registerSingletonBean(Class)` | Spring（`createBean`） | ✅ | ❌（手工注册到 SingletonBeanRegistry，不参与销毁） | 推荐 |
| `putSingleton(String, Object)` | 你 | ❌（要的话先 `autowireBean(obj)`） | ❌ | 第三方对象集成 |

`registerBeanIfAbsent` 内部：

```java
BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(beanClass);
for (Object arg : constructorArgs) builder.addConstructorArgValue(arg);
BeanDefinition bd = builder.getBeanDefinition();
DefaultListableBeanFactory factory = ...;
factory.registerBeanDefinition(beanName, bd);
```

注：Spring **lazy** 实例化——只有第一次 `getBean` 时才真正构造。

`registerSingletonBean(Class)` 内部：

```java
AutowireCapableBeanFactory factory = context.getAutowireCapableBeanFactory();
T obj = factory.createBean(tClass);             // 立即创建+注入依赖
String beanName = StrUtil.lowerFirst(tClass.getSimpleName());   // ← 与 Spring 默认一致
putSingleton(beanName, obj);
return obj;
```

`putSingleton(String, Object)` 内部：

```java
ConfigurableApplicationContext ctx = (ConfigurableApplicationContext) getRequiredContext();
ctx.getBeanFactory().registerSingleton(beanName, object);
```

## `dynamicLoadPackage` 实现

```java
ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));   // 仅 @Component（含 @Service/@Repository/@Controller）
ConfigurableApplicationContext ctx = (ConfigurableApplicationContext) context;
BeanDefinitionRegistry registry = (BeanDefinitionRegistry) ctx.getBeanFactory();

scanner.findCandidateComponents(basePackage).forEach(bd -> {
    Class<?> clazz = Class.forName(bd.getBeanClassName());
    BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(clazz);
    registry.registerBeanDefinition(function.apply(bd), builder.getBeanDefinition());
});
```

`function` 决定 bean 名——常用：

- `BeanDefinition::getBeanClassName` — 用类全名
- `bd -> StrUtil.lowerFirst(Class.forName(bd.getBeanClassName()).getSimpleName())` — 用 Spring 默认命名

错误处理：单个类失败仅 `log.error("动态注入Bean错误: {}", ...)`，不中断后续。

## `@AutoPropertiesClass` / `@AutoPropertiesMethod` 反射约束

`GalaxyApplicationBuilder.addLoadProperties(packageName)` 流程：

```java
Set<Class<?>> classes = ClassUtil.scanPackageByAnnotation(packageName, AutoPropertiesClass.class);
for (Class cls : classes) {
    if (cacheLoadProperties.contains(cls)) continue;
    for (Method method : cls.getDeclaredMethods()) {
        if (method.getAnnotation(AutoPropertiesMethod.class) == null) continue;
        method.setAccessible(true);

        // 三个约束：返回类型 Map / 无参 / 静态
        ParameterizedType pt = (ParameterizedType) method.getGenericReturnType();
        Class retCls = (Class) pt.getRawType();
        boolean isStatic = Modifier.isStatic(method.getModifiers());
        Type[] params = method.getParameterTypes();

        if (params.length <= 0 && Map.class == retCls && isStatic) {
            Map<String, Object> map = (Map<String, Object>) method.invoke(null);
            if (map != null) super.properties(map);   // 注入 Spring properties
        } else {
            log.error("配置加载类 X 的 Y 函数不符合规范：无参数、返回值 Map、静态!");
        }
    }
    cacheLoadProperties.add(cls);
}
```

注意：返回类型必须**正好是 `Map`**——`HashMap`、`LinkedHashMap` 等子类的 `getRawType()` 都是 `Map`，但**`Map<String, Object>` 必须有泛型参数**才能通过 `(ParameterizedType)` 强转——返回 `Map`（无泛型）会 `ClassCastException`。

## 全局参数双访问入口

```java
GalaxyApplicationBuilder.put("k", "v")    ←→    GalaxySpringUtil.putGlobalArgument("k", "v")
GalaxyApplicationBuilder.get("k")         ←→    GalaxySpringUtil.getGlobalArgument("k")
```

后者多一层 null 检查（key 必须 `StrUtil.isNotBlank`）。底层都是同一个 `ConcurrentHashMap`。

## 已弃用 API 的"为什么不要用"

### `getBean(Class)` / `getBean(String, Class)` / `getBean(String)`

```java
@Deprecated
public static <T> T getBean(Class<T> c) {
    T result = null;
    try {
        result = context.getBean(c);                 // ← context==null 直接 NPE
    } catch (NoSuchBeanDefinitionException exception) {
//        log.warn(...);                              // ← 日志被注释，调试黑洞
    }
    return result;                                   // ← 返回 null（调用方分不清原因）
}
```

具体问题：
- catch 范围只覆盖 `NoSuchBeanDefinitionException`，`BeanNotOfRequiredTypeException` / `NoUniqueBeanDefinitionException` / `BeanCreationException` 都会冒到调用方
- 日志被注释 → 找不到 bean 时调用方完全不知道原因
- `getBean(String)` 是 unchecked cast，类型错误时调用方接收时 ClassCastException

### `getBeans(Class)` 返回 null

调用方 `getBeans(...).stream()` 在没找到时直接 NPE，违反 Java 集合 API 约定（应返回空集合）。

### `getBeanNamesByAnno`

三重问题：
- 命名说"GetBeanNames"，实际返回**实例集合**
- 已经从 `getBeansWithAnnotation` 拿到实例了，又对每个 class 重新查 `getBeans` → **N+1 次容器查询**
- `(Class<? extends T>) entry.getValue().getClass()` unchecked cast，调用方接收时如果泛型不匹配会 ClassCastException

### `registerBean(name, class, args)`

```java
@Deprecated
public static <T> void registerBean(String beanName, Class<T> beanClass, Object ... constructorArgs) {
    if (Objects.isNull(beanClass)) { return; }
    if (!ObjectUtil.isNull(getBean(beanName))) {     // ← 不可靠：bean 创建失败时也返回 null
        return;
    }
    // 注册...
}
```

如果之前注册过的 bean 创建失败（`BeanCreationException`），`getBean(beanName)` 返回 null，**判定"不存在"，重复注册覆盖原 BeanDefinition**。新版用 `containsBeanDefinition` + `containsSingleton` 双检查，可靠。

### `registerSingleton(Class<T>) → T`

```java
@Deprecated
public static <T> T registerSingleton(Class<T> tClass) {
    // ...
    String beanName = StrUtil.upperFirst(tClass.getSimpleName());   // ← upperFirst!
    registerSingleton(beanName, obj);
    return obj;
}
```

`StrUtil.upperFirst("MyService")` = `"MyService"`，但 Spring 默认 `@Component` 的命名是 `"myService"`（首字母小写）。结果：

```java
GalaxySpringUtil.registerSingleton(NotificationService.class);

@Autowired
private NotificationService svc;   // ❌ 找不到，Spring 找的是 "notificationService"
```

只能用 `@Qualifier("NotificationService")` 显式取，反直觉。新版 `registerSingletonBean` 改用 `lowerFirst`，与 Spring 默认一致。

### `registerSingleton(String, Object) → int`

返回值"全局单例总数"对调用方完全无意义（调用方拿这个数能干嘛？）。新版 `putSingleton` 返回 void。

### `removeBeanByName(String)`

```java
@Deprecated
public static void removeBeanByName(String name){
    Object o = getBean(name);                                            // ← 用不可靠的 getBean 检查
    if(ObjectUtil.isNotNull(o)){
        DefaultListableBeanFactory factory = ...;
        factory.removeBeanDefinition(name);                              // ← 仅删 BeanDefinition
    }
}
```

两个问题：
1. **对纯单例抛 NoSuchBeanDefinitionException**：通过 `registerSingleton(String, Object)` 注册的对象只在 SingletonRegistry 里没有 BeanDefinition，`removeBeanDefinition` 会直接抛异常
2. **不清单例缓存**：已经通过 `getBean` 拿到的实例引用依然有效，且 SingletonRegistry 里也还有缓存。下次 `getBean(name)` 仍可能命中残留单例

新版 `unregisterBean` 同时调 `removeBeanDefinition` + `destroySingleton`，对纯单例和有 definition 的都能工作。
