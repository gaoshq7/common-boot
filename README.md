<h1 align="center" style="margin: 30px 0 30px; font-weight: bold;">common-boot</h1>
<h4 align="center">Spring Boot 2 二次封装的API</h4>
<p align="center">
	<a href="https://github.com/gaoshq7/cornerstone/blob/main/LICENSE"><img src="http://img.shields.io/badge/license-apache%202-brightgreen.svg"></a>
</p>

---

## 简介

`common-boot`是一套针对于 Spring boot 2 研发环境下二次封装便于使用的基础环境API。

---

## 特性

- **自定义banner**：支持使用者提供图片作为程序启动banner。
- **半动态加载bean**：支持程序启动时根据变量确定需要加载的beans所在路径。
- **动态加载bean**：支持程序启动后根据变量确定需要加载的beans所在路径。
- **全局环境变量**：支持多模块共享环境变量。
- **上下文环境变量**：支持Spring环境共享变量。
- **函数预加载**：支持程序启动时函数预加载。
- **事件推送接收**：提供完整的事件推送、分类接收机制。
- **事件函数**：支持添加Spring上下文特定事件触发函数。
- **web请求拦截器**：支持添加Spring web请求拦截器。
- **web请求封装函数**：提供Spring web请求二次封装的抽象controller。
- **自定义启动行为**：支持Spring boot程序启动时修改相关启动注解内容。

---

## 使用方法

### 引入依赖

```xml
<dependency>
    <groupId>io.github.gaoshq7</groupId>
    <artifactId>common-boot</artifactId>
    <version>1.0.1</version>
</dependency>
```

### 功能使用

- **<span style="color:blue;">自定义banner</span>**

  在application.yml里面配置banner.msg，并将图片放到resource中
  ```yaml
  banner:
    msg: xxx.jpg
  ```
  ⚠️ <span style="color:red;">图片只支持 ["gif", "jpg", "png"] 三种格式。</span>

___

- **<span style="color:blue;">半动态加载bean</span>**

  ```java
  package io.github.gaoshq7;
  
  import cn.gsq.common.GalaxyApplicationBuilder;
  import org.springframework.boot.ApplicationArguments;
  import org.springframework.boot.autoconfigure.SpringBootApplication;
  
  @SpringBootApplication
  public class DemoApplication {
  
      public static void main(String[] args) {
          GalaxyApplicationBuilder builder = new GalaxyApplicationBuilder(DemoApplication.class);
          builder.addLoadPackage("io.github.gaoshq7");
          builder.run(args);
      }
  
  }
  ```
___

- **<span style="color:blue;">动态加载bean</span>**

  ```java
  package io.github.gaoshq7;

  import cn.gsq.common.config.GalaxySpringUtil;
  import org.springframework.beans.factory.config.BeanDefinition;
  import org.springframework.boot.ApplicationArguments;
  import org.springframework.boot.ApplicationRunner;
  import org.springframework.boot.SpringApplication;
  import org.springframework.boot.autoconfigure.SpringBootApplication;
  
  @SpringBootApplication
  public class DemoApplication implements ApplicationRunner {
  
      public static void main(String[] args) {
          SpringApplication.run(DemoApplication.class, args);
      }
  
      @Override
      public void run(ApplicationArguments args) throws Exception {
          GalaxySpringUtil.dynamicLoadPackage("io.github.gaoshq7", BeanDefinition::getBeanClassName);
      }
  
  }
  ```
___

- **<span style="color:blue;">全局环境变量</span>**

  ```java
  package io.github.gaoshq7;

  import cn.gsq.common.config.GalaxySpringUtil;
  import org.springframework.boot.ApplicationArguments;
  import org.springframework.boot.ApplicationRunner;
  import org.springframework.boot.SpringApplication;
  import org.springframework.boot.autoconfigure.SpringBootApplication;
  
  @SpringBootApplication
  public class DemoApplication implements ApplicationRunner {
  
    public static void main(String[] args) {
      GalaxySpringUtil.putGlobalArgument("key", "大西瓜");
      SpringApplication.run(DemoApplication.class, args);
    }
  
    @Override
    public void run(ApplicationArguments args) {
      GalaxySpringUtil.getGlobalArgument("key");
    }
  
  }
  ```