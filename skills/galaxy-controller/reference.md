# Galaxy Controller — 完整参考

## 类层级

```
BaseCallbackController（提供请求/会话/属性的基础访问）
    ↑ extends
AbstractController（追加客户端信息、参数、请求体、Cookie 读写、响应输出、文件上传下载）
    ↑ extends
（你的 Controller）
```

## 类型来源（避免 import 时找错包）

文档中出现的非 JDK 类型，全限定名速查：

| 简称 | 全限定名 | 来源 |
|------|----------|------|
| `HttpServletRequest` / `HttpServletResponse` / `HttpSession` / `Cookie` / `ServletContext` | `javax.servlet.*` | Servlet API（Servlet 3.x/4.x，**非 jakarta**） |
| `HttpHeaders` | `org.springframework.http.HttpHeaders` | Spring |
| `MultipartFile` / `MultipartHttpServletRequest` | `org.springframework.web.multipart.*` | Spring |
| `ServletRequestAttributes` | `org.springframework.web.context.request.ServletRequestAttributes` | Spring |
| `JSONObject` | `cn.hutool.json.JSONObject` | hutool（**不是** Jackson / fastjson） |
| `UserAgent` | `cn.hutool.http.useragent.UserAgent` | hutool |
| `IORuntimeException` | `cn.hutool.core.io.IORuntimeException` | hutool（注意是 hutool 自己的 RuntimeException 包装） |
| `LocalDate` / `LocalDateTime` / `BigDecimal` | `java.time.*` / `java.math.BigDecimal` | JDK 8+ |
| `MultipartFileBuilder` / `MultipartFileConfig` | `cn.gsq.common.controller.multipart.*` | 本库 |

## `AbstractController` 方法（按 13 个区域）

### 1. 客户端信息

```java
protected String   getIp();                       // 优先代理头，可被伪造
protected String   getRemoteIp();                 // TCP 层，不可伪造
protected boolean  isLocalIp();                   // 基于 getRemoteIp 判 127.0.0.0/8、::1
protected boolean  isInternalIp();                // 基于 getRemoteIp 判 RFC1918 + 169.254/16 + 回环
protected static boolean isLocalIp(String ip);
protected static boolean isInternalIp(String ip); // 内部用 hutool NetUtil.isInnerIP；非法格式吞为 false
```

⚠️ 安全决策（如"仅内网可访问"）必须用 `isLocalIp() / isInternalIp()` 这两个实例方法（底层 `getRemoteIp()` 不可伪造），不要用 `getIp()`。

### 2. 请求方法判定

```java
protected String   getRequestMethod();
protected boolean  isGet();
protected boolean  isPost();
protected boolean  isMethod(String method);                   // 不区分大小写
protected void     requireMethod(String... allowed);          // 不匹配抛 IllegalStateException
protected boolean  isAjax();                                   // 仅 X-Requested-With: XMLHttpRequest
```

⚠️ `isAjax()` 不再读 `Accept: application/json`——那会把 Postman/curl/REST 工具误判为 ajax。判断"请求体或期望 JSON"请用 `isJson()`。

### 3. User-Agent

```java
protected String     getUserAgent();              // 不存在返回 null
protected UserAgent  parseUserAgent();            // ⚠️ 无 UA 头或空白时返回 null（hutool 行为），调用前必须判空
protected boolean    isMobile();                  // UA 启发式
```

### 4. Header

```java
protected String   getHeader(String name);                    // 不存在 null
protected String   getHeader(String name, String def);
protected Map<String, String> getHeaders();                   // 不可修改副本，同名 header 用 ", " 拼接
```

### 5. Cookie

```java
// 读
protected String   getCookieValue(String name);               // 不存在 null
protected String   getCookieValue(String name, String def);

// 写（默认 path=/, HttpOnly=true, Secure=false）
protected void setCookie(String name, String value);                                                            // maxAge=-1 会话级
protected void setCookie(String name, String value, int maxAge);
protected void setCookie(String name, String value, int maxAge, String path, boolean httpOnly, boolean secure); // 完全参数

// 删（浏览器要求 name+path 与写入时一致）
protected void removeCookie(String name);                     // path=/
protected void removeCookie(String name, String path);
```

⚠️ 生产环境 HTTPS 下建议显式设 `secure=true`。`maxAge`：`-1`=会话级，`0`=立即过期，`>0`=N 秒。

### 6. 参数获取

