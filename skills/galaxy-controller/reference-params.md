# Galaxy Controller — 参数获取

> 💡 本文档示例中的 `Result` 是业务方自定义的响应包装类，本库不提供；替换为你项目同类即可。


## 字符串参数

| 方法 | 返回 | 说明 |
|------|------|------|
| `getParameter(String name)` | `String` | 不存在返回 `null`。⚠️ 使用前先判空或改用带 `def` 版本 |
| `getParameter(String name, String def)` | `String` | 不存在返回 `def`；**空串视为合法值，不替换为 `def`** |
| `getParameters(String name)` | `String[]` | 不存在返回 `null`。⚠️ 遍历前需判空 |
| `getParametersMap()` | `Map<String,String[]>` | 不可修改深拷贝（值数组也是克隆，外部修改不影响容器） |

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

## 类型化参数（带默认值）

| 方法 | 返回 | 说明 |
|------|------|------|
| `getParameterInt(String name, int def)` | `int` | 不存在/空串返回 `def`；非法值抛 `IllegalArgumentException` |
| `getParameterLong(String name, long def)` | `long` | 同上 |
| `getParameterBool(String name, boolean def)` | `boolean` | 支持 true/false/1/0/yes/no/on/off；非法值抛异常 |
| `getParameterDate(name, LocalDate def)` | `LocalDate` | 默认 pattern `yyyy-MM-dd` |
| `getParameterDate(name, pattern, def)` | `LocalDate` | 自定义 pattern |
| `getParameterDateTime(name, LocalDateTime def)` | `LocalDateTime` | 默认 pattern `yyyy-MM-dd HH:mm:ss` |
| `getParameterDateTime(name, pattern, def)` | `LocalDateTime` | 自定义 pattern |
| `getParameterEnum(name, enumClass, def)` | `<E extends Enum<E>>` | 按 `Enum.name()` 不区分大小写匹配；不匹配抛 `IllegalArgumentException` |
| `getParameterDecimal(name, def)` | `BigDecimal` | `new BigDecimal(value)` 保精度 |

### ⚠️ "非法值"的边界

`getParameterInt` 内部用 `Integer.parseInt()`，以下情况都会抛异常：
- `"abc"`、`"25.5"`、`" 25 "`（含空格）、`""`（空串）
- 合法：`"25"`、`"-3"`、`"+5"`、`"0"`

```java
// 如果你想宽容处理（如 trim、忽略非数字），先用 getParameter 自己处理
String raw = getParameter("age");
if (StrUtil.isBlank(raw)) return 0;
try {
    return Integer.parseInt(raw.trim());
} catch (NumberFormatException e) {
    return 0;
}
```

### 类型化参数示例

```java
@GetMapping("/orders")
public Result list() {
    LocalDate from = getParameterDate("from", LocalDate.now().minusDays(7));
    LocalDate to   = getParameterDate("to", LocalDate.now());
    OrderStatus status = getParameterEnum("status", OrderStatus.class, OrderStatus.ALL);
    BigDecimal minAmount = getParameterDecimal("minAmount", BigDecimal.ZERO);
    int page = getParameterInt("page", 1);
    int size = getParameterInt("size", 20);
    return Result.ok(orderService.list(from, to, status, minAmount, page, size));
}
```

## 必填参数

| 方法 | 返回 | 说明 |
|------|------|------|
| `getParameterRequired(String name)` | `String` | 缺失/空白抛 `IllegalArgumentException("参数 X 不能为空")` |
| `getParameterIntRequired(String name)` | `int` | + 非法整数抛异常 |
| `getParameterLongRequired(String name)` | `long` | + 非法长整数抛异常 |

```java
@PostMapping("/orders")
public Result create() {
    long userId = getParameterLongRequired("userId");
    String sku  = getParameterRequired("sku");
    int qty     = getParameterIntRequired("qty");
    return Result.ok(orderService.create(userId, sku, qty));
}
```

