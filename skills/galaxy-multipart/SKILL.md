---
name: galaxy-multipart
description: Spring MVC 文件上传处理，链式 Builder 限制大小/后缀/MIME 类型/字节流类型/保存路径，单文件或多文件批量保存。当用户写"文件上传"、"MultipartFile"、"upload"、"MultipartHttpServletRequest"、"@RequestParam MultipartFile"相关代码时使用。
---

# Galaxy Multipart

## 何时使用

下游项目编写**文件上传接口**时加载本 SKILL：

- 单字段单文件 / 单字段多文件 / 多字段
- 限制：文件大小、文件名后缀、MIME ContentType 前缀、字节流类型嗅探
- 自定义保存路径，是否使用原文件名

不要自己手写 `multipart.getFile().transferTo(...)`——库已经把校验、保存、命名、清理打包好了。

## 引入

`io.github.gaoshq7:common-boot:1.0.2`。Controller **必须继承 `AbstractController`** 才能用 `createMultipart()`。

## 核心做法

```java
@RestController
public class FileController extends AbstractController {

    @PostMapping("/upload")
    public Result upload() throws IOException {
        String[] result = createMultipart()
                .addFieldName("file")
                .setMaxSize("10MB")
                .setFileExt("jpg", "png", "pdf")
                .setContentTypePrefix("image")
                .setSavePath("/var/data/uploads")
                .saveAndName();   // [savedPath, originalFilename]
        return Result.ok(result);
    }
}
```

## 核心 API

### 构造与字段

通过 `AbstractController.createMultipart()` 获得 `MultipartFileBuilder` 实例（内部包装当前请求的 `MultipartHttpServletRequest`，自动启用 `@RequestPart` 解析）。

| 链式方法（返回 `this`） | 作用 | 默认 |
|------------------------|------|------|
| `addFieldName(String)` | 追加表单字段名 | 空集合 |
| `resetFieldName(String)` | 清空后只用这一个字段 | — |
| `setMaxSize(long)` | 字节大小上限 | 0（不限） |
| `setMaxSize(String)` | 用字符串如 `"10MB"`、`"500KB"`（logback `FileSize` 解析） | — |
| `setFileExt(String...)` | 允许的后缀（不带点，大小写无关） | null（不限） |
| `setInputStreamType(String...)` | 字节流嗅探类型（hutool `FileTypeUtil.getType`） | null |
| `setContentTypePrefix(String)` | MIME 前缀（如 `"image"`、`"video"`） | null |
| `setSavePath(String)` | 保存目录 | `MultipartFileConfig.getFileTempPath()`（系统临时目录） |
| `setUseOriginalFilename(boolean)` | true=用原文件名；false=`{objectId}_{unicode文件名}` | false |
| `setMultiple(boolean)` | true=单字段多文件；false=单字段单文件 | false |

### 保存方法

| 方法 | 返回 | 适用 |
|------|------|------|
| `save() → String` | 单个保存路径 | 必须 `fieldNames.size() == 1` 且 `multiple == false` |
| `saves() → String[]` | 一组保存路径 | 多字段 / 多文件 |
| `saveAndName() → String[2]` | `[savedPath, originalFilename]` | 单文件，附原文件名 |
| `saveAndNames() → List<String[2]>` | 列表，每项 `[savedPath, originalFilename]` | 多字段 / 多文件，附原文件名 |

### 全局配置

```java
MultipartFileConfig.setFileTempPath(String path);   // 全局默认保存路径（启动期设置）
String defaultPath = MultipartFileConfig.getFileTempPath();   // 默认是 hutool UserInfo.getTempDir()
```

### 辅助方法（继承自 `AbstractController`）

| 方法 | 返回 | 用途 |
|------|------|------|
| `createMultipart()` | `MultipartFileBuilder` | 创建 builder（线程缓存 `MultipartHttpServletRequest`） |
| `hasFile()` | `boolean` | 当前请求是否有文件 |
| `getMultiRequest()` | `MultipartHttpServletRequest` | 拿原生 request（自己处理） |

## 典型用法

### 单文件上传

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

### 单字段多文件

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

### 多字段，需要原文件名

```java
@PostMapping("/import")
public List<String[]> importDocs() throws IOException {
    return createMultipart()
            .addFieldName("contract")
            .addFieldName("invoice")
            .setFileExt("pdf")
            .setSavePath("/var/data/docs")
            .saveAndNames();   // 每项 [path, originalName]
}
```

### 字节流类型嗅探（防止改后缀绕过校验）

```java
createMultipart()
        .addFieldName("img")
        .setFileExt("jpg", "png", "gif")
        .setInputStreamType("jpg", "png", "gif")   // 双重校验：后缀 + 字节流嗅探
        .save();
```

`setInputStreamType` 走 hutool `FileTypeUtil.getType(InputStream)`，按文件头 magic number 识别——比 `Content-Type` 可靠。

## 注意事项

- **校验顺序**：后缀 → 文件大小 → 字节流类型 → 保存到磁盘 → MIME 前缀。MIME 校验**在保存之后**做——失败会自动 `FileUtil.del(filePath)` 删除已写入文件，但短暂落盘。要避免这一点，用 `setInputStreamType`（保存前嗅探）。
- **`save()` 的前置条件**：`fieldNames.size() == 1` 且 `multiple == false`。否则抛 `IllegalArgumentException("fieldNames size:X  use saves")`。
- **空文件名 / 空内容**：库会抛 `IllegalArgumentException`。
- **不使用原文件名**：保存路径形如 `{savePath}/{ObjectId}_{unicode编码后的文件名}`，反斜杠被替换成 `_`。要恢复成原名请保存 `saveAndName()` 返回的第二位。
- **路径自动 normalize**：`FileUtil.normalize` 会处理 `..`、双斜杠等——但不做白名单校验，**`savePath` 来自用户输入是危险的**（路径穿越）。`savePath` 应在代码里硬编码或从配置读，不要用请求参数。
- **临时目录默认**：未设 `setSavePath` 也未设 `MultipartFileConfig.setFileTempPath` 时，落 hutool `UserInfo.getTempDir()`（OS 临时目录）。
- **Spring multipart 配置仍要做**：`spring.servlet.multipart.max-file-size` / `max-request-size` 要够大，否则在到达 `MultipartFileBuilder` 前就被 Spring 拒绝了。
- 处理完后 `AbstractController.clearResources()` 会在 `BaseInterceptor.afterCompletion` 自动调用，清理线程缓存的 `MultipartHttpServletRequest`。

更多：完整字段语义、校验流程见 [reference.md](reference.md)。