```java
// 基础
protected String   getParameter(String name);                      // 不存在 null
protected String   getParameter(String name, String def);          // 空串视为合法值，不替换
protected String[] getParameters(String name);                     // 不存在 null
protected int      getParameterInt(String name, int def);          // 非法值抛 IllegalArgumentException
protected long     getParameterLong(String name, long def);
protected boolean  getParameterBool(String name, boolean def);

// 必填（缺失/空白抛 IllegalArgumentException）
protected String getParameterRequired(String name);
protected int    getParameterIntRequired(String name);
protected long   getParameterLongRequired(String name);

// 类型化（详见 reference-params.md）
protected LocalDate     getParameterDate(String name, LocalDate def);                            // pattern=yyyy-MM-dd
protected LocalDate     getParameterDate(String name, String pattern, LocalDate def);
protected LocalDateTime getParameterDateTime(String name, LocalDateTime def);                    // pattern=yyyy-MM-dd HH:mm:ss
protected LocalDateTime getParameterDateTime(String name, String pattern, LocalDateTime def);
protected <E extends Enum<E>> E getParameterEnum(String name, Class<E> enumClass, E def);        // 按 name() 不区分大小写
protected BigDecimal    getParameterDecimal(String name, BigDecimal def);

// 全量
protected Map<String, String[]> getParametersMap();           // 不可修改深拷贝（值数组也是克隆）
```

### 7. 请求体读取

```java
protected String      getBody();                              // ServletUtil.getBody，仅能消费一次
protected <T> T       getBodyJson(Class<T> cls);              // body 空返回 null
protected JSONObject  getBodyJson();                          // body 空返回 null，非 JSON 对象抛
```

⚠️ 输入流仅能消费一次。若 filter/拦截器/`@RequestBody` 已读过 body，再调本方法会抛 `IORuntimeException`。multipart 请求不要用本方法读 body。

### 8. 表单转 Bean

```java
protected <T> T  getObject(Class<T> tClass);                  // ⚠️ Mass Assignment 风险
protected <T> T  getObject(Class<T> tClass, boolean ignoreError);
```

⚠️ **Mass Assignment**：hutool `ServletUtil.toBean` 按字段名自动绑定，无白名单。**禁止**绑定持久层 entity（攻击者可传 `?isAdmin=true` 提权），必须用专用 DTO。复杂场景（嵌套对象、List 字段）改用 Spring `@RequestBody` + Jackson。

### 9. URL 信息

```java
protected String   getRequestUrl();                           // 完整 URL（反代下为内部地址）
protected String   getRequestUri();                           // 不含 host
protected String   getContextPath();                          // /app 或 ""
protected String   getQueryString();                          // ? 之后，无 query 时 null
protected String   getBaseUrl();                              // scheme://host:port/contextPath，省略 80/443
protected String   getReferer();                              // Referer 头，可被伪造
protected String   getOrigin();                               // Origin 头，CORS 用
```

### 10. Content-Type 判定

```java
protected String   getContentType();
protected boolean  isContentType(String prefix);              // 不区分大小写
protected boolean  isJson();                                  // application/json
protected boolean  isFormUrlEncoded();                        // application/x-www-form-urlencoded
protected boolean  isMultipart();                             // multipart/*
```

### 11. 响应输出（详见 reference-response.md）

```java
protected void writeJson(Object data);                        // application/json
protected void writeText(String text);                        // text/plain
protected void writeHtml(String html);                        // text/html，注意 XSS
protected void redirect(String url);                          // 302
protected void status(int code);                              // 设响应码
protected void sendError(int code, String message);           // 错误页跳转
protected void setNoCache();                                  // 三件套：Cache-Control + Pragma + Expires
```

⚠️ 调用 `writeJson` / `writeText` / `writeHtml` / `redirect` / `sendError` 后 Controller 方法**必须返回 void**，否则 Spring 二次序列化导致响应损坏。

### 12. 文件下载（详见 reference-response.md）

```java
protected void download(File file);                                       // 原文件名
protected void download(File file, String filename);                      // 自动 RFC 5987 中文编码
protected void download(InputStream in, String filename, long contentLength); // 流式，size<0 表示未知
```

调用方提供的 `InputStream` 在方法结束时会被关闭。

### 13. 文件上传（详见 reference-upload.md）

```java
protected MultipartHttpServletRequest getMultiRequest();      // 非 multipart 抛 IllegalStateException
protected boolean              hasFile();                     // 非 multipart 返回 false（异常会 log.warn）
protected MultipartFileBuilder createMultipart();
```

