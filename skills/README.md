# common-boot SKILLS

`common-boot`（`io.github.gaoshq7:common-boot:1.0.2`，Spring Boot 2.7.18）是一个工具箱型公共依赖。本目录下的 SKILL 一份对应一个独立功能点，目的是让 Claude Code 在协助下游项目开发时按需触发加载，告诉 Claude "这个能力库里已经有了，怎么用"。

## SKILL 索引

| SKILL | 触发场景 |
|-------|----------|
| [galaxy-startup](galaxy-startup/) | 编写应用启动 main 方法、自定义 banner、注册启动期扩展 |
| [galaxy-spring-util](galaxy-spring-util/) | 运行时操作 Spring 容器（取/注册/删 Bean、读环境变量、跨模块共享参数） |
| [galaxy-async](galaxy-async/) | 提交异步任务，需要回调或异常处理 |
| [galaxy-interceptor](galaxy-interceptor/) | 编写 HTTP 拦截器、统一前置后置处理 |
| [galaxy-controller](galaxy-controller/) | Controller 中获取请求参数、IP、Header、Cookie、会话 |
| [galaxy-multipart](galaxy-multipart/) | 文件上传，限制大小/后缀/ContentType |
| [galaxy-preload](galaxy-preload/) | 应用启动后执行初始化逻辑 |
| [galaxy-event](galaxy-event/) | 模块间事件解耦通信、订阅 Spring 事件 |

## 分发到下游项目

每份 SKILL 是一个独立目录（含 `SKILL.md`，可能附 `reference.md`）。下游项目把需要的 SKILL 拷到自己的 `.claude/skills/` 即可：

```bash
# 在下游项目根目录执行
mkdir -p .claude/skills
cp -r <common-boot 路径>/skills/galaxy-* .claude/skills/
```

只需你用到的几份也可以单独拷贝（例如不写拦截器的项目可不拷 `galaxy-interceptor`）。

## 版本对应

当前 SKILL 内容对应 `common-boot:1.0.2`。库升级时同步更新本目录。
