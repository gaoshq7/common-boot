# Galaxy Controller — 响应输出 + 文件下载

## ⚠️ 调用约束（最重要）

`writeJson` / `writeText` / `writeHtml` / `redirect` / `sendError` / `download` 都**直接操作 response 输出流**。调用后 Controller 方法**必须返回 void**（或返回类型为 `ResponseEntity` 但返回 `null`），否则 Spring 会对返回值再做一次序列化，导致响应体重复或损坏。

```java
// ❌ 双重输出
@GetMapping("/x")
public Result foo() {
    writeJson(data);
    return Result.ok();   // Spring 二次序列化，前端拿到的 JSON 损坏
}

// ✅ 正确：方法返回 void
@GetMapping("/x")
public void foo() {
    writeJson(data);
}

// ✅ 也对：业务自己决定要不要让 Spring 渲染
@GetMapping("/x")
public Result foo() {
    return Result.ok(data);   // 不调 writeJson，让 Spring 自己渲染
}
```

## 响应输出

| 方法 | Content-Type | 用途 |
|------|--------------|------|
| `writeJson(Object data)` | `application/json;charset=UTF-8` | 任意对象（含 null）经 `JSONUtil.toJsonStr` 序列化 |
| `writeText(String text)` | `text/plain;charset=UTF-8` | 纯文本，null 视为空串 |
| `writeHtml(String html)` | `text/html;charset=UTF-8` | HTML 内容；⚠️ 调用方自行做 XSS 转义 |
| `redirect(String url)` | — | 302 重定向 |
| `status(int code)` | — | 设响应码（不立即提交，后续仍可写入响应） |
| `sendError(int, String)` | — | 错误页跳转，立即提交 |
| `setNoCache()` | — | 三件套：Cache-Control + Pragma + Expires |

### `writeJson` 典型用法

```java
@GetMapping("/users/{id}")
public void detail(@PathVariable long id) {
    User user = userService.find(id);
    if (user == null) {
        sendError(404, "user not found");
        return;
    }
    writeJson(user);
}
```

### ⚠️ `writeJson` 已是 JSON 字符串的陷阱

```java
String alreadyJson = "{\"a\":1}";
writeJson(alreadyJson);   // ❌ 输出 "\"{\\\"a\\\":1}\""（再 escape 一次）

writeText(alreadyJson);   // ✅ 输出 {"a":1}
```

如果手里已经是 JSON 字符串，用 `writeText` 但记得手动 `response.setContentType("application/json;charset=UTF-8")` 或先 `JSONUtil.parse(alreadyJson)` 解回对象。

### `setNoCache` 用途

```java
@GetMapping("/admin/sensitive-list")
public void list() {
    setNoCache();
    writeJson(adminService.list());
}
```

发出的响应头：
```
Cache-Control: no-store, no-cache, must-revalidate, max-age=0
Pragma: no-cache
Expires: 0
```

### `redirect` / `sendError`

```java
@GetMapping("/login-required")
public void check() {
    if (!isLoggedIn()) {
        redirect("/login");
        return;
    }
    // ...
}

@GetMapping("/admin/op")
public void op() {
    if (!isInternalIp()) {
        sendError(403, "管理接口仅限内网访问");
        return;
    }
    // ...
}
```

## 文件下载

⚠️ 与 `writeJson` 同样的约束：**调用后 Controller 方法必须返回 void**。

| 方法 | 用途 |
|------|------|
| `download(File file)` | 用 `file.getName()` 作为下载文件名 |
| `download(File file, String filename)` | 指定下载文件名，**自动处理中文 RFC 5987 编码** |
| `download(InputStream in, String filename, long contentLength)` | 流式下载（大文件、网络流、压缩流均可）；`contentLength<0` 表示未知 |

### 典型用法

```java
@GetMapping("/files/{id}")
public void download(@PathVariable long id) {
    FileRecord record = fileService.findById(id);
    if (record == null) {
        sendError(404, "文件不存在");
        return;
    }
    File local = new File(record.getPath());
    download(local, record.getOriginalName());   // 中文 originalName 自动 RFC 5987 编码
}
```

### 大文件 / 流式数据

```java
@GetMapping("/reports/{id}.csv")
public void exportCsv(@PathVariable long id) {
    setNoCache();
    try (InputStream stream = reportService.exportCsvStream(id)) {
        download(stream, "report-" + id + ".csv", -1L);   // size 未知
    }
}
```

⚠️ `download(InputStream, ...)` 会**关闭传入的 InputStream**——上面 try-with-resources 看起来是双重关闭，但 hutool/JDK 的 close 是幂等的，不会出错。

### Content-Disposition 编码细节

`download(File, String filename)` 输出的响应头：

```
Content-Type: application/octet-stream
Content-Disposition: attachment; filename="..."; filename*=UTF-8''<URL-encoded>
Content-Length: <file.length()>
```

`filename="..."` 是 ASCII 兜底（老浏览器看），`filename*=UTF-8''...` 是 RFC 5987 现代编码（IE10+、Chrome、Firefox、Safari 都看）。中文文件名两者用同一份 URL-encoded 值。

### ⚠️ 不要把保存路径暴露给前端

```java
// ❌ 危险：返回服务器本地路径让前端拼出 download URL
return ApiResp.ok(file.getAbsolutePath());

// ✅ 安全：返回业务 id，前端调 /files/{id} 触发下载，服务端反查路径
return ApiResp.ok(record.getId());
```

把本地路径暴露给前端会引入"任意文件读取"漏洞（攻击者可以传 `path=../../etc/passwd`）。

> 💡 示例中 `ApiResp` 是业务方自定义响应包装类，本库不提供。

## Cookie 写入（响应头的一部分）

详细 API 见 [reference.md](reference.md) 第 5 节。这里给出典型场景：

```java
// 设置会话 token（带 HttpOnly + Secure，3600 秒过期）
setCookie("session", token, 3600, "/", true, true);

// 删除（path 必须与写入时一致）
removeCookie("session");
```

⚠️ 删除 Cookie 时浏览器要求 `name + path` 与写入时一致，否则不会被删。生产环境 HTTPS 下务必显式设 `secure=true`。

## 响应状态码

| 方法 | 区别 |
|------|------|
| `status(int code)` | 仅设响应码，不立即提交，后续仍可写入 body |
| `sendError(int code, String message)` | 立即提交，跳转到 servlet 容器的 error page；调用后必须 return void |

```java
// 想自定义错误响应体：用 status + writeJson
status(400);
writeJson(ApiResp.fail("INVALID_PARAM", "userId 必填"));   // ApiResp 是业务包装类

// 想用 servlet 容器/Spring 的统一错误页：用 sendError
sendError(400, "userId 必填");
```

## 异常时机

| 错误场景 | 抛出的异常 |
|---------|----------|
| 当前请求无 response（非 web 线程，如 `@Async`） | `IllegalStateException` |
| 写入响应时 `IOException`（如客户端断连） | `IORuntimeException` |
| `redirect(null)` / `download(null, ...)` | `NullPointerException`（Objects.requireNonNull） |
| `download(File)` 但文件不存在 | `IllegalArgumentException` |
