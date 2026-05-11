# Galaxy Controller — 文件上传

## 核心做法

### 安全探测（不解析请求体）

```java
@PostMapping("/upload")
public Result upload() {
    if (!hasFile()) {
        return Result.fail("请选择文件");
    }
    // ...
}
```

`hasFile()` 对非 multipart 请求返回 `false`，格式错误的 multipart 也返回 `false`，不会抛异常。

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
| `addFieldName(String)` | 表单字段名 | 空集合 |
| `resetFieldName(String)` | 清空后只用该字段 | — |
| `setMultiple(boolean)` | 单字段多文件 | `false` |
| `setMaxSize(long)` | 字节上限（0 = 不限） | **10MB** |
| `setMaxSize(String)` | 如 `"10MB"` | — |
| `setFileExt(String...)` | 允许的后缀（大小写无关） | 不限 |
| `setInputStreamType(String...)` | 文件头嗅探白名单 | 不限 |
| `setContentTypePrefix(String)` | MIME 前缀 | 不限 |
| `setSavePath(String)` | 保存目录 | 系统临时目录 |
| `setUseOriginalFilename(boolean)` | 是否用原文件名 | `false` |

## 保存方法

| 方法 | 返回 | 适用场景 |
|------|------|----------|
| `save()` | `String` 路径 | 单字段单文件 |
| `saves()` | `String[]` | 多字段/多文件 |
| `saveAndName()` | `String[2]` `[path, originalFilename]` | 单文件，要原文件名 |
| `saveAndNames()` | `List<String[2]>` | 多文件，要原文件名 |

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

// ❌ 不能直接给前端访问
return Result.ok(path);  // 前端无法访问服务器的本地路径

// ✅ 需要另外提供下载/预览接口
return Result.ok("/api/download?path=" + URLEncoder.encode(path, "UTF-8"));
```

### ⚠️ useOriginalFilename=true 的覆盖风险

同名文件会**抛异常**而不是覆盖。如果你需要允许覆盖（如更新头像），不要开这个选项，自己在外层删除旧文件后再上传。

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
