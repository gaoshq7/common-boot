# Galaxy Controller — 完整参考

## 类层级

```
BaseCallbackController（提供请求/会话/属性的基础访问）
    ↑ extends
AbstractController（追加参数提取、客户端信息、文件上传）
    ↑ extends
（你的 Controller）
```

## `AbstractController` 完整方法

```java
@Override public void resetInfo();   // 由 BaseInterceptor.reload() 触发，子类按需重写

protected String   getIp();
protected String   getHeader(String name);
protected Map<String, String> getHeaders();
protected String   getCookieValue(String name);
protected String   getParameter(String name);
protected String   getParameter(String name, String def);
protected String[] getParameters(String name);
protected int      getParameterInt(String name, int def);
protected long     getParameterLong(String name, long def);
protected Map<String, String[]> getParametersMap();
protected Map<String, String>   getRefererParameter();
protected <T> T    getObject(Class<T> tClass);

// 文件上传（详见 galaxy-multipart）
public static void clearResources();   // 由 BaseInterceptor.afterCompletion 自动调
protected MultipartHttpServletRequest getMultiRequest();
protected boolean  hasFile();
protected MultipartFileBuilder createMultipart();
```

## `BaseCallbackController` 完整方法（继承得到）

```java
public void resetInfo();   // 默认空实现

// 静态工具
public static ServletRequestAttributes getRequestAttributes();      // 非空断言
public static ServletRequestAttributes tryGetRequestAttributes();   // 可能 null
public static String getClientIP();
public static Map<String, String> getHeaderMapValues(HttpServletRequest request);

// 实例方法
public HttpServletRequest  getRequest();
public HttpServletResponse getResponse();
public HttpSession         getSession();
public ServletContext      getServletContext();

public Object getAttribute(String name);
public void   setAttribute(String name, Object object);

public String getSessionAttribute(String name);          // toString
public Object getSessionAttributeObj(String name);
public void   setSessionAttribute(String name, Object object);
public void   removeSessionAttribute(String name);
```

## 与 `BaseInterceptor` 的协作

```
HTTP 请求来 → DispatcherServlet
  → BaseInterceptor.preHandle(req, resp, Object handler)
      ├─ /error 路径：调 error(response)，写自定义 body，return false
      ├─ /favicon.ico：写 favicon，return false
      └─ 其余：
          ├─ if controller instanceof BaseCallbackController:
          │     currentController.set((BaseCallbackController) controller)   ← 缓存进 ThreadLocal
          └─ 调用子类抽象 preHandle(req, resp, HandlerMethod)

  → 你的拦截器子类 preHandle(...) 返回 true
      （你可以在此手动调 reload() —— 触发 currentController.resetInfo()）

  → Controller 方法执行（继承 AbstractController 的）

  → BaseInterceptor.postHandle(...) （状态码 ≥ 400 时打 error 日志）
  → BaseInterceptor.afterCompletion(...)
      ├─ ClientAbortException：warn 日志
      ├─ 其他 ex：error 日志
      ├─ AbstractController.clearResources()   ← 清 multipart 缓存
      └─ clearResources()                      ← 清 currentController
```

`BaseInterceptor` 内部的 `currentController` ThreadLocal：

```java
private static final ThreadLocal<BaseCallbackController> currentController = new ThreadLocal<>();
```

子类可调 `reload()` 主动触发 `resetInfo()`：

```java
public class MyInterceptor extends BaseInterceptor {
    @Override
    protected boolean preHandle(HttpServletRequest req, HttpServletResponse resp, HandlerMethod handler) {
        // 校验 token...
        reload();   // 触发当前 controller 的 resetInfo()
        return true;
    }
}
```

## ThreadLocal 列表

| ThreadLocal | 位置 | 清理时机 |
|-------------|------|----------|
| `RequestContextHolder` | Spring（每个请求自动）| Spring 自动 |
| `BaseInterceptor.currentController` | 拦截器 | `afterCompletion` |
| `AbstractController.THREAD_LOCAL_MULTIPART_HTTP_SERVLET_REQUEST` | Controller | `afterCompletion` |

**异步线程**（`@Async`、`CompletableFuture`、`CommonAsyncProcessor`）**不会**继承上述 ThreadLocal——异步执行体内调用 `getRequest()` 会 NPE。需要的数据在主请求线程提前取出。

## 实现细节摘录

### `getIp()`

```java
return ServletUtil.getClientIP(getRequest());
```

`ServletUtil.getClientIP` 默认按以下顺序：`X-Forwarded-For` → `X-Real-IP` → `Proxy-Client-IP` → `WL-Proxy-Client-IP` → `request.getRemoteAddr()`。

要改 header 顺序：在 yml 配 `ip.defaultHeaderName`（库提供这个 key，但需要在调 `getClientIP` 的位置自己读取并传给 hutool——`ServletUtil.getClientIP` 默认实现不读这个 key，仅作为约定常量存在于 `CommonPropertiesFinal.IP_DEFAULT_HEADER_NAME`）。

### `getRefererParameter()`

```java
String referer = getHeader(HttpHeaders.REFERER);
return HttpUtil.decodeParamMap(referer, CharsetUtil.charset(CharsetUtil.UTF_8));
```

`Referer` 中含 query string（`?a=1&b=2`）时，返回 `{a:1, b:2}`。无 referer 或无 query 时返回空 Map。

### `getObject(Class<T>)`

```java
return ServletUtil.toBean(getRequest(), tClass, true);
```

第三参数 `true` = 大小写不敏感（form 字段 `userName` 也能填到 `username` 字段）。