⚠️ 必填族只覆盖最常用的 String/int/long。需要"必填的 LocalDate / Enum / BigDecimal"时自己组合：
```java
String dateStr = getParameterRequired("date");
LocalDate date = LocalDate.parse(dateStr);   // 业务自己 parse
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
    return Result.ok(form);
}
```

| 方法 | 说明 |
|------|------|
| `getObject(Class<T>)` | 类型不匹配时**抛异常**。字段名大小写不敏感 |
| `getObject(Class<T>, boolean ignoreError)` | `ignoreError=true` 时类型不匹配字段保持默认值（⚠️ 可能导致数据静默丢失） |

### ⚠️ Mass Assignment 风险（重要）

`getObject` 基于字段名自动绑定，**无白名单**：客户端能传入任意参数覆盖 bean 字段。

```java
// ❌ 危险：User 含 isAdmin、role 等敏感字段
@PostMapping("/profile/update")
public Result update() {
    User user = getObject(User.class);    // 攻击者传 ?isAdmin=true&role=ROOT 直接提权
    userService.save(user);
    return Result.ok();
}

// ✅ 安全：DTO 只含可被前端修改的字段
@PostMapping("/profile/update")
public Result update() {
    ProfileUpdateDto dto = getObject(ProfileUpdateDto.class);   // 只含 nickname、avatar
    userService.updateProfile(getCurrentUserId(), dto);
    return Result.ok();
}
```

**禁止**直接绑定持久层 entity，**必须**使用专用 DTO。

### 能力局限

hutool `ServletUtil.toBean` 对**嵌套对象、List 字段、复杂泛型支持有限**。这种场景请改用 Spring MVC 的 `@RequestBody` + Jackson：

```java
@PostMapping("/orders")
public Result create(@RequestBody @Valid CreateOrderDto dto) {   // Spring 自带白名单 + 校验
    return Result.ok(orderService.create(dto));
}
```

### ⚠️ `ignoreError=true` 的风险

```java
// 前端传了 age=abc
UserForm form = getObject(UserForm.class, true);
// form.getAge() 是 0（默认值），而不是抛异常
// 调用者以为保存成功了，实际 age 是错的
```

建议默认用 `getObject(Class)`（抛异常），明确需要容错时才用 `ignoreError=true`。

## 请求体读取（JSON body）

适合 `Content-Type: application/json` 的请求。

| 方法 | 返回 | 说明 |
|------|------|------|
| `getBody()` | `String` | 原始 body |
| `getBodyJson(Class<T>)` | `T` | body 空返回 `null` |
| `getBodyJson()` | `JSONObject` | body 空返回 `null`；非 JSON 对象抛 hutool 异常 |

```java
@PostMapping(value = "/orders", consumes = "application/json")
public Result create() {
    CreateOrderDto dto = getBodyJson(CreateOrderDto.class);
    return Result.ok(orderService.create(dto));
}
```

### ⚠️ 输入流仅能消费一次

```java
// ❌ 双读：filter 已读 → controller 再调 → 抛 IORuntimeException
filter.doFilter:   request.getReader().lines().forEach(...)
controller:        String body = getBody();   // 报错

// ✅ 解决方案 1：filter 用 ContentCachingRequestWrapper
// ✅ 解决方案 2：filter 不读 body
```

### ⚠️ multipart 请求不能用 getBody

multipart 表单字段会被 servlet 容器从输入流读出，再调 `getBody()` 通常返回空串。文件上传请用 `createMultipart()`（详见 [reference-upload.md](reference-upload.md)）。

## Content-Type 判定（参数前置）

```java
@PostMapping("/save")
public Result save() {
    if (isJson()) {
        Data data = getBodyJson(Data.class);
        return Result.ok(service.save(data));
    }
    if (isFormUrlEncoded()) {
        Data data = getObject(Data.class);
        return Result.ok(service.save(data));
    }
    return Result.fail("不支持的 Content-Type");
}
```

判定方法：`isJson()` / `isFormUrlEncoded()` / `isMultipart()` / `isContentType(prefix)`。

⚠️ **不要用 `isAjax()` 做 Content-Type 判定**——它只看 `X-Requested-With`，不反映请求体格式。
