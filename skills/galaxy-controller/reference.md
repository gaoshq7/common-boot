# Galaxy Controller — 完整参考

## 类层级

```
BaseCallbackController（提供请求/会话/属性的基础访问）
    ↑ extends
AbstractController（追加参数提取、客户端信息、文件上传）
    ↑ extends
（你的 Controller）
```

## `AbstractController` 完整方法

```java
// 客户端信息
protected String   getIp();
protected String   getRemoteIp();
protected boolean  isAjax();
protected boolean  isGet();
protected boolean  isPost();
protected boolean  isMethod(String method);
protected String   getUserAgent();
protected String   getUserAgent(String def);

// Header / Cookie
protected String   getHeader(String name);
protected String   getHeader(String name, String def);
protected Map<String, String> getHeaders();
protected String   getCookieValue(String name);
protected String   getCookieValue(String name, String def);

// 参数
protected String   getParameter(String name);
protected String   getParameter(String name, String def);
protected String[] getParameters(String name);
protected int      getParameterInt(String name, int def);
protected long     getParameterLong(String name, long def);
protected boolean  getParameterBool(String name, boolean def);
protected Map<String, String[]> getParametersMap();
protected Map<String, String>   getRefererParameter();
protected String   getRequestUrl();
protected String   getRequestUri();
protected <T> T    getObject(Class<T> tClass);
protected <T> T    getObject(Class<T> tClass, boolean ignoreError);

// 文件上传
public static void clearResources();   // 由 BaseInterceptor.afterCompletion 自动调
protected MultipartHttpServletRequest getMultiRequest();
protected boolean  hasFile();
protected MultipartFileBuilder createMultipart();
```

## `BaseCallbackController` 完整方法（继承得到）

```java
public void resetInfo();   // 默认空实现，子类可重写

// 静态工具
public static ServletRequestAttributes getRequestAttributes();      // 非请求线程抛 IllegalStateException
public static ServletRequestAttributes tryGetRequestAttributes();   // 可能 null
public static String getClientIP();
public static Map<String, String> getHeaderMapValues(HttpServletRequest request);  // 同名值逗号拼接

// 实例方法
public HttpServletRequest  getRequest();
public HttpServletResponse getResponse();   // 可能为 null
public HttpSession         getSession();    // 不存在时自动创建
public HttpSession         getSession(boolean create);
public ServletContext      getServletContext();

public Object  getAttribute(String name);
public void    setAttribute(String name, Object object);

public String  getSessionAttribute(String name);          // 不存在返回 null
public Object  getSessionAttributeObj(String name);
public void    setSessionAttribute(String name, Object object);
public void    removeSessionAttribute(String name);
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

```java
1. fileName = multiFile.getOriginalFilename()
   ↓ if blank → throw IllegalArgumentException("fileName:不能获取到文件名")
   ↓ if contains ".." / "/" / "\\" → throw IllegalArgumentException("fileName:非法文件名:...")

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

调 `save()` 等价于 `paths = saves(); return paths[0]`。

`saveAndName()` / `saveAndNames()` 同理：前者有 `checkSaveOne` 前置。

## `MultipartFileConfig`

```java
public class MultipartFileConfig {
    public static void setFileTempPath(String fileTempPath);   // 启动期设置
    public static String getFileTempPath();                     // 默认 hutool UserInfo.getTempDir()
}
```

## 与 `BaseInterceptor` 的协作

```
HTTP 请求来 → DispatcherServlet
  → BaseInterceptor.preHandle(req, resp, Object handler)
      ├─ /error 路径：调 error(response)，写自定义 body，return false
      ├─ /favicon.ico：写 favicon，return false
      └─ 其余：
          ├─ if controller instanceof BaseCallbackController:
          │     currentController.set((BaseCallbackController) controller)   ← ThreadLocal 缓存
          └─ 调用子类抽象 preHandle(req, resp, HandlerMethod)

  → 你的拦截器子类 preHandle(...) 返回 true
      （可在此手动调 reload() —— 触发 currentController.resetInfo()）

  → Controller 方法执行

  → BaseInterceptor.postHandle(...) （状态码 ≥ 400 时打 error 日志）
  → BaseInterceptor.afterCompletion(...)
      ├─ ClientAbortException：warn 日志
      ├─ 其他 ex：error 日志
      ├─ AbstractController.clearResources()   ← 兼容方法（现为空实现，request attribute 自动回收）
      └─ clearResources()                      ← 清 currentController ThreadLocal
```

子类可调 `reload()` 主动触发 `resetInfo()`：

```java
public class MyInterceptor extends BaseInterceptor {
    @Override
    protected boolean preHandle(HttpServletRequest req, HttpServletResponse resp, HandlerMethod handler) {
        // 校验 token...
        reload();   // 触发当前 controller 的 resetInfo()
        return true;
    }
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
