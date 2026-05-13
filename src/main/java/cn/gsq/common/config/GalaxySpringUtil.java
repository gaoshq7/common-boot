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
import org.springframework.core.env.Profiles;
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
        // 启动期一次性加载事件处理器，避免运行期首个事件触发的并发竞态与级联加载失败
        EventHandleSelector.ensureInit();
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
            // 清理静态 handler 引用，避免容器重启 / DevTools 热重启时调用到旧 context 的 bean
            EventHandleSelector.reset();
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
        ApplicationContext ctx = context;
        if (ctx != null) {
            return ctx.getEnvironment();
        }
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
     * @deprecated 失败时静默返回 null（日志被注释）、context 未就绪时直接 NPE、catch 范围只覆盖 NoSuchBeanDefinitionException 而忽略其他 BeansException。
     * 请改用 {@link #findBean(Class)}（Optional 风格）或 {@link #getRequiredBean(Class)}（缺失即抛 IllegalStateException）。
    **/
    @Deprecated
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
     * @deprecated 同 {@link #getBean(Class)}。请改用 {@link #findBean(String, Class)} 或 {@link #getRequiredBean(String, Class)}。
    **/
    @Deprecated
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
     * @deprecated 隐式 unchecked cast——若返回值类型与实际 bean 类型不一致，调用方接收时 ClassCastException。
     * 请改用 {@link #findBean(String)}（返回 {@code Optional<Object>}）或 {@link #findBean(String, Class)}。
    **/
    @Deprecated
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
     * @deprecated 找不到时返回 {@code null}（违反集合 API 最佳实践，调用方 .stream() 会 NPE）。
     * 请改用 {@link #findBeans(Class)}（找不到返回空 list）或 {@link #getBeansAsMap(Class)}（保留 bean name）。
    **/
    @Deprecated
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
     * @deprecated 命名误导（实际返回实例不返回名）；性能差（N+1 次容器查询）；unchecked cast 易引发 ClassCastException。
     * 真"按注解取实例"请用 {@link #getBeansByAnnotation(Class)}；真"按注解取 bean 名"请用 {@link #getBeanNamesForAnnotation(Class)}。
    **/
    @Deprecated
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
     * @deprecated 用 {@link #getBean(String)} 检查存在性不可靠（创建失败也返回 null）；返回 void 调用方无法区分"成功注册"还是"已存在被跳过"。
     * 请改用 {@link #registerBeanIfAbsent(String, Class, Object...)}（用 containsBeanDefinition 检查，返回 boolean）。
    **/
    @Deprecated
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
     * @deprecated 生成的 bean name 是 {@code StrUtil.upperFirst(...)}，与 Spring 默认的首字母小写约定相反，导致 {@code @Autowired} 找不到（必须用 {@code @Qualifier} 显式引用）。
     * 请改用 {@link #registerSingletonBean(Class)}（用 lowerFirst 命名，对齐 Spring 约定）。
    **/
    @Deprecated
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
     * @deprecated 返回值"全局单例总数"对调用方无意义；context 未就绪时 NPE。
     * 请改用 {@link #putSingleton(String, Object)}（返回 void）。如需让 Spring 自动注入对象的 {@code @Autowired} 字段，请配合 {@link #autowireBean(Object)}。
    **/
    @Deprecated
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
     * @deprecated 仅删除 BeanDefinition，不会清除已实例化的单例；对仅通过 {@code registerSingleton(String, Object)} 注册的纯单例（无 BeanDefinition）会抛 NoSuchBeanDefinitionException。
     * 请改用 {@link #unregisterBean(String)}（同时清 definition + singleton，对纯单例也工作）。
    **/
    @Deprecated
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

    // ============================================================
    // ===== 推荐使用的新 API（null 安全、命名清晰、行为可预测）
    // ============================================================

    // ---------- A. 状态查询 ----------

    /**
     * Spring 上下文是否已就绪（{@code setApplicationContext} 已被调用）。
     * 启动早期（main 方法、static 块）建议先判一下，避免 NPE。
     */
    public static boolean isReady() {
        return context != null;
    }

    /**
     * 获取已就绪的 ApplicationContext，未就绪时抛 IllegalStateException（带清晰提示），不再 NPE。
     * 推荐在所有"必须有容器才能继续"的场景使用。
     */
    public static ApplicationContext getRequiredContext() {
        ApplicationContext ctx = context;
        if (ctx == null) {
            throw new IllegalStateException(
                    "Spring ApplicationContext 尚未就绪——请勿在 setApplicationContext 回调前调用 GalaxySpringUtil 的容器相关方法。"
                            + " 推荐把依赖容器的初始化逻辑放到 @PreLoadMethod 或 ApplicationEventLoad 钩子中。"
            );
        }
        return ctx;
    }

    /**
     * 容器中是否存在指定名字的 bean（含 BeanDefinition 与 纯单例）。context 未就绪返回 false。
     */
    public static boolean containsBean(String name) {
        return context != null && context.containsBean(name);
    }

    /**
     * 容器中是否存在指定名字的 BeanDefinition（**不含**仅通过 putSingleton 注册的纯单例）。
     * context 未就绪或底层不是 BeanDefinitionRegistry 时返回 false。
     */
    public static boolean containsBeanDefinition(String name) {
        if (context == null) {
            return false;
        }
        AutowireCapableBeanFactory bf = context.getAutowireCapableBeanFactory();
        return bf instanceof BeanDefinitionRegistry
                && ((BeanDefinitionRegistry) bf).containsBeanDefinition(name);
    }

    // ---------- B. Bean 查询（Optional / 非 null 集合） ----------

    /**
     * 按类型查找 bean，找不到或 context 未就绪返回 {@link Optional#empty()}。
     * 比旧 {@link #getBean(Class)} 更安全：null 处理显式化。
     */
    public static <T> Optional<T> findBean(Class<T> type) {
        if (context == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(context.getBean(type));
        } catch (BeansException e) {
            log.debug("findBean({}) 失败: {}", type, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 按名字 + 类型查找 bean，找不到或 context 未就绪返回 {@link Optional#empty()}。
     */
    public static <T> Optional<T> findBean(String name, Class<T> type) {
        if (context == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(context.getBean(name, type));
        } catch (BeansException e) {
            log.debug("findBean({}, {}) 失败: {}", name, type, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 按名字查找 bean，返回 {@code Optional<Object>}——调用方需要自己 cast 或用 {@code instanceof} 判断。
     * 比旧 {@link #getBean(String)} 安全：避免隐式 unchecked cast。
     */
    public static Optional<Object> findBean(String name) {
        if (context == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(context.getBean(name));
        } catch (BeansException e) {
            log.debug("findBean({}) 失败: {}", name, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 按类型获取 bean，找不到抛 IllegalStateException（带类型信息）。
     * 适用于"业务上必须存在"的强依赖场景。
     */
    public static <T> T getRequiredBean(Class<T> type) {
        try {
            return getRequiredContext().getBean(type);
        } catch (BeansException e) {
            throw new IllegalStateException("找不到必需的 bean，类型: " + type.getName(), e);
        }
    }

    /**
     * 按名字 + 类型获取 bean，找不到抛 IllegalStateException。
     */
    public static <T> T getRequiredBean(String name, Class<T> type) {
        try {
            return getRequiredContext().getBean(name, type);
        } catch (BeansException e) {
            throw new IllegalStateException("找不到必需的 bean，name=" + name + ", type=" + type.getName(), e);
        }
    }

    /**
     * 按类型获取所有 bean。找不到或 context 未就绪返回空 list（**绝不返回 null**）。
     * 替代旧 {@link #getBeans(Class)}（旧版找不到返回 null）。
     */
    public static <T> List<T> findBeans(Class<T> type) {
        return new ArrayList<>(getBeansAsMap(type).values());
    }

    /**
     * 按类型获取所有 bean，保留 bean name。比 {@link #findBeans(Class)} 多一个 name 维度。
     * 找不到或 context 未就绪返回空 map。
     */
    public static <T> Map<String, T> getBeansAsMap(Class<T> type) {
        if (context == null) {
            return Collections.emptyMap();
        }
        try {
            return context.getBeansOfType(type);
        } catch (BeansException e) {
            log.debug("getBeansAsMap({}) 失败: {}", type, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 按类型获取所有 bean 的名字（**真·返回 names**）。context 未就绪返回空数组。
     */
    public static String[] getBeanNames(Class<?> type) {
        if (context == null) {
            return new String[0];
        }
        return context.getBeanNamesForType(type);
    }

    /**
     * 按注解获取所有 bean 实例。一行 + 一次容器查询，避免旧 {@link #getBeanNamesByAnno} 的 N+1 + unchecked cast 隐患。
     */
    @SuppressWarnings("unchecked")
    public static <T> Collection<T> getBeansByAnnotation(Class<? extends Annotation> annotationType) {
        if (context == null) {
            return Collections.emptyList();
        }
        try {
            return (Collection<T>) context.getBeansWithAnnotation(annotationType).values();
        } catch (BeansException e) {
            log.debug("getBeansByAnnotation({}) 失败: {}", annotationType, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 按类上的注解获取 bean 名字数组（**真·返回 names**）。context 未就绪返回空数组。
     */
    public static String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
        if (context == null) {
            return new String[0];
        }
        return context.getBeanNamesForAnnotation(annotationType);
    }

    /**
     * 是否单例 scope。bean 不存在或 context 未就绪返回 false。
     */
    public static boolean isSingleton(String name) {
        if (context == null) {
            return false;
        }
        try {
            return context.isSingleton(name);
        } catch (NoSuchBeanDefinitionException e) {
            return false;
        }
    }

    /**
     * 是否 prototype scope。bean 不存在或 context 未就绪返回 false。
     */
    public static boolean isPrototype(String name) {
        if (context == null) {
            return false;
        }
        try {
            return context.isPrototype(name);
        } catch (NoSuchBeanDefinitionException e) {
            return false;
        }
    }

    /**
     * 获取 bean 的实际类型。bean 不存在或 context 未就绪返回 null。
     */
    public static Class<?> getBeanType(String name) {
        if (context == null) {
            return null;
        }
        try {
            return context.getType(name);
        } catch (NoSuchBeanDefinitionException e) {
            return null;
        }
    }

    /**
     * 获取 bean 的 BeanDefinition（用于读元数据）。
     * 不存在 / 是纯单例 / context 未就绪 → 返回 null。
     */
    public static BeanDefinition getBeanDefinition(String name) {
        if (context == null) {
            return null;
        }
        AutowireCapableBeanFactory bf = context.getAutowireCapableBeanFactory();
        if (!(bf instanceof BeanDefinitionRegistry)) {
            return null;
        }
        try {
            return ((BeanDefinitionRegistry) bf).getBeanDefinition(name);
        } catch (NoSuchBeanDefinitionException e) {
            return null;
        }
    }

    // ---------- C. 配置访问（Environment 包装） ----------

    /**
     * 读 yml/properties 配置项，未配置返回 null。等价于 {@code getEnvironment().getProperty(key)} 但带 null 安全。
     */
    public static String getProperty(String key) {
        Environment env = getEnvironment();
        return env != null ? env.getProperty(key) : null;
    }

    /**
     * 读配置项，未配置返回 defaultValue。
     */
    public static String getProperty(String key, String defaultValue) {
        Environment env = getEnvironment();
        return env != null ? env.getProperty(key, defaultValue) : defaultValue;
    }

    /**
     * 读配置项并按目标类型转换，未配置返回 null。常用：{@code getProperty("server.port", Integer.class)}。
     */
    public static <T> T getProperty(String key, Class<T> type) {
        Environment env = getEnvironment();
        return env != null ? env.getProperty(key, type) : null;
    }

    /**
     * 读配置项并按目标类型转换，未配置返回 defaultValue。
     */
    public static <T> T getProperty(String key, Class<T> type, T defaultValue) {
        Environment env = getEnvironment();
        return env != null ? env.getProperty(key, type, defaultValue) : defaultValue;
    }

    /**
     * 当前激活的 profiles。Environment 不可用返回空数组。
     */
    public static String[] getActiveProfiles() {
        Environment env = getEnvironment();
        return env != null ? env.getActiveProfiles() : new String[0];
    }

    /**
     * 当前是否激活了指定 profile（支持表达式，如 {@code "dev | test"}）。
     */
    public static boolean acceptsProfile(String profileExpression) {
        Environment env = getEnvironment();
        return env != null && env.acceptsProfiles(Profiles.of(profileExpression));
    }

    // ---------- D. 注入辅助（给非 Spring 创建的对象注入依赖） ----------

    /**
     * 让 Spring 处理一个**已经 new 出来的对象**的 {@code @Autowired} 字段（不会注册成 bean）。
     * 常用：反序列化、第三方框架回调里拿到的对象想用容器里的依赖。
     */
    public static void autowireBean(Object existingObject) {
        Objects.requireNonNull(existingObject, "existingObject");
        getRequiredContext().getAutowireCapableBeanFactory().autowireBean(existingObject);
    }

    /**
     * 走完整的 bean 初始化生命周期（autowire + BeanPostProcessor + InitializingBean.afterPropertiesSet）。
     * 比 {@link #autowireBean(Object)} 完整，但通常不需要除非你也要触发 {@code @PostConstruct} 等。
     */
    @SuppressWarnings("unchecked")
    public static <T> T initializeBean(T existingObject, String beanName) {
        Objects.requireNonNull(existingObject, "existingObject");
        return (T) getRequiredContext().getAutowireCapableBeanFactory()
                .initializeBean(existingObject, beanName);
    }

    // ---------- E. 安全注册 / 删除 ----------

    /**
     * 仅在指定名字的 bean 不存在时注册（按 BeanDefinition 检查，比旧 {@link #registerBean} 的 getBean 检查可靠）。
     * @return true=本次注册成功；false=已存在被跳过 / 入参非法。
     */
    public static <T> boolean registerBeanIfAbsent(String beanName, Class<T> beanClass, Object... constructorArgs) {
        if (StrUtil.isBlank(beanName) || beanClass == null) {
            log.warn("registerBeanIfAbsent 入参非法: beanName={}, beanClass={}", beanName, beanClass);
            return false;
        }
        if (context == null) {
            log.warn("registerBeanIfAbsent 失败: ApplicationContext 未就绪");
            return false;
        }
        AutowireCapableBeanFactory bf = context.getAutowireCapableBeanFactory();
        if (!(bf instanceof DefaultListableBeanFactory)) {
            log.warn("registerBeanIfAbsent 失败: BeanFactory 不是 DefaultListableBeanFactory");
            return false;
        }
        DefaultListableBeanFactory factory = (DefaultListableBeanFactory) bf;
        if (factory.containsBeanDefinition(beanName) || factory.containsSingleton(beanName)) {
            return false;
        }
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(beanClass);
        if (constructorArgs != null) {
            for (Object arg : constructorArgs) {
                builder.addConstructorArgValue(arg);
            }
        }
        factory.registerBeanDefinition(beanName, builder.getBeanDefinition());
        return true;
    }

    /**
     * 用 Spring {@code AutowireCapableBeanFactory.createBean} 创建 + 注册单例。
     * Bean name 用 {@code lowerFirst(SimpleName)}（如 {@code MyService → "myService"}），与 Spring 默认 {@code @Component} 命名一致，
     * 方便下游用 {@code @Autowired} 直接注入。
     */
    public static <T> T registerSingletonBean(Class<T> clazz) {
        Objects.requireNonNull(clazz, "clazz");
        AutowireCapableBeanFactory bf = getRequiredContext().getAutowireCapableBeanFactory();
        T obj = bf.createBean(clazz);
        String beanName = StrUtil.lowerFirst(clazz.getSimpleName());
        putSingleton(beanName, obj);
        return obj;
    }

    /**
     * 把一个**已存在的对象**注册为容器里的单例。**不会**自动注入 {@code @Autowired} 字段——若需要，先 {@link #autowireBean(Object)}。
     * 比旧 {@link #registerSingleton(String, Object)} 更直观（无意义的 int 返回值变成 void）。
     */
    public static void putSingleton(String beanName, Object object) {
        Objects.requireNonNull(beanName, "beanName");
        Objects.requireNonNull(object, "object");
        ConfigurableApplicationContext ctx = (ConfigurableApplicationContext) getRequiredContext();
        ctx.getBeanFactory().registerSingleton(beanName, object);
    }

    /**
     * 完整移除 bean：同时清除 BeanDefinition 与 已实例化的单例。
     * 对仅通过 {@link #putSingleton} 注册的纯单例也工作。
     * @return true=至少清除了一项；false=不存在或 context 未就绪。
     */
    public static boolean unregisterBean(String name) {
        if (StrUtil.isBlank(name) || context == null) {
            return false;
        }
        AutowireCapableBeanFactory bf = context.getAutowireCapableBeanFactory();
        if (!(bf instanceof DefaultListableBeanFactory)) {
            return false;
        }
        DefaultListableBeanFactory factory = (DefaultListableBeanFactory) bf;
        boolean removed = false;
        if (factory.containsBeanDefinition(name)) {
            factory.removeBeanDefinition(name);
            removed = true;
        }
        if (factory.containsSingleton(name)) {
            factory.destroySingleton(name);
            removed = true;
        }
        return removed;
    }

    // ---------- F. 事件 ----------

    /**
     * 发布事件，返回是否成功。context 未就绪时打 warn 日志并返回 false（不像旧 {@link #publishEvent(Object)} 静默丢失）。
     */
    public static boolean tryPublishEvent(Object event) {
        if (context == null) {
            log.warn("无法发布事件 [{}]: ApplicationContext 未就绪", event);
            return false;
        }
        context.publishEvent(event);
        return true;
    }

}
