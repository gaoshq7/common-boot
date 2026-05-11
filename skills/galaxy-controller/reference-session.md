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
| 用途 | 跨请求保持的数据 | 单次请求内的临时数据 |
| 方法 | `getSessionAttribute` / `setSessionAttribute` | `getAttribute` / `setAttribute` |

```java
// 跨请求保持的数据用 Session
setSessionAttribute("key", value);

// 单次请求内传递的数据用 Request 属性
setAttribute("key", value);
```

## 典型用法

```java
@PostMapping("/store")
public Result store() {
    setSessionAttribute("key", "some-value");
    return Result.ok();
}

@GetMapping("/read")
public Result read() {
    String value = getSessionAttribute("key");
    return Result.ok(value);
}

@DeleteMapping("/clear")
public Result clear() {
    removeSessionAttribute("key");
    return Result.ok();
}
```

### ⚠️ `getSession()` 会意外创建 session

即使只是"读"session，如果不存在也会创建一个空 session，增加服务器内存压力和分布式同步开销。

```java
// ❌ 如果只是想检查 session 中是否存在某个值，不要这样写
Object value = getSessionAttributeObj("key");  // 内部调 getSession()，会创建空 session

// ✅ 明确不需要创建时
HttpSession session = getSession(false);
if (session == null) return Result.fail("会话不存在");
Object value = session.getAttribute("key");
```
