# Galaxy Controller — 文件上传

## 核心做法

### 安全探测（不解析请求体）

```java
@PostMapping("/upload")
public ApiResp<String> upload() {
    if (!hasFile()) {
        return ApiResp.fail("请选择文件");
    }
    // ...
}
```

> 💡 示例中 `ApiResp` / `Result` 是业务方自定义的响应包装类，本库不提供。

`hasFile()` 对非 multipart 请求返回 `false`，格式错误的 multipart 也返回 `false`（异常会被 `DefaultSystemLog.warn` 记录，便于排障）。

⚠️ `hasFile()` 进入 multipart 分支会**触发 Spring 实际解析请求体**（生成临时文件），并非"零成本探测"——只是不抛异常而已。

⚠️ 直接调 `createMultipart()` / `getMultiRequest()` 而非 multipart 请求会抛 `IllegalStateException`。建议在 controller 上限定 `consumes = "multipart/form-data"` 或前置 `hasFile()` 检查。

### 保存文件

```java
@PostMapping("/upload")
public Result upload() throws IOException {
    String path = createMultipart()
            .addFieldName("file")
            .setMaxSize("10MB")
            .setFileExt("jpg", "png")
            .save();
    return Result.ok(path);
}
```

## Builder 链式 API

| 方法 | 作用 | 默认 |
|------|------|------|
| `addFieldName(String)` | 添加表单字段名（多字段时 `saves()` 返回顺序与添加顺序一致） | 空集合 |
| `resetFieldName(String)` | 清空后只用该字段 | — |
| `setMultiple(boolean)` | 单字段多文件 | `false` |
| `setMaxSize(long)` | 字节上限（0 = 不限） | **10MB** |
| `setMaxSize(String)` | 如 `"10MB"` | — |
| `setFileExt(String...)` | 允许的后缀（大小写无关；空数组或 null 均视为不限） | 不限 |
| `setInputStreamType(String...)` | 文件头嗅探白名单（空数组或 null 均视为不限） | 不限 |
| `setContentTypePrefix(String)` | MIME 前缀 | 不限 |
| `setSavePath(String)` | 保存目录 | 系统临时目录 |
| `setUseOriginalFilename(boolean)` | 是否用原文件名（同名会抛异常，不会覆盖） | `false` |

## 保存方法

四个方法都声明 **`throws IOException`**（**checked**），调用方必须 `throws` 或 `try-catch`。校验失败（fileExt / maxSize / inputStreamType / contentTypePrefix / 非法文件名等）抛 `IllegalArgumentException`，磁盘写入失败抛 `IOException`。

| 方法 | 返回 | 适用场景 |
|------|------|----------|
| `save() throws IOException` | `String` 路径 | 单字段单文件 |
| `saves() throws IOException` | `String[]` | 多字段/多文件 |
| `saveAndName() throws IOException` | `String[2]` `[path, originalFilename]` | 单文件，要原文件名 |
| `saveAndNames() throws IOException` | `List<String[2]>` | 多文件，要原文件名 |

## 典型用法

### 单文件

```java
@PostMapping("/avatar")
public String uploadAvatar() throws IOException {
    return createMultipart()
            .addFieldName("avatar")
            .setMaxSize("2MB")
            .setContentTypePrefix("image")
            .setSavePath("/var/data/avatars")
            .save();
}
```

### 多文件

```java
@PostMapping("/photos")
public String[] uploadPhotos() throws IOException {
    return createMultipart()
            .addFieldName("photos")
            .setMultiple(true)
            .setMaxSize("5MB")
            .setFileExt("jpg", "png")
            .saves();
}
```

### 防后缀篡改（双重校验）

```java
createMultipart()
        .addFieldName("img")
        .setFileExt("jpg", "png", "gif")
        .setInputStreamType("jpg", "png", "gif")   // 文件头嗅探
        .save();
```

## 全局配置

```java
// 启动期设置全局默认保存路径
MultipartFileConfig.setFileTempPath("/var/data/uploads");
```

未设置时默认使用系统临时目录（`System.getProperty("java.io.tmpdir")`）。

## 注意事项

### ⚠️ 默认 10MB 限制

```java
// ❌ 如果没设 setMaxSize，默认只有 10MB
// 上传 15MB 文件会抛 IllegalArgumentException("maxSize:too big:...")

// ✅ 明确取消限制
.setMaxSize(0)

// ✅ 或设置更大的限制
.setMaxSize("100MB")
```

### ⚠️ savePath 的路径穿越风险

```java
// ❌ 危险：savePath 来自用户输入
.setSavePath(getParameter("path"))   // 用户传 "../../../etc" 可能写入系统目录

// ✅ 安全：硬编码或从配置读取
.setSavePath("/var/data/uploads")
```

### ⚠️ 返回的是服务器本地路径，不是 URL

```java
String path = createMultipart().save();  // 如 "/var/data/uploads/abc.jpg"
```

返回值是**服务器本地文件系统路径**，前端无法直接访问。如何让前端访问取决于业务层的设计（通常需要把 path 存到业务表、暴露 id，下载接口根据 id 反查 path），**不要直接把 path 暴露给前端使用**——会引入任意文件读取风险。

### ⚠️ useOriginalFilename=true 的覆盖风险

同名文件会**抛异常**而不是覆盖。如果你需要允许覆盖同名文件，不要开这个选项，自己在外层删除旧文件后再上传。

### ⚠️ Spring multipart 配置必须同步

```yaml
spring:
  servlet:
    multipart:
      enabled: true
      max-file-size: 100MB      # 必须 >= setMaxSize，否则 Spring 先拒绝
      max-request-size: 200MB
```

如果 `spring.servlet.multipart.max-file-size` 比 `setMaxSize` 小，Spring 会在到达 builder 之前拒绝，抛 `MaxUploadSizeExceededException`。
