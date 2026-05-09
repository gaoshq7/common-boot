# Galaxy Spring Util — 完整参考

`cn.gsq.common.config.GalaxySpringUtil`（implements `ApplicationListener`, `ApplicationContextAware`，`@Configuration`）

## 完整静态方法

```java
// 上下文
public static ApplicationContext getContext()
public static Environment getEnvironment()
public static void updateApplicationContext(ApplicationContext applicationContext)

// 全局参数（透传到 GalaxyApplicationBuilder.put/get）
public static void putGlobalArgument(String key, Object value)
public static Object getGlobalArgument(String key)

// 事件
public static void publishEvent(ApplicationEvent event)
public static void publishEvent(Object event)

// Bean 查询
public static <T> T getBean(Class<T> c)
public static <T> T getBean(String name)
public static <T> T getBean(String name, Class<T> clazz)
public static <T> List<T> getBeans(Class<T> clazz)
public static <T> Collection<T> getBeanNamesByAnno(Class<? extends Annotation> clazz)

// Bean 注册 / 删除
public static <T> void registerBean(String beanName, Class<T> beanClass, Object... constructorArgs)
public static <T> T   registerSingleton(Class<T> tClass)
public static int     registerSingleton(String beanName, Object object)
public static void    removeBeanByName(String name)

// 包扫描动态注册
public static void dynamicLoadPackage(String basePackage, Function<BeanDefinition, String> function)
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

- 在 `@PreLoadMethod` 里调 `GalaxySpringUtil.getBean` **绝对安全**
- 在 `static {}` 静态块或 main 函数中调 → 失败（context 还没初始化）
- 在普通 `@Bean` 方法或 `@Component` 构造器中 → 危险（依赖于容器初始化顺序）

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

## `registerBean` vs `registerSingleton(Class)` vs `registerSingleton(String, Object)`

| 方法 | 实例化方 | 注入依赖 | 容器管理生命周期 |
|------|----------|----------|-------------------|
| `registerBean(name, class, args)` | Spring（基于 BeanDefinition + 构造参数） | ✅ | ✅ |
| `registerSingleton(Class)` | Spring（`createBean`） | ✅ | ❌（手工注册到 SingletonBeanRegistry，不参与销毁） |
| `registerSingleton(String, Object)` | 你 | ❌ | ❌ |

`registerBean` 内部：

```java
BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(beanClass);
Arrays.stream(constructorArgs).forEach(builder::addConstructorArgValue);
BeanDefinition bd = builder.getBeanDefinition();
DefaultListableBeanFactory factory = (DefaultListableBeanFactory) context.getAutowireCapableBeanFactory();
factory.registerBeanDefinition(beanName, bd);
```

注：Spring **lazy** 实例化——只有第一次 `getBean` 时才真正构造。

`registerSingleton(Class)` 内部：

```java
AutowireCapableBeanFactory factory = context.getAutowireCapableBeanFactory();
T obj = factory.createBean(tClass);   // 立即创建+注入依赖
String beanName = StrUtil.upperFirst(tClass.getSimpleName());
registerSingleton(beanName, obj);     // 调下面的重载
return obj;
```

`registerSingleton(String, Object)` 内部：

```java
ConfigurableApplicationContext ctx = (ConfigurableApplicationContext) context;
ConfigurableListableBeanFactory bf = ctx.getBeanFactory();
bf.registerSingleton(beanName, object);
return bf.getSingletonCount();
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

## `getBeanNamesByAnno` 的暗坑

源码：

```java
Map<String, Object> beanWithAnnotation = context.getBeansWithAnnotation(clazz);
Set<T> result = new HashSet<>();
for (Map.Entry<String, Object> entry : beanWithAnnotation.entrySet()) {
    Class<? extends T> aClass = (Class<? extends T>) entry.getValue().getClass();
    CollUtil.addAll(result, getBeans(aClass));   // 用具体子类再查一次
}
return result;
```

- 没在做"按注解过滤" + 直接拿实例——而是先按注解拿到一组实例，再用每个实例的具体 class 重查 `getBeans(class)`，再合并去重。
- 如果不同 class 的注解 Bean 没有共同父类 T，`(Class<? extends T>)` 强转**不会立即抛**——但 `result` 类型擦除后用 T 接收会在外层取值时抛 `ClassCastException`。
- **正确用法**：所有标该注解的类有共同父类/接口。例如 `@MyPlugin` 都标在 `Plugin` 实现类上，调 `getBeanNamesByAnno(MyPlugin.class) → Collection<Plugin>` 才安全。

## 全局参数双访问入口

```java
GalaxyApplicationBuilder.put("k", "v")    ←→    GalaxySpringUtil.putGlobalArgument("k", "v")
GalaxyApplicationBuilder.get("k")         ←→    GalaxySpringUtil.getGlobalArgument("k")
```

后者多一层 null 检查（key 必须 `StrUtil.isNotBlank`）。底层都是同一个 `ConcurrentHashMap`。
