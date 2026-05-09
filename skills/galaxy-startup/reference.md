# Galaxy Startup — 完整参考

`cn.gsq.common.GalaxyApplicationBuilder extends org.springframework.boot.builder.SpringApplicationBuilder`

## 完整方法清单

### 注册扩展（链式，返回 `this`）

```java
GalaxyApplicationBuilder addLoadPackage(String packageName)
GalaxyApplicationBuilder addLoadProperties(String packageName)
GalaxyApplicationBuilder addPreClassPaths(String... paths)
GalaxyApplicationBuilder addEventHandlePaths(String... paths)
GalaxyApplicationBuilder addInterceptor(Class<? extends BaseInterceptor> cls)
GalaxyApplicationBuilder addApplicationEventLoad(ApplicationEventLoad applicationEventLoad)
GalaxyApplicationBuilder addApplicationEventClient(ApplicationEventClient applicationEventClient)
void setCommentHandle(Consumer<Map<String, Object>> commentHandle)
```

### 静态访问

```java
static <R> R getActiveApplication(Function<GalaxyApplicationBuilder, R> function)
static Environment getEnvironment()
static void put(String key, Object value)
static Object get(String key)
```

### Getter（lombok）

```java
Set<AbstractInformationLoader> getLoaders()
Set<ApplicationEventLoad> getApplicationEventLoads()
Set<ApplicationEventClient> getApplicationEventClients()
Set<Class<? extends BaseInterceptor>> getInterceptorClass()
```

### 父类（`SpringApplicationBuilder`）

`profiles(...)`、`bannerMode(...)`、`web(WebApplicationType)`、`headless(...)`、`logStartupInfo(...)`、`properties(...)`、`run(String...)` 等全部可用。

## `addLoadPackage` 工作原理

库通过反射修改 `@SpringBootApplication`（或 `@ComponentScan`）的注解 `memberValues` Map，往 `scanBasePackages` 字段（或 `value` 字段）追加包名。

如果你要在追加之后做点其他注解修改，用 `setCommentHandle(Consumer<Map<String,Object>> handle)` 传一个钩子——它在每次 `addLoadPackage` 后被调用，参数是注解的 `memberValues` Map，可以修改其他字段（如 `excludeFilters`）。

```java
builder.setCommentHandle(memberValues -> {
    System.out.println("已扩展扫描包: " + Arrays.toString((String[]) memberValues.get("scanBasePackages")));
});
builder.addLoadPackage("com.example.extra");
```

## `AbstractInformationLoader` 扩展点

如果你的库本身依赖 `common-boot`，且想**让自己的扫描路径自动生效**（下游不必每次写 `addPreClassPaths` 等），实现一个 `AbstractInformationLoader` 放在 `cn.galaxy.loader` 包下：

```java
// 文件路径必须是: cn/galaxy/loader/MyLoader.java
package cn.galaxy.loader;

import cn.gsq.common.AbstractInformationLoader;
import cn.hutool.core.collection.CollUtil;
import java.util.List;

public class MyLoader extends AbstractInformationLoader {

    @Override
    public boolean isEnable() {
        return true;   // 可基于环境变量 / 系统属性条件化
    }

    @Override
    public List<String> springBeansSupply() {
        return CollUtil.newArrayList("com.example.mylib.beans");
    }

    @Override
    public List<String> envArgsSupply() {
        return CollUtil.newArrayList("com.example.mylib.config");
    }

    @Override
    public List<String> initMethodsSupply() {
        return CollUtil.newArrayList("com.example.mylib.init");
    }

    @Override
    public List<String> eventHandleSupply() {
        return CollUtil.newArrayList("com.example.mylib.events");
    }
}
```

**强约束**：

- 包必须**正好是** `cn.galaxy.loader`（来源：`CommonPropertiesFinal.SCAN_ROOT_PACKAGE`）
- 类必须有无参构造（`ReflectUtil.newInstance` 调用）
- 父类 4 个 supply 方法默认返回 `null`，按需重写

任意一个返回非空 List，相应的扫描路径会被加入。`isEnable()` 返回 `false` 则整个 loader 被跳过。

## `addLoadProperties` 工作原理

扫描指定包下 `@AutoPropertiesClass` 标注的类，再扫这些类里 `@AutoPropertiesMethod` 标注的方法，调用并把返回的 `Map<String,Object>` 通过 `super.properties(map)` 注入。

**方法约束**：必须 `static`、无参、返回 `Map<String,Object>`。否则记 error 跳过。

```java
@AutoPropertiesClass
public class MyDynamicConfig {

    @AutoPropertiesMethod
    public static Map<String, Object> load() {
        Map<String, Object> map = new HashMap<>();
        map.put("custom.endpoint", System.getenv("MY_ENDPOINT"));
        map.put("custom.timeout", 30000);
        return map;
    }
}
```

启动期 `builder.addLoadProperties("com.example.config")` 即可。

## Banner 解析顺序

`banner.msg` 取值后：

1. 包含 `"classpath"`：取后缀（点之后的部分）
   - 后缀是 `gif/jpg/png`：`ImageBanner` 渲染（图片不存在则 fallback）
   - 否则：`ResourceBanner` 渲染文本文件
2. 不包含 `classpath` 或两种 banner 都加载失败：直接 `out.println(msg)` 当字面量

## 双重存储

| 存储 | 作用 | 访问方式 |
|------|------|----------|
| `PRESET_PARAMETER`（`ConcurrentHashMap`） | 全局参数，跨模块共享 | `GalaxyApplicationBuilder.put/get` 或 `GalaxySpringUtil.putGlobalArgument/getGlobalArgument` |
| Spring `Environment` | 标准配置（yml / properties / @Value） | `GalaxyApplicationBuilder.getEnvironment()` 或 `GalaxySpringUtil.getEnvironment()` |

全局参数**不需要 Spring 容器就绪**，可以在 `main` 函数里、构造期就 put——配置类（`@AutoPropertiesMethod`）里也能读到上一个模块 put 的值。
