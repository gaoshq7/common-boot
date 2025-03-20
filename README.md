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
- **装载开关**：提供各种组件是否统一装载开关。
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
    <version>1.0.2</version>
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
  
  @SpringBootApplication(scanBasePackages = "io.github.gaoshq7")
  public class DemoApplication {
  
      public static void main(String[] args) {
          GalaxyApplicationBuilder builder = new GalaxyApplicationBuilder(DemoApplication.class);
          builder.addLoadPackage("xxx.xxx.xxx");
          builder.run(args);
      }
  
  }
  ```
  ⚠️ <span style="color:red;">@SpringBootApplication注解中需要有“scanBasePackages”属性，否则将不会扫描DemoApplication.class所在的类路径。</span>

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
  
  @SpringBootApplication(scanBasePackages = "io.github.gaoshq7")
  public class DemoApplication implements ApplicationRunner {
  
      public static void main(String[] args) {
          GalaxyApplicationBuilder builder = new GalaxyApplicationBuilder(DemoApplication.class);
          builder.run(args);
      }
  
      @Override
      public void run(ApplicationArguments args) throws Exception {
          GalaxySpringUtil.dynamicLoadPackage("xxx.xxx.xxx", BeanDefinition::getBeanClassName);
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
___

- **<span style="color:blue;">上下文环境变量</span>**

  #### 获取已有变量方式

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
          SpringApplication.run(DemoApplication.class, args);
      }
  
      @Override
      public void run(ApplicationArguments args) {
          // 获取Spring boot环境中的变量不需要GalaxyApplicationBuilder启动方式。
          GalaxySpringUtil.getEnvironment().getProperty("spring.application.name");
      }
  
  }
  ```
  #### 添加自定义变量
  
  ```java
  package aaa.bbb.ccc;

  import cn.gsq.common.AutoPropertiesClass;
  import cn.gsq.common.AutoPropertiesMethod;
  import cn.hutool.core.map.MapUtil;
  
  import java.util.Map;
  
  @AutoPropertiesClass
  public class CustomConfig {
  
      @AutoPropertiesMethod
      public static Map<String, String> get1() {
          return MapUtil.of("key1", "大西瓜");
      }
    
      @AutoPropertiesMethod
      public static Map<String, String> get2() {
          return MapUtil.of("key2", "小草莓");
      }
  
  }
  ```
  #### 载入自定义变量方式一：机制载入
  
  1. <span style="color:yellow;">指定预加载环境变量类路径</span>

  ```java
  package cn.galaxy.loader;

  import cn.gsq.common.AbstractInformationLoader;
  import cn.hutool.core.collection.CollUtil;
  
  import java.util.List;
  
  // 该类必须放置在“cn.galaxy.loader”路径下且必须继承“AbstractInformationLoader”。
  public class CustomLoader extends AbstractInformationLoader {
  
      @Override
      public List<String> envArgsSupply() {
          return CollUtil.newArrayList("aaa.bbb.ccc");
      }
  
  }
  ```
  
  2. <span style="color:yellow;">获取环境变量</span>

  ```java
  package io.github.gaoshq7;

  import cn.gsq.common.GalaxyApplicationBuilder;
  import cn.gsq.common.config.GalaxySpringUtil;
  import org.springframework.boot.ApplicationArguments;
  import org.springframework.boot.ApplicationRunner;
  import org.springframework.boot.autoconfigure.SpringBootApplication;
  
  @SpringBootApplication(scanBasePackages = "io.github.gaoshq7")
  public class DemoApplication implements ApplicationRunner {
  
      public static void main(String[] args) {
          GalaxyApplicationBuilder builder = new GalaxyApplicationBuilder(DemoApplication.class);
          builder.run(args);
      }
  
      @Override
      public void run(ApplicationArguments args) {
          GalaxySpringUtil.getEnvironment().getProperty("key1");
          GalaxySpringUtil.getEnvironment().getProperty("key2");
      }
  
  }
  ```
  
  #### 载入变量方式二：代码载入

  ```java
  package io.github.gaoshq7;

  import cn.gsq.common.GalaxyApplicationBuilder;
  import cn.gsq.common.config.GalaxySpringUtil;
  import org.springframework.boot.ApplicationArguments;
  import org.springframework.boot.ApplicationRunner;
  import org.springframework.boot.autoconfigure.SpringBootApplication;
  
  @SpringBootApplication(scanBasePackages = "io.github.gaoshq7")
  public class DemoApplication implements ApplicationRunner {
  
      public static void main(String[] args) {
          GalaxyApplicationBuilder builder = new GalaxyApplicationBuilder(DemoApplication.class);
          builder.addLoadProperties("aaa.bbb.ccc");
          builder.run(args);
      }
  
      @Override
      public void run(ApplicationArguments args) {
          GalaxySpringUtil.getEnvironment().getProperty("key1");
          GalaxySpringUtil.getEnvironment().getProperty("key2");
      }
  
  }
  ```
