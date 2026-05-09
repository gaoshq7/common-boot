# Galaxy Multipart — 完整参考

## 类关系

```
AbstractController                                           ← 你的 Controller 继承
  ↓ createMultipart()
MultipartFileBuilder(MultipartHttpServletRequest request)    ← 链式 API
  ↑ 内部调用
MultipartFileConfig.getFileTempPath()                        ← 全局默认保存路径
```

## `MultipartFileBuilder` 完整字段

```java
private MultipartHttpServletRequest multipartHttpServletRequest;
private long maxSize = 0;                    // 字节大小上限，0 = 不限
private Set<String> fieldNames = new HashSet<>();  // 表单字段名集合
private boolean multiple;                    // 单字段多文件开关
private String[] fileExt;                    // 后缀白名单，null = 不限
private String contentTypePrefix;            // MIME 前缀（如 "image"），null = 不限
private String[] inputStreamType;            // 字节流嗅探类型白名单，null = 不限
private String savePath;                     // 保存路径，null = 走 MultipartFileConfig.getFileTempPath()
private boolean useOriginalFilename;         // 是否用原文件名保存
```

## 完整方法签名

```java
public MultipartFileBuilder(MultipartHttpServletRequest request)

// 链式 setter
public MultipartFileBuilder setMaxSize(long maxSize)
public MultipartFileBuilder setMaxSize(String maxSize)         // logback FileSize 解析
public MultipartFileBuilder setUseOriginalFilename(boolean)
public MultipartFileBuilder addFieldName(String fieldName)
public MultipartFileBuilder resetFieldName(String fieldName)
public MultipartFileBuilder setMultiple(boolean multiple)
public MultipartFileBuilder setFileExt(String... fileExt)
public MultipartFileBuilder setInputStreamType(String... inputStreamType)
public MultipartFileBuilder setContentTypePrefix(String contentTypePrefix)
public MultipartFileBuilder setSavePath(String savePath)

// 保存
public String              save() throws IOException
public String[]            saves() throws IOException
public String[]            saveAndName() throws IOException     // [path, originalFilename]
public List<String[]>      saveAndNames() throws IOException
```

## 校验与保存流程（每个文件）

源码顺序如下：

```java
1. fileName = multiFile.getOriginalFilename()
   ↓ if blank → throw IllegalArgumentException("fileName:不能获取到文件名")

2. fileSize = multiFile.getSize()
   ↓ if <= 0 → throw IllegalArgumentException("fileSize:文件内容为空")

3. if (fileExt != null):
       check FileUtil.extName(fileName) ∈ fileExt (ignoreCase)
       ↓ 否则 throw "fileExt:类型错误:..."

4. if (maxSize > 0 && fileSize > maxSize):
       throw "maxSize:too big:..."

5. if (inputStreamType != null):
       fileType = FileTypeUtil.getType(multiFile.getInputStream())
       check fileType ∈ inputStreamType (ignoreCase)
       ↓ 否则 throw "inputStreamType:类型错误:..."

6. localPath = savePath != null ? savePath : MultipartFileConfig.getFileTempPath()

7. 计算 filePath:
   if useOriginalFilename:
       filePath = normalize("{localPath}/{fileName}")
   else:
       saveFileName = UnicodeUtil.toUnicode(fileName).replace("\\", "_")
       filePath = normalize("{localPath}/{ObjectId}_{saveFileName}")

8. FileUtil.writeFromStream(multiFile.getInputStream(), filePath)   // 落盘

9. if (contentTypePrefix != null):
       contentType = FileUtil.getMimeType(filePath)
       if contentType == null:
           FileUtil.del(filePath)
           throw "contentTypePrefix:获取文件类型失败"
       if !contentType.startsWith(contentTypePrefix):
           FileUtil.del(filePath)
           throw "contentTypePrefix:文件类型不正确:..."

10. return new String[]{filePath, fileName}
```

**关键点**：MIME 校验是**保存后**做，失败再删——这意味着失败时短暂有文件落盘。预防穿透改用 `setInputStreamType`（在第 5 步、保存前嗅探）。

## `save()` 与 `saves()` 区别

`save()` 内部仍然调 `saves()`，但加了前置校验：

```java
private void checkSaveOne() {
    if (fieldNames.size() != 1) throw new IllegalArgumentException("fieldNames size:X  use saves");
    if (multiple) throw new IllegalArgumentException("multiple use saves");
}
```

调 `save()` 等价于 `paths = saves(); return paths[0]`。

`saveAndName()` / `saveAndNames()` 同理：前者有 `checkSaveOne` 前置。

## `saves()` 流程

对每个 `fieldName`：

- 若 `multiple == true`：`request.getFiles(fieldName)` 拿到 `List<MultipartFile>`，逐个保存
- 否则：`request.getFile(fieldName)` 拿单个，保存一次

返回 `String[]`（按 fieldName 顺序，多文件展开后填充）。

## `MultipartFileConfig`

```java
public class MultipartFileConfig {
    private static String fileTempPath;
    private static final UserInfo USER_INFO = new UserInfo();

    public static void setFileTempPath(String fileTempPath);
    public static String getFileTempPath();   // 未设置则 fallback USER_INFO.getTempDir()
}
```

**线程安全注意**：`fileTempPath` 是普通 `static` 字段，不是 `volatile`——启动期设置一次（`@PreLoadClass` 或 `main`）即可，不要在并发请求里改。

## `AbstractController` 文件相关方法

```java
// 线程缓存：避免同一请求内反复 wrap
private static final ThreadLocal<MultipartHttpServletRequest> THREAD_LOCAL_MULTIPART_HTTP_SERVLET_REQUEST;

public static void clearResources();  // 在 BaseInterceptor.afterCompletion 自动被调

protected MultipartHttpServletRequest getMultiRequest();
// 1. request 已是 MultipartHttpServletRequest → 直接返回
// 2. 是 multipart 请求但不是 wrapper → new StandardMultipartHttpServletRequest 包一层并缓存
// 3. 不是 multipart → throw IllegalArgumentException("此次访问没有对应的 MultipartHttpServletRequest ...")

protected boolean hasFile();          // getFileMap().size() > 0
protected MultipartFileBuilder createMultipart();   // new MultipartFileBuilder(getMultiRequest())
```

## Spring 配置（必须，库不替你做）

```yaml
spring:
  servlet:
    multipart:
      enabled: true
      max-file-size: 100MB
      max-request-size: 200MB
```

如果 `max-file-size` 比 `setMaxSize` 小，Spring 会先拒绝（在到达 builder 之前），抛 `MaxUploadSizeExceededException`。
