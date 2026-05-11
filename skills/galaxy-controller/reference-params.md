# Galaxy Controller — 参数获取

## 字符串参数

| 方法 | 返回 | 说明 |
|------|------|------|
| `getParameter(String name)` | `String` | 不存在返回 `null`。⚠️ 使用前先判空或改用带 `def` 版本 |
| `getParameter(String name, String def)` | `String` | 不存在返回 `def`；**空串视为合法值，不替换为 `def`** |
| `getParameters(String name)` | `String[]` | 不存在返回 `null`。⚠️ 遍历前需判空 |
| `getParametersMap()` | `Map<String,String[]>` | 所有参数，返回不可修改的副本 |
| `getRefererParameter()` | `Map<String,String>` | 解析 Referer 的 query 参数；Referer 可被伪造，不可作为安全决策依据 |

### ⚠️ `getParameter(name, def)` 的空串陷阱

`?keyword=`（值为空串）**不会**返回 `def`，而是返回 `""`。

```java
// 前端传了 ?keyword=
getParameter("keyword", "default");  // 返回 ""，不是 "default"

// 只有不传 keyword 时才返回 default
getParameter("keyword", "default");  // 返回 "default"
```

如果你希望"空串也视为缺失"，需要自己包装：
```java
String keyword = getParameter("keyword");
if (StrUtil.isBlank(keyword)) keyword = "default";
```

## 类型化参数

| 方法 | 返回 | 说明 |
|------|------|------|
| `getParameterInt(String name, int def)` | `int` | 不存在/空串返回 `def`；**非法值抛 `IllegalArgumentException`** |
| `getParameterLong(String name, long def)` | `long` | 同上 |
| `getParameterBool(String name, boolean def)` | `boolean` | 支持 true/false/1/0/yes/no/on/off；非法值抛异常 |

### ⚠️ "非法值"的边界

`getParameterInt` 内部使用 `Integer.parseInt()`，以下情况都会抛异常：
- `"abc"`、`"25.5"`、`" 25 "`（含空格）、`""`（空串）
- 合法：`"25"`、`"-3"`、`"+5"`、`"0"`

```java
// 如果你想宽容处理（如trim、忽略非数字），先用 getParameter 自己处理
String raw = getParameter("age");
if (StrUtil.isBlank(raw)) return 0;
try {
    return Integer.parseInt(raw.trim());
} catch (NumberFormatException e) {
    return 0;
}
```

## 表单转 Bean

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

| 方法 | 说明 |
|------|------|
| `getObject(Class<T>)` | 类型不匹配时**抛异常**。字段名大小写不敏感 |
| `getObject(Class<T>, boolean ignoreError)` | `ignoreError=true` 时类型不匹配字段保持默认值（⚠️ 可能导致数据静默丢失） |

### ⚠️ `ignoreError=true` 的风险

```java
// 前端传了 age=abc
UserForm form = getObject(UserForm.class, true);
// form.getAge() 是 0（默认值），而不是抛异常
// 调用者以为保存成功了，实际 age 是错的
```

建议默认用 `getObject(Class)`（抛异常），明确需要容错时才用 `ignoreError=true`。
