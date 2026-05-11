# Galaxy Controller — Session 操作

## API

| 方法 | 返回 | 说明 |
|------|------|------|
| `getSession()` | `HttpSession` | 不存在时自动创建 |
| `getSession(boolean create)` | `HttpSession` | `false` 时不创建，可能返回 `null` |
| `getSessionAttribute(String name)` | `String` | 不存在返回 `null` |
| `getSessionAttributeObj(String name)` | `Object` | 拿原始对象 |
| `setSessionAttribute(String name, Object value)` | — | 写入 |
| `removeSessionAttribute(String name)` | — | 删除 |

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
