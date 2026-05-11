---
name: galaxy-controller
description: Spring MVC Controller 继承 AbstractController 基类，无需手动注入 HttpServletRequest 即可获取 request/response/IP/UA/参数/Session/文件上传等。当用户写"@RestController"、"@Controller"、"继承 AbstractController"、"写接口"、"获取参数"、"getParameter"、"文件上传"、"upload"、"Session"、"登录"时使用。
---

# Galaxy Controller

## 核心做法

```java
@RestController
public class UserController extends AbstractController {
    // 所有 protected 方法直接可用
}
```

`AbstractController extends BaseCallbackController`，底层用 `RequestContextHolder` 拿当前请求。

## 功能索引

| 功能 | 读哪个文档 |
|------|-----------|
| 基础对象（request/response/session/IP/UA） | 本文档下方 |
| 参数获取（getParameter/getObject） | [reference-params.md](reference-params.md) |
| Session 操作（登录/登出） | [reference-session.md](reference-session.md) |
| 文件上传（MultipartFileBuilder） | [reference-upload.md](reference-upload.md) |
| 完整方法签名清单 | [reference.md](reference.md) |

## 最常用 API

```java
// 请求对象
getRequest()                    → HttpServletRequest
getResponse()                   → HttpServletResponse（可能为 null）
getSession()                    → HttpSession（自动创建）
getSession(boolean create)      → HttpSession

// 客户端信息
getIp()                         → String（优先代理头，可被伪造）
getRemoteIp()                   → String（TCP 层，不可伪造）
isAjax()                        → boolean
getUserAgent()                  → String（可能为 null）
getUserAgent(String def)        → String（带默认值）

// Header / Cookie（不存在时返回 null，易 NPE，建议用带 def 的重载）
getHeader(String name)          → String
getHeader(String name, String def) → String
getCookieValue(String name)     → String
getCookieValue(String name, String def) → String

// 参数获取（详见 reference-params.md）
getParameter(String name)       → String
getParameter(String name, String def) → String
getParameterInt(String name, int def) → int
getObject(Class<T> tClass)      → T
```

## 引入

`io.github.gaoshq7:common-boot:1.0.2`

## 注意事项

- 不在 web 请求线程中调用（如 `@Async`）会抛 `IllegalStateException`
- 绝大多数方法是 `protected`，只能在继承的子类中使用