⚠️ `hasFile()` 进入 multipart 分支会触发 Spring 实际解析请求体（生成临时文件），**非零成本探测**。

## `BaseCallbackController` 完整方法（继承得到）

```java
public void resetInfo();   // 默认空实现，专为可选的拦截器钩子 cn.gsq.common.interceptor.BaseInterceptor#reload() 设计；不使用 BaseInterceptor 时此方法不会被调用，可忽略

// 静态工具（可在 service / util 等非 controller 类中直接调用，无需注入）
public static ServletRequestAttributes getRequestAttributes();      // 非请求线程抛 IllegalStateException
public static ServletRequestAttributes tryGetRequestAttributes();   // 同上，但非请求线程返回 null（适合"可选取上下文"的工具代码）
public static String getClientIP();                                  // 优先代理头，可被伪造；非请求线程返回 null
public static Map<String, String> getHeaderMapValues(HttpServletRequest request);  // 同名 header 用 ", " 拼接

// 实例方法
public HttpServletRequest  getRequest();
public HttpServletResponse getResponse();   // 非 web 线程（@Async / 早期 filter）时可能为 null；正常 Controller 调用一定有
public HttpSession         getSession();    // 不存在时自动创建
public HttpSession         getSession(boolean create);
public ServletContext      getServletContext();

public Object  getAttribute(String name);
public void    setAttribute(String name, Object object);

public String  getSessionAttribute(String name);          // ⚠️ 内部 value.toString()，非 String 值会得到 hash 字符串，详见 reference-session.md
public Object  getSessionAttributeObj(String name);       // 复合对象用此
public void    setSessionAttribute(String name, Object object);
public void    removeSessionAttribute(String name);
```

### 静态工具方法的典型使用场景

```java
// 在 service 层拿当前请求的 IP（无需通过 controller 传递）
public class AuditService {
    public void record(String action) {
        String ip = BaseCallbackController.getClientIP();   // 非请求线程返回 null
        // ...
    }
}

// 在工具类中可选地拿请求上下文（不在请求线程也不抛异常）
ServletRequestAttributes attrs = BaseCallbackController.tryGetRequestAttributes();
if (attrs != null) {
    // 有上下文时执行
}
```

## `MultipartFileBuilder` 完整方法

```java
public MultipartFileBuilder(MultipartHttpServletRequest request)

// 链式 setter
public MultipartFileBuilder setMaxSize(long maxSize)           // 默认 10MB，0 = 不限
public MultipartFileBuilder setMaxSize(String maxSize)         // 如 "10MB"、"500KB"
public MultipartFileBuilder setUseOriginalFilename(boolean)
public MultipartFileBuilder addFieldName(String fieldName)
public MultipartFileBuilder resetFieldName(String fieldName)
public MultipartFileBuilder setMultiple(boolean multiple)
public MultipartFileBuilder setFileExt(String... fileExt)              // 空数组 = 不限
public MultipartFileBuilder setInputStreamType(String... inputStreamType) // 空数组 = 不限
public MultipartFileBuilder setContentTypePrefix(String contentTypePrefix)
public MultipartFileBuilder setSavePath(String savePath)

// 保存
public String              save() throws IOException
public String[]            saves() throws IOException
public String[]            saveAndName() throws IOException        // [path, originalFilename]
public List<String[]>      saveAndNames() throws IOException
```

字段名内部用 `LinkedHashSet`，多字段返回顺序与 `addFieldName` 调用顺序一致。

## 校验与保存流程（每个文件）

