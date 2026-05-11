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

- 默认大小限制 **10MB**，`setMaxSize(0)` 恢复无限制
- `useOriginalFilename=true` 时同名文件会**抛异常**（防覆盖）
- `savePath` 应硬编码或从配置读取，不要用请求参数（路径穿越风险）
- MIME 校验在保存后做，失败自动删文件。如需保存前校验，用 `setInputStreamType`
- Spring multipart 配置仍需：`spring.servlet.multipart.max-file-size`
- 返回的是**服务器本地路径**，如需提供下载/预览，需另外配置静态资源映射或写下载接口
