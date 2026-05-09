---
name: galaxy-controller
description: Spring MVC Controller 中获取请求参数、客户端 IP、Header、Cookie、表单对象、Request/Session 属性，无需手动注入 HttpServletRequest。当用户写"@RestController"、"@Controller"、"获取请求参数"、"getParameter"、"获取客户端 IP"、"读取 Header / Cookie"、"操作 Session"相关代码时使用。
---

# Galaxy Controller

## 何时使用

下游项目编写 Controller 时加载本 SKILL：

- 不想在每个方法签名写 `HttpServletRequest request` 参数
- 需要：客户端 IP、Header、Cookie、URL 参数、表单转 Bean、Request/Session 属性、Referer 解析
- 想让拦截器（`BaseInterceptor`）能拿到当前 Controller 实例做回调（`reload()`）

## 引入

`io.github.gaoshq7:common-boot:1.0.2`。

## 核心做法

```java
@RestController
public class UserController extends AbstractController {   // ← 关键：继承

    @GetMapping("/me")
    public Map<String, Object> me() {
        Map<String, Object> map = new HashMap<>();
        map.put("ip", getIp());
        map.put("userAgent", getHeader("User-Agent"));
        map.put("token", getCookieValue("token"));
        map.put("name", getParameter("name", "anonymous"));
        map.put("age", getParameterInt("age", 0));
        return map;
    }
}
```

`AbstractController extends BaseCallbackController`，两层共提供约 20 个保护方法。底层用 `RequestContextHolder` 拿当前请求——线程安全。

## 核心 API

### 客户端信息

| 方法 | 返回 | 说明 |
|------|------|------|
| `getIp()` | `String` | 客户端 IP（hutool `ServletUtil.getClientIP`，自动识别 X-Forwarded-For / X-Real-IP） |

### Header / Cookie

| 方法 | 返回 |
|------|------|
| `getHeader(String name)` | `String` |
| `getHeaders()` | `Map<String,String>`（所有 header） |
| `getCookieValue(String name)` | `String`（不存在返回 `""`） |

### 请求参数

| 方法 | 返回 | 说明 |
|------|------|------|
| `getParameter(String name)` | `String` | null 安全（不存在返回 null） |
| `getParameter(String name, String def)` | `String` | 默认值 |
| `getParameterInt(String name, int def)` | `int` | hutool `Convert.toInt` |
| `getParameterLong(String name, long def)` | `long` | |
| `getParameters(String name)` | `String[]` | 数组参数 |
| `getParametersMap()` | `Map<String,String[]>` | 所有参数 |
| `getRefererParameter()` | `Map<String,String>` | 解析 `Referer` URL 中的 query 参数 |
| `getObject(Class<T>)` | `T` | 表单数据 → Bean（hutool `ServletUtil.toBean`，忽略大小写） |

### Request / Session 属性

| 方法 | 返回 |
|------|------|
| `getAttribute(String name)` | `Object`（Request 域） |
| `setAttribute(String name, Object o)` | — |
| `getSessionAttribute(String name)` | `String`（Session 域，转字符串，不存在返回 `""`） |
| `getSessionAttributeObj(String name)` | `Object`（Session 域，原对象） |
| `setSessionAttribute(String name, Object o)` | — |
| `removeSessionAttribute(String name)` | — |

### 原生对象

| 方法 | 返回 |
|------|------|
| `getRequest()` | `HttpServletRequest` |
| `getResponse()` | `HttpServletResponse` |
| `getSession()` | `HttpSession` |
| `getServletContext()` | `ServletContext` |

### 文件上传相关

见 `galaxy-multipart` SKILL：`createMultipart()`、`hasFile()`、`getMultiRequest()`。

### 静态工具（`BaseCallbackController`）

| 方法 | 返回 | 用途 |
|------|------|------|
| `getRequestAttributes()` | `ServletRequestAttributes` | 强约束（断言非空） |
| `tryGetRequestAttributes()` | `ServletRequestAttributes` | 弱约束（可能 null） |
| `getClientIP()` | `String` | 任意位置（非 Controller）拿 IP |
| `getHeaderMapValues(HttpServletRequest)` | `Map<String,String>` | 给定 request 提取所有 header |

## 典型用法

### 表单转 Bean

```java
public class UserForm {
    private String name;
    private int age;
    // getter/setter
}

@PostMapping("/users")
public Result createUser() {
    UserForm form = getObject(UserForm.class);   // 自动从 query / form 填充
    return Result.ok(userService.save(form));
}
```

### Session 操作

```java
@PostMapping("/login")
public Result login() {
    String username = getParameter("username");
    String password = getParameter("password");
    User user = authService.verify(username, password);
    setSessionAttribute("user", user);
    return Result.ok();
}

@GetMapping("/me")
public Result me() {
    User user = (User) getSessionAttributeObj("user");
    if (user == null) return Result.fail(401, "未登录");
    return Result.ok(user);
}
```

### 在拦截器里 reload Controller 状态

`BaseInterceptor.reload()` 会调当前 Controller 的 `resetInfo()` 方法（见 `BaseCallbackController#resetInfo`）。Controller 重写 `resetInfo()` 可在拦截阶段做"每个请求重置一次"的逻辑：

```java
@RestController
public class TenantController extends AbstractController {
    private String currentTenant;

    @Override
    public void resetInfo() {
        this.currentTenant = getHeader("X-Tenant-Id");   // 每次请求自动刷新
    }

    @GetMapping("/data")
    public Object data() {
        return tenantService.fetch(currentTenant);
    }
}
```

注意：自定义拦截器需要在 `preHandle` 之外的合适时机调 `reload()`——`BaseInterceptor` 默认不会调，你写自己的拦截器时可在校验通过后手动调。

## 注意事项

- **绝大多数方法是 `protected`**——只能在子类（继承 `AbstractController` 的 Controller）里用。
- **不在 web 请求线程中调用**会抛 `NullPointerException`（`Objects.requireNonNull(request, "request null")`）——异步任务里需要 `request` 时，提前在请求线程拿了再传过去。
- `getParameter(name)`（无默认值版）**返回 null**，不是空串——后续判空别用 `.equals("")`。
- `getCookieValue(name)` 不存在时返回空串 `""`。
- `getSessionAttribute` 返回 `String`（`Objects.toString(obj, "")`）；要拿原对象用 `getSessionAttributeObj`。
- `getObject(Class<T>)` 用 hutool `ServletUtil.toBean(request, tClass, true)`——第三参数 `true` = 大小写不敏感、忽略 `null` 字段。
- 文件上传相关方法见 `galaxy-multipart`。

更多：完整签名、`BaseCallbackController` 静态工具、与 `BaseInterceptor` 的协作机制见 [reference.md](reference.md)。
