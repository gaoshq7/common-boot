# Galaxy Controller — 参数获取

## 字符串参数

| 方法 | 返回 | 说明 |
|------|------|------|
| `getParameter(String name)` | `String` | 不存在返回 `null` |
| `getParameter(String name, String def)` | `String` | 不存在返回 `def`；**空串视为合法值，不替换** |
| `getParameters(String name)` | `String[]` | 不存在返回 `null` |
| `getParametersMap()` | `Map<String,String[]>` | 所有参数 |
| `getRefererParameter()` | `Map<String,String>` | 解析 Referer 的 query 参数 |

## 类型化参数

| 方法 | 返回 | 说明 |
|------|------|------|
| `getParameterInt(String name, int def)` | `int` | 不存在/空串返回 `def`；**非法值（如"abc"）抛异常** |
| `getParameterLong(String name, long def)` | `long` | 同上 |
| `getParameterBool(String name, boolean def)` | `boolean` | 支持 true/false/1/0/yes/no/on/off；非法值抛异常 |

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

`getObject(Class<T>)` 类型不匹配时**抛异常**。如需容错：`getObject(Class<T>, true)`。
