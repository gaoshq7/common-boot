---
name: galaxy-controller
description: Spring MVC Controller 继承 AbstractController 基类，无需手动注入 HttpServletRequest 即可获取 request/response/IP/UA/参数/Session/Cookie 读写/请求体 JSON/响应输出/文件上传下载。当用户写"@RestController"、"@Controller"、"继承 AbstractController"、"写接口"、"获取参数"、"必填参数"、"getParameter"、"文件上传"、"upload"、"文件下载"、"download"、"writeJson"、"redirect"、"setCookie"、"Session"时使用。
---

# Galaxy Controller

继承 `AbstractController`，所有 `protected` 方法直接可用：请求信息、参数、Cookie、Session、请求体、响应输出、文件上传下载。

## 快速上手（5 分钟）

### 1. 引入依赖

```xml
<dependency>
    <groupId>io.github.gaoshq7</groupId>
    <artifactId>common-boot</artifactId>
    <version>1.0.2</version>
</dependency>
```

### 2. 配置（仅文件上传需要）

```yaml
spring:
  servlet:
    multipart:
      enabled: true
      max-file-size: 100MB
      max-request-size: 200MB
```

### 3. 写一个 Controller

```java
@RestController
@RequestMapping("/api/users")
public class UserController extends AbstractController {

    @Autowired
    private UserService userService;

    @GetMapping("/{id}")
    public ApiResp<User> detail(@PathVariable long id) {
        User user = userService.find(id);
        return ApiResp.ok(user);
    }

    @PostMapping
    public ApiResp<Long> create() {
        String name = getParameterRequired("name");          // 缺失抛异常
        int age = getParameterInt("age", 0);                  // 缺失返 0
        long userId = userService.create(name, age);
        return ApiResp.ok(userId);
    }

    @PostMapping("/avatar")
    public ApiResp<String> uploadAvatar() throws IOException {
        String path = createMultipart()
                .addFieldName("avatar")
                .setMaxSize("2MB")
                .setContentTypePrefix("image")
                .save();
        return ApiResp.ok(path);
    }
}
```

> 💡 上面 `ApiResp` 是**业务方自定义**的响应包装类（任何项目通常都有一个），本库不提供。本文档所有示例用 `ApiResp` / `Result` / `Result.ok()` 等都是同类业务约定，按你项目实际类名替换即可。

### 4. 启动并验证

`mvn spring-boot:run` 后调用 `/api/users/1` 即可。

## 类层级

```
BaseCallbackController（提供请求/会话/属性的基础访问）
    ↑ extends
AbstractController（追加客户端信息、参数、请求体、Cookie 读写、响应输出、文件上传下载）
    ↑ extends
（你的 Controller）
```

## 功能索引

| 功能 | 文档 |
|------|------|
| 完整方法签名清单（13 个区域） | [reference.md](reference.md) |
| 参数获取（必填、类型化、Date/Enum/BigDecimal、请求体 JSON） | [reference-params.md](reference-params.md) |
| 响应输出 + 文件下载（writeJson/redirect/download） | [reference-response.md](reference-response.md) |
| 文件上传（MultipartFileBuilder） | [reference-upload.md](reference-upload.md) |
| Session 属性读写 | [reference-session.md](reference-session.md) |
| 基础对象（request/response/session/IP/UA/Cookie）| 本文档下方 |

## 最常用 API

```java
// 请求对象
getRequest()                    → HttpServletRequest
getResponse()                   → HttpServletResponse（非 web 线程时为 null）
getSession()                    → HttpSession（自动创建）
getSession(boolean create)      → HttpSession

// 客户端信息
getIp()                         → String（优先代理头，可被伪造）
getRemoteIp()                   → String（TCP 层，不可伪造）
isLocalIp() / isInternalIp()    → boolean（基于 getRemoteIp，安全决策用此）
getUserAgent()                  → String（可能为 null）
parseUserAgent()                → cn.hutool.http.useragent.UserAgent
isMobile()                      → boolean

// 请求方法
getRequestMethod()              → String
isGet() / isPost()              → boolean
isMethod("PUT") / requireMethod("POST", "PUT")  // 后者不匹配抛 IllegalStateException
isAjax()                        → boolean（仅 X-Requested-With；判 JSON 用 isJson()）

// Content-Type 判定
isJson() / isFormUrlEncoded() / isMultipart() / isContentType(prefix)

// Header / Cookie 读
getHeader(name)                 → String
getHeader(name, def)            → String
getCookieValue(name)            → String
getCookieValue(name, def)       → String

// Cookie 写
setCookie(name, value)                     // 会话级
setCookie(name, value, maxAge)             // path=/, HttpOnly=true
setCookie(name, value, maxAge, path, httpOnly, secure)
removeCookie(name)                          // path=/
removeCookie(name, path)

// URL 信息
getRequestUrl() / getRequestUri()
getContextPath() / getQueryString() / getBaseUrl()
getReferer() / getOrigin()

// 参数（详见 reference-params.md）
getParameter(name) / getParameter(name, def)
getParameterInt(name, def) / getParameterLong(name, def) / getParameterBool(name, def)
getParameterRequired(name) / getParameterIntRequired(name) / getParameterLongRequired(name)
getParameterDate(name, def) / getParameterDateTime(name, def)
getParameterEnum(name, Class, def) / getParameterDecimal(name, def)

// 请求体（仅能读一次）
getBody()                       → String
getBodyJson(Class<T>)           → T
getBodyJson()                   → cn.hutool.json.JSONObject

// 响应输出（详见 reference-response.md，调用后必须 return void）
writeJson(data) / writeText(text) / writeHtml(html)
redirect(url) / status(code) / sendError(code, msg) / setNoCache()
download(file) / download(file, filename) / download(stream, filename, size)

// 文件上传（详见 reference-upload.md）
hasFile()                       → boolean
createMultipart()               → MultipartFileBuilder
```

## 注意事项

- 示例中的 `ApiResp` / `Result` / `Result.ok(...)` 等都是**业务方自定义**的响应包装类，本库不提供；替换为你项目的同类即可。
- 不在 web 请求线程中调用（如 `@Async`）会抛 `IllegalStateException`
- 绝大多数方法是 `protected`，只能在继承的子类中使用
- ⚠️ `writeJson` / `writeText` / `writeHtml` / `redirect` / `sendError` / `download` 调用后 Controller 方法**必须返回 void**（或 `ResponseEntity` 返回 null），否则 Spring 会对返回值二次序列化导致响应损坏
- ⚠️ `getBody()` / `getBodyJson()` 的输入流仅能消费一次；filter 或 `@RequestBody` 提前消费过会抛 `IORuntimeException`
- ⚠️ `getObject(Class)` 存在 Mass Assignment 风险，**禁止**直接绑定持久层 entity，必须用 DTO
