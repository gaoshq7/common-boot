---
name: galaxy-interceptor
description: 编写 Spring MVC HTTP 拦截器、对请求做统一前置/后置/异常处理、按 URL 模式控制拦截范围。当用户写"拦截器"、"interceptor"、"统一鉴权"、"请求日志"、"全局异常返回"、"preHandle / postHandle"相关代码时使用。
---

# Galaxy Interceptor

## 何时使用

下游项目在做以下事情时加载本 SKILL：

- 写一个 HTTP 拦截器（鉴权、日志、签名校验、限流前置等）
- 想统一处理 `/error` 路径，返回自定义错误体
- 需要按路径模式（include / exclude）控制拦截范围
- 需要给多个拦截器排序

## 引入

`io.github.gaoshq7:common-boot:1.0.2`，无额外依赖（`spring-boot-starter-web` 由下游项目提供）。

## 核心做法

**继承 `cn.gsq.common.interceptor.BaseInterceptor`，实现抽象方法 `preHandle(req, resp, HandlerMethod)`，类上标 `@InterceptorPattens`，然后在启动类用 `builder.addInterceptor(...)` 注册（或 yml 配 `interceptor.initPackageName` 包扫描）。**

不要自己写 `WebMvcConfigurer.addInterceptors(...)`——库内 `InterceptorControl` 已经做了。

## 核心 API

| 类 / 注解 | 用途 |
|-----------|------|
| `BaseInterceptor`（抽象类） | 所有拦截器的基类 |
| `@InterceptorPattens(value, exclude, sort)` | 标在拦截器类上，配置拦截规则 |
| `BaseInterceptor#preHandle(req, resp, HandlerMethod)` | **必须实现**：返回 `false` 中断后续处理 |
| `BaseInterceptor#error(HttpServletResponse) → Object` | 可选：`/error` 转发时返回的 body（默认 `{"code":状态码,"msg":"系统异常"}`） |
| `BaseInterceptor#postHandle(...)` / `afterCompletion(...)` | 可选：父类已有默认实现（错误日志），按需重写 |
| `BaseInterceptor.getSession()`（静态） | 当前请求的 `HttpSession` |

`@InterceptorPattens` 三个属性：

- `value`：拦截路径，默认 `{"/**"}`
- `exclude`：排除路径，默认 `{}`
- `sort`：执行顺序，**值越小越先执行**

## 典型用法

```java
package com.example.interceptor;

import cn.gsq.common.interceptor.BaseInterceptor;
import cn.gsq.common.interceptor.InterceptorPattens;
import org.springframework.web.method.HandlerMethod;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@InterceptorPattens(
        value = {"/api/**"},
        exclude = {"/api/public/**", "/api/login"},
        sort = 10
)
public class AuthInterceptor extends BaseInterceptor {

    @Override
    protected boolean preHandle(HttpServletRequest request,
                                HttpServletResponse response,
                                HandlerMethod handlerMethod) throws Exception {
        String token = request.getHeader("Authorization");
        if (token == null || !verify(token)) {
            response.setStatus(401);
            return false;   // 中断后续处理
        }
        return true;
    }

    /** 可选：自定义 /error 返回体 */
    @Override
    protected Object error(HttpServletResponse response) {
        return new ErrorBody(response.getStatus(), "请求失败，请联系管理员");
    }

    private boolean verify(String token) { /* ... */ return true; }
}
```

启动类注册：

```java
public static void main(String[] args) {
    new GalaxyApplicationBuilder(MyApplication.class)
            .addInterceptor(AuthInterceptor.class)
            .run(args);
}
```

或者通过 yml 配包扫描（多个包用逗号分隔）：

```yaml
interceptor:
  initPackageName: com.example.interceptor,com.example.other
```

## 配置项

| 配置 key | 含义 |
|----------|------|
| `interceptor.initPackageName` | 拦截器自动扫描包路径，多个用 `,` 分隔 |
| `interceptor.resourceHandler` | 静态资源 URL 路径（如 `/static/**`） |
| `interceptor.resourceLocation` | 静态资源文件路径（如 `classpath:/static/`） |

## 注意事项

- **`preHandle` 是抽象方法，必须实现**。不要重写父类那个 4 参数的 `preHandle(req, resp, Object handler)`——它已经被 `final` 化的逻辑包了一层（处理 `/error` 转发、`favicon.ico`、`BaseCallbackController` 缓存）。
- **Controller 必须继承 `AbstractController`** 才能在 `preHandle` 之后通过 `currentController` 拿到——见 `galaxy-controller`。
- 系统异常（500）会触发 `/error` 重定向，`error(response)` 在那时被调用；若要在异常时返回自定义结构，重写它。
- `postHandle` 在 controller 抛 500 时**不会执行**；清理动作放 `afterCompletion`。
- 请求日志钩子（servlet 失败请求）：实现 `cn.gsq.common.config.servlet.LogHook`，启动期 `DefaultSystemLog.setHook(myHook)`，会在 `ServletRequestHandledEvent.wasFailure()` 时回调 `servletLog(LogLevel.ERROR, msg)`。