___

- **<span style="color:blue;">函数预加载</span>**

  #### 添加预加载函数

  ```java
  package aaa.bbb.ccc;

  import cn.gsq.common.PreLoadClass;
  import cn.gsq.common.PreLoadMethod;
  
  // 可以根据序号排列预加载类、函数的顺序。
  @PreLoadClass(1)
  public class PreFunction {
  
      @PreLoadMethod(1)
      private void function01() {
          System.out.println("打开冰箱");
      }
  
      @PreLoadMethod(2)
      private void function02() {
          System.out.println("拿出大象");
      }
  
      @PreLoadMethod(3)
      private void function03() {
          System.out.println("关上冰箱门");
      }
  
  }
  ```
  #### 载入预加载函数方式一：机制载入

  1. <span style="color:yellow;">添加载入路径实例</span>

  ```java
  package cn.galaxy.loader;

  import cn.gsq.common.AbstractInformationLoader;
  import cn.hutool.core.collection.CollUtil;
  
  import java.util.List;
  
  // 该类必须放置在“cn.galaxy.loader”路径下且必须继承“AbstractInformationLoader”。
  public class CustomLoader extends AbstractInformationLoader {
  
      @Override
      public List<String> initMethodsSupply() {
          return CollUtil.newArrayList("aaa.bbb.ccc");
      }
  
  }
  ```

  2. <span style="color:yellow;">启动程序</span>

  ```java
  package io.github.gaoshq7;

  import cn.gsq.common.GalaxyApplicationBuilder;
  import org.springframework.boot.autoconfigure.SpringBootApplication;
  
  @SpringBootApplication(scanBasePackages = "io.github.gaoshq7")
  public class DemoApplication {
  
      public static void main(String[] args) {
          GalaxyApplicationBuilder builder = new GalaxyApplicationBuilder(DemoApplication.class);
          builder.run(args);
      }
  
  }
  ```
  #### 载入预加载函数方式二：代码载入

  ```java
  package io.github.gaoshq7;

  import cn.gsq.common.GalaxyApplicationBuilder;
  import org.springframework.boot.autoconfigure.SpringBootApplication;
  
  @SpringBootApplication(scanBasePackages = "io.github.gaoshq7")
  public class DemoApplication {
  
      public static void main(String[] args) {
          GalaxyApplicationBuilder builder = new GalaxyApplicationBuilder(DemoApplication.class);
          builder.addPreClassPaths("aaa.bbb.ccc");
          builder.run(args);
      }
  
  }
  ```
___

