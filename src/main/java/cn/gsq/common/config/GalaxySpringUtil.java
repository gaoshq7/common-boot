package cn.gsq.common.config;

import cn.gsq.common.*;
import cn.gsq.common.config.event.ApplicationEventClient;
import cn.gsq.common.config.event.ApplicationEventLoad;
import cn.gsq.common.config.servlet.LogHook;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.*;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.env.Environment;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.ServletRequestHandledEvent;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Project : galaxy
 * Class : cn.gsq.common.config.GalaxySpringUtil
 *
 * @author : gsq
 * @date : 2022-06-14 11:15
 * @note : It's not technology, it's art !
 **/
@Configuration
public class GalaxySpringUtil implements ApplicationListener, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(GalaxySpringUtil.class);
    private static volatile ApplicationContext context;

    /**
     * @Description : 给静态变量赋值
     * @Param : [applicationContext]
     * @Return : void
     * @Author : gsq
     * @Date : 11:30 上午
     * @note : An art cell !
    **/
    private static synchronized void setApplicationContexts(ApplicationContext applicationContext) {
        GalaxySpringUtil.context = applicationContext;
    }

    /**
     * @Description : 切换上下文
     * @Param : [applicationContext]
     * @Return : void
     * @Author : gsq
     * @Date : 11:20
     * @note : An art cell !
    **/
    public static void updateApplicationContext(ApplicationContext applicationContext) {
        GalaxySpringUtil.setApplicationContexts(applicationContext);
    }

    /**
     * @Description : 引入spring boot上下文
     * @Param : [applicationContext]
     * @Return : void
     * @Author : gsq
     * @Date : 11:29 上午
     * @note : An art cell !
     **/
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        setApplicationContexts(applicationContext);
        Set<ApplicationEventLoad> applicationEventLoads = GalaxyApplicationBuilder.getActiveApplication(GalaxyApplicationBuilder::getApplicationEventLoads);
        if (applicationEventLoads != null) {
            for (ApplicationEventLoad applicationEventLoad : applicationEventLoads) {
                applicationEventLoad.applicationLoad();
            }
        }
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        // 启动失败
        if (event instanceof ApplicationFailedEvent) {
            ApplicationFailedEvent event1 = (ApplicationFailedEvent) event;
            DefaultSystemLog.getLog().error("Galaxy核心启动失败：{}", event1.getException().getMessage(), event1.getException());
            return;
        }
        // 全事件通知触发器
        Set<ApplicationEventClient> applicationEventClients = GalaxyApplicationBuilder.getActiveApplication(GalaxyApplicationBuilder::getApplicationEventClients);
        if (applicationEventClients != null) {
            for (ApplicationEventClient applicationEventClient : applicationEventClients) {
                applicationEventClient.onApplicationEvent(event);
            }
        }
        // 事件选择器通知触发
        EventHandleSelector.handleEvent(event);
        // spring app可用通知触发器
        if (event instanceof ApplicationReadyEvent) {
            CommonInitPackage.init();
            DefaultSystemLog.getLog().info("Galaxy核心启动成功...");
            return;
        }
        // 应用关闭
        if (event instanceof ContextClosedEvent) {
            DefaultSystemLog.getLog().info("Galaxy核心将要关闭...");
            return;
        }
        // 请求异常记录
        if (event instanceof ServletRequestHandledEvent) {
            ServletRequestHandledEvent requestHandledEvent = (ServletRequestHandledEvent) event;
            if (requestHandledEvent.wasFailure()) {
                LogHook logHook = DefaultSystemLog.getHook();
                if (logHook != null)
                    logHook.servletLog(LogLevel.ERROR, requestHandledEvent.getDescription());
//                DefaultSystemLog.getLog().error(
//                        "http请求{}失败：{}",
//                        requestHandledEvent.getRequestUrl(),
//                        requestHandledEvent.getDescription()
//                );
            }
        }
    }

    /**
     * @Description : 获取上下文
     * @Param : []
     * @Return : org.springframework.context.ApplicationContext
     * @Author : gsq
     * @Date : 11:32 上午
     * @note : An art cell !
    **/
    public static ApplicationContext getContext() {
        return GalaxySpringUtil.context;
    }

    /**
     * @Description : 获取运行环境
     * @Param : []
     * @Return : org.springframework.core.env.Environment
     * @Author : gsq
     * @Date : 14:06
     * @note : An art cell !
    **/
    public static Environment getEnvironment() {
        return GalaxyApplicationBuilder.getEnvironment();
    }

    /**
     * @Description : 添加全局环境变量
     * @Param : [key, value]
     * @Return : void
     * @Author : gsq
     * @Date : 14:10
     * @note : An art cell !
    **/
    public static void putGlobalArgument(String key, Object value) {
        if(ObjectUtil.isNotNull(key) && ObjectUtil.isNotNull(value)) {
            GalaxyApplicationBuilder.put(key, value);
        }
    }

    /**
     * @Description : 获取全局环境变量
     * @Param : [key]
     * @Return : java.lang.Object
     * @Author : gsq
     * @Date : 14:13
     * @note : An art cell !
    **/
    public static Object getGlobalArgument(String key) {
        return StrUtil.isNotBlank(key) ? GalaxyApplicationBuilder.get(key) : null;
    }

    /**
     * @Description : 推送事件
     * @Param : [event]
     * @Return : void
     * @Author : gsq
     * @Date : 11:04
     * @note : An art cell !
    **/
    public static void publishEvent(ApplicationEvent event) {
        if (null != context) {
            context.publishEvent(event);
        }
    }

    /**
     * @Description : 推送事件
     * @Param : [event]
     * @Return : void
     * @Author : gsq
     * @Date : 09:09
     * @note : An art cell !
    **/
    public static void publishEvent(Object event) {
        if (null != context) {
            context.publishEvent(event);
        }
    }

    /**
     * @Description : 根据类型获取Bean
     * @Param : [c]
     * @Return : T
     * @Author : gsq
     * @Date : 2:11 下午
     * @note : An art cell !
    **/
    public static <T> T getBean(Class<T> c) {
        T result = null;
        try {
            result = context.getBean(c);
        } catch (NoSuchBeanDefinitionException exception) {
//            log.warn("没有获取到对象实体 ： {}", exception.getBeanType()==null?"类型不详":exception.getBeanType().getName());
        }
        return result;
    }

    /**
     * @Description : 根据实例名和类型获取Bean
     * @Param : [name, clazz]
     * @Return : T
     * @Author : gsq
     * @Date : 2:15 下午
     * @note : An art cell !
    **/
    public static <T> T getBean(String name, Class<T> clazz) {
        T result = null;
        try {
            result = context.getBean(name, clazz);
        } catch (NoSuchBeanDefinitionException exception) {
//            log.warn("没有获取到对象实体 ： {}", exception.getBeanType()==null?"类型不详":exception.getBeanType().getName());
        }
        return result;
    }

    /**
     * @Description : 根据名称获取Bean
     * @Param : [name]
     * @Return : java.lang.Object
     * @Author : gsq
     * @Date : 2:17 下午
     * @note : An art cell !
    **/
    public static <T> T getBean(String name) {
        T result = null;
        try {
            result = (T) context.getBean(name);
        } catch (NoSuchBeanDefinitionException exception){
//            log.warn("没有获取到对象实体 ： {}", exception.getBeanType()==null?"类型不详":exception.getBeanType().getName());
        }
        return result;
    }

    /**
     * @Description : 根据类型获取多个Bean
     * @Param : [clazz]
     * @Return : java.util.List<T>
     * @Author : gsq
     * @Date : 2:18 下午
     * @note : An art cell !
    **/
    public static <T> List<T> getBeans(Class<T> clazz){
        List<T> result = null;
        try {
            Map<String, T> map = context.getBeansOfType(clazz);
            result = map.keySet().stream().map(map::get).collect(Collectors.toList());
        } catch (NoSuchBeanDefinitionException exception){
//            log.warn("没有获取到对象实体 ： {}", exception.getBeanType()==null?"类型不详":exception.getBeanType().getName());
        }
        return result;
    }

    /**
     * @Description : 根据注解获取实例集合
     * @Param : [clazz]
     * @Return : java.util.List<T>
     * @Author : gsq
     * @Date : 2:27 下午
     * @note : ⚠️ 需要保障拥有注解的所有class有统一的父类 "T" !
    **/
    public static <T> Collection<T> getBeanNamesByAnno(Class<? extends Annotation> clazz) {
        Map<String, Object> beanWhithAnnotation = context.getBeansWithAnnotation(clazz);
        Set<Map.Entry<String, Object>> entitySet = beanWhithAnnotation.entrySet();
        Set<T> result = CollUtil.newHashSet();
        for (Map.Entry<String, Object> entry : entitySet) {
            Class<? extends T> aClass = (Class<? extends T>) entry.getValue().getClass();
            CollUtil.addAll(result, getBeans(aClass));
        }
        return result;
    }

    /**
     * @Description : IOC容器动态注入Bean
     * @Param : [beanName, beanClass, constructorArgs]
     * @Return : void
     * @Author : gsq
     * @Date : 3:33 下午
     * @note : An art cell !
    **/
    public static <T> void registerBean(String beanName, Class<T> beanClass, Object ... constructorArgs) {
        if (Objects.isNull(beanClass)) {
            DefaultSystemLog.getLog().debug("beanClass为空，无法注册: {}", beanName);
            return;
        }
        if (!ObjectUtil.isNull(getBean(beanName))) {
            DefaultSystemLog.getLog().debug("bean已经存在，无法注册: {}", beanName);
            return;
        }
        // 构建BeanDefinitionBuilder
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(beanClass);
        // 添加Bean对象构造函数的参数
        Optional.ofNullable(constructorArgs).ifPresent(argArr ->
                Arrays.stream(argArr).forEach(builder::addConstructorArgValue));
        // 从builder中获取到BeanDefinition对象
        BeanDefinition beanDefinition = builder.getBeanDefinition();
        // 获取spring容器中的IOC容器
        DefaultListableBeanFactory factory = (DefaultListableBeanFactory) context.getAutowireCapableBeanFactory();
        // 向IOC容器中注入bean对象
        factory.registerBeanDefinition(beanName, beanDefinition);
    }

    /**
     * @Description : 根据class动态注册bean
     * @Param : [tClass]
     * @Return : T
     * @Author : gsq
     * @Date : 14:24
     * @note : An art cell !
    **/
    public static <T> T registerSingleton(Class<T> tClass) {
        Objects.requireNonNull(tClass);
        // 创建bean
        AutowireCapableBeanFactory autowireCapableBeanFactory = context.getAutowireCapableBeanFactory();
        T obj = autowireCapableBeanFactory.createBean(tClass);
        String beanName = StrUtil.upperFirst(tClass.getSimpleName());
        registerSingleton(beanName, obj);
        return obj;
    }

    /**
     * @Description : 通过名称动态注册bean
     * @Param : [beanName, object]
     * @Return : int
     * @Author : gsq
     * @Date : 14:23
     * @note : An art cell !
    **/
    public static int registerSingleton(String beanName, Object object) {
        // 注册
        ConfigurableApplicationContext configurableApplicationContext = (ConfigurableApplicationContext) context;
        ConfigurableListableBeanFactory configurableListableBeanFactory = configurableApplicationContext.getBeanFactory();
        configurableListableBeanFactory.registerSingleton(beanName, object);
        return configurableListableBeanFactory.getSingletonCount();
    }

    /**
     * @Description : IOC容器动态删除Bean
     * @Param : [name]
     * @Return : void
     * @Author : gsq
     * @Date : 3:35 下午
     * @note : An art cell !
    **/
    public static void removeBeanByName(String name){
        Object o = getBean(name);
        if(ObjectUtil.isNotNull(o)){
            DefaultListableBeanFactory factory = (DefaultListableBeanFactory) context.getAutowireCapableBeanFactory();
            factory.removeBeanDefinition(name);
        }
    }

    /**
     * @Description : 动态加载beans
     * @Param : [basePackage, function]
     * @Return : void
     * @Author : gsq
     * @Date : 4:50 下午
     * @note : An art cell !
     **/
    public static void dynamicLoadPackage(String basePackage, Function<BeanDefinition, String> function) {
        // 创建扫描器并设置过滤器，筛选标注了 @Component 的类
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        // 获取 bean 的注册器
        ConfigurableApplicationContext configurableContext = (ConfigurableApplicationContext) GalaxySpringUtil.context;
        final BeanDefinitionRegistry registry = (BeanDefinitionRegistry) configurableContext.getBeanFactory();
        // 扫描包路径下的类
        scanner.findCandidateComponents(basePackage).forEach(beanDefinition -> {
            try {
                Class<?> clazz = Class.forName(beanDefinition.getBeanClassName());
                BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(clazz);
                registry.registerBeanDefinition(function.apply(beanDefinition), builder.getBeanDefinition());
            } catch (Exception e) {
                log.error("动态注入Bean错误：{}", e.getMessage(), e);
            }
        });
    }

}