```java
1. fileName = multiFile.getOriginalFilename()
   ↓ if blank → throw IllegalArgumentException("fileName:不能获取到文件名")
   ↓ if contains ".." / "/" / "\\" → throw IllegalArgumentException("fileName:非法文件名:...")

2. fileSize = multiFile.getSize()
   ↓ if <= 0 → throw IllegalArgumentException("fileSize:文件内容为空")

3. if (fileExt != null && fileExt.length > 0):
       check FileUtil.extName(fileName) ∈ fileExt (ignoreCase)
       ↓ 否则 throw "fileExt:类型错误:..."

4. if (maxSize > 0 && fileSize > maxSize):
       throw "maxSize:too big:..."

5. if (inputStreamType != null && inputStreamType.length > 0):
       fileType = FileTypeUtil.getType(multiFile.getInputStream())
       check fileType ∈ inputStreamType (ignoreCase)
       ↓ 否则 throw "inputStreamType:类型错误:..."

6. localPath = savePath != null ? savePath : MultipartFileConfig.getFileTempPath()

7. 计算 filePath:
   if useOriginalFilename:
       filePath = normalize("{localPath}/{fileName}")
       ↓ if FileUtil.exist(filePath) → throw IllegalArgumentException("fileName:文件已存在:...")
   else:
       saveFileName = UnicodeUtil.toUnicode(fileName).replace("\\", "_")
       filePath = normalize("{localPath}/{ObjectId}_{saveFileName}")

8. FileUtil.writeFromStream(multiFile.getInputStream(), filePath)   // 落盘

9. if (contentTypePrefix != null):
       contentType = Files.probeContentType(Paths.get(filePath))  // 优先基于内容
       if contentType == null:
           contentType = FileUtil.getMimeType(filePath)             // 回退到扩展名
       if contentType == null:
           deleteFileQuietly(filePath)
           throw "contentTypePrefix:获取文件类型失败"
       if !contentType.startsWith(contentTypePrefix):
           deleteFileQuietly(filePath)
           throw "contentTypePrefix:文件类型不正确:..."

10. return new String[]{filePath, fileName}
```

## `save()` 与 `saves()` 区别

`save()` 内部仍然调 `saves()`，但加了前置校验：

```java
private void checkSaveOne() {
    if (fieldNames.size() != 1) throw IllegalArgumentException("fieldNames size:X  use saves");
    if (multiple) throw IllegalArgumentException("multiple use saves");
}
```

调 `save()` 等价于 `paths = saves(); return paths[0]`。`saveAndName()` / `saveAndNames()` 同理：前者有 `checkSaveOne` 前置。

## `MultipartFileConfig`

```java
public class MultipartFileConfig {
    public static void setFileTempPath(String fileTempPath);   // 启动期设置
    public static String getFileTempPath();                     // 默认 hutool UserInfo.getTempDir()（即 java.io.tmpdir）
}
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

## 异步线程注意事项

`@Async`、`CompletableFuture`、`CommonAsyncProcessor` 等异步执行体内**不会**继承 `RequestContextHolder` 的 ThreadLocal——调用 `getRequest()` 会抛 `IllegalStateException`。需要的数据在主请求线程提前取出。

`BaseCallbackController.tryGetRequestAttributes()` 在非请求线程返回 `null` 而非抛异常，可用于"可选使用 request 上下文"的工具代码。

## 常见异常一览

| 场景 | 抛出的异常 |
|------|-----------|
| 非 web 请求线程调 `getRequest()` / `getResponse()` 等 | `IllegalStateException` |
| `getParameterInt/Long/Bool` 等遇非法值 | `IllegalArgumentException` |
| `getParameterRequired/IntRequired/LongRequired` 参数缺失 | `IllegalArgumentException("参数 X 不能为空")` |
| `getParameterDate/DateTime` 格式不匹配 | `IllegalArgumentException` |
| `getParameterEnum` 值不匹配任何枚举常量 | `IllegalArgumentException` |
| `getParameterDecimal` 非法数字 | `IllegalArgumentException` |
| `requireMethod(...)` HTTP 方法不匹配 | `IllegalStateException` |
| `getBody()` 输入流已被消费 / IO 错误 | `IORuntimeException` |
| `getBodyJson()` body 是 JSON 数组或非法 JSON | hutool `JSONException` |
| `writeJson` / `writeText` 等写响应时 IO 错误 | `IORuntimeException` |
| `redirect(url)` / `sendError` 时 IO 错误 | `IORuntimeException` |
| `download(File)` 文件不存在或不是普通文件 | `IllegalArgumentException` |
| `download(File/InputStream/...)` 入参 null | `NullPointerException` |
| `getMultiRequest()` 当前不是 multipart 请求 | `IllegalStateException` |
| `createMultipart()` 当前不是 multipart 请求 | `IllegalStateException`（内部调 `getMultiRequest()`） |
| `MultipartFileBuilder.save/saves/saveAndName*` | 校验失败抛 `IllegalArgumentException`；磁盘写入 IO 错误抛 `IOException`（**checked**，需 `throws`） |
| `MultipartFileBuilder` 字段名为空时调 `saves` | `IllegalArgumentException("fieldNames:empty")` |
| Spring 配置 `max-file-size` 小于业务 `setMaxSize` | `MaxUploadSizeExceededException`（Spring 抛，在 builder 之前） |