- **<span style="color:blue;">事件推送接收</span>**

  #### 创建自定义事件

  ```java
  package aaa.bbb.ccc;

  import cn.gsq.common.config.event.GalaxyGeneralEvent;
  import lombok.Getter;
  import lombok.Setter;
  
  @Getter
  @Setter
  public class CustomEvent extends GalaxyGeneralEvent {
  
      private String name;
  
      // 事件通过“module”属性分发
      public CustomEvent(String module, Object source, String name) {
          super(module, source);
          this.name = name;
      }
  
  }
  ```
  
  #### 添加事件处理函数

  ```java
  package aaa.bbb.ccc;

  import cn.gsq.common.EventHandleClass;
  import cn.gsq.common.EventHandleMethod;
  import cn.gsq.common.config.event.GalaxyGeneralEvent;
  import org.springframework.context.event.ContextRefreshedEvent;
  
  @EventHandleClass
  public class EventHandle {
  
      // 可以接收到基础类型事件，“module”属性定义与否不影响。
      @EventHandleMethod
      private void handle00(ContextRefreshedEvent event) {
          System.out.println("上下文刷新事件：" + event.getApplicationContext().getId());
      }
  
      // 不能接收到“E1”类型的除“CustomEvent”参数以外任何事件。
      @EventHandleMethod(module = "E1")
      private void handle01(CustomEvent event) {
          System.out.println("handle01处理：" + event.getName());
      }
  
      // 可以且只可以接收“CustomEvent”类型的事件。
      @EventHandleMethod
      private void handle03(CustomEvent event) {
          System.out.println("handle03处理" + event.getName());
      }
  
      // ⚠️ 事件处理顺序不可预测。
  
  }
  ```

  #### 加载事件处理函数方式一：机制载入

  ```java
  package cn.galaxy.loader;

  import cn.gsq.common.AbstractInformationLoader;
  import cn.hutool.core.collection.CollUtil;
  
  import java.util.List;
  
  // 该类必须放置在“cn.galaxy.loader”路径下且必须继承“AbstractInformationLoader”。
  public class CustomLoader extends AbstractInformationLoader {
  
      @Override
      public List<String> eventHandleSupply() {
          return CollUtil.newArrayList("aaa.bbb.ccc");
      }
  
  }
  ```

  #### 加载事件处理函数方式二：代码载入

  ```java
  package io.github.gaoshq7;

  import cn.gsq.common.GalaxyApplicationBuilder;
  import cn.gsq.common.config.GalaxySpringUtil;
  import cn.gsq.common.config.event.GalaxyGeneralEvent;
  import org.springframework.boot.ApplicationArguments;
  import org.springframework.boot.ApplicationRunner;
  import org.springframework.boot.autoconfigure.SpringBootApplication;
  
  @SpringBootApplication(scanBasePackages = "io.github.gaoshq7")
  public class DemoApplication implements ApplicationRunner {
  
      public static void main(String[] args) {
          GalaxyApplicationBuilder builder = new GalaxyApplicationBuilder(DemoApplication.class);
          builder.addEventHandlePaths("aaa.bbb.ccc");
          builder.run(args);
      }
  
      @Override
      public void run(ApplicationArguments args) {
          // 事件推送
          GalaxySpringUtil.publishEvent(new GalaxyGeneralEvent("E1", "大西瓜"));
      }
  
  }
  ```
___

- **<span style="color:blue;">事件函数</span>**

  ```java
  package io.github.gaoshq7;

  import cn.gsq.common.GalaxyApplicationBuilder;
  import org.springframework.boot.autoconfigure.SpringBootApplication;
  
  @SpringBootApplication(scanBasePackages = "io.github.gaoshq7")
  public class DemoApplication {
  
      public static void main(String[] args) {
          GalaxyApplicationBuilder builder = new GalaxyApplicationBuilder(DemoApplication.class);
          builder.addApplicationEventLoad(() -> System.out.println("上下文加载成功。"));
          builder.addApplicationEventClient(event -> System.out.println("监听所有事件。"));
          builder.run(args);
      }
  
  }
  ```
___

- **<span style="color:blue;">装载开关</span>**

  ```java
  package cn.galaxy.loader;

  import cn.gsq.common.AbstractInformationLoader;
  
  // isEnable为false时当前所有类路径全部失效
  public class CustomLoader extends AbstractInformationLoader {
  
      @Override
      public boolean isEnable() {
          return true;
      }
  
  }
  ```