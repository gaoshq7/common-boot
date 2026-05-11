# Galaxy Controller — Session 操作

## API

| 方法 | 返回 | 说明 |
|------|------|------|
| `getSession()` | `HttpSession` | 不存在时**自动创建** session |
| `getSession(boolean create)` | `HttpSession` | `false` 时不创建，可能返回 `null` |
| `getSessionAttribute(String name)` | `String` | 不存在返回 `null` |
| `getSessionAttributeObj(String name)` | `Object` | 拿原始对象；不存在返回 `null` |
| `setSessionAttribute(String name, Object value)` | — | 写入 session |
| `removeSessionAttribute(String name)` | — | 删除 |

### ⚠️ Session vs Request Attribute 的区别

| | Session | Request |
|--|---------|---------|
| 生命周期 | 多次请求（默认 30min） | 单次请求 |
| 用途 | 登录态、用户信息 | 单次请求内的临时数据 |
| 方法 | `getSessionAttribute` / `setSessionAttribute` | `getAttribute` / `setAttribute` |

```java
// ❌ 错误：用 Request 属性存登录态（请求结束就丢失）
setAttribute("user", user);

// ✅ 正确：用 Session 存登录态
setSessionAttribute("user", user);
```

## 典型用法

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

@PostMapping("/logout")
public Result logout() {
    removeSessionAttribute("user");
    return Result.ok();
}
```

### ⚠️ `getSession()` 会意外创建 session

即使只是"读"session，如果不存在也会创建一个空 session，增加服务器内存压力和分布式同步开销。

```java
// ❌ 如果只是想检查是否已登录，不要这样写
User user = (User) getSessionAttributeObj("user");  // 内部调 getSession()，会创建空 session

// ✅ 明确不需要创建时
HttpSession session = getSession(false);
if (session == null) return Result.fail(401, "未登录");
User user = (User) session.getAttribute("user");
```
