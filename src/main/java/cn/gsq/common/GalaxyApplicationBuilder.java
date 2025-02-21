package cn.gsq.common;

import cn.gsq.common.config.event.ApplicationEventClient;
import cn.gsq.common.config.event.ApplicationEventLoad;
import cn.gsq.common.interceptor.BaseInterceptor;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.*;
import cn.hutool.extra.spring.SpringUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.Banner;
import org.springframework.boot.ImageBanner;
import org.springframework.boot.ResourceBanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Project : galaxy
 * Class : cn.gsq.common.GalaxyApplicationBuilder
 *
 * @author : gsq
 * @date : 2021-04-15 16:39
 * @note : It's not technology, it's art !
 **/
@Slf4j
public class GalaxyApplicationBuilder extends SpringApplicationBuilder {

    private final static ConcurrentHashMap<String, Object> PRESET_PARAMETER = new ConcurrentHashMap<>();    // 全局参数（多模块调用）

    private final static List<GalaxyApplicationBuilder> APPLICATION_BUILDER = new ArrayList<>();

    protected final static List<String> PRE_CLASS_PATHS = new ArrayList<>();     // 预加载类路径

    protected final static List<String> EVENT_HANDLE_PATHS = new ArrayList<>();     // 事件处理器类路径

    private final HashSet<Class> cacheLoadProperties = new HashSet<>();     // 配置类

    @Getter
    protected final Set<AbstractInformationLoader> loaders = new HashSet<>();   // 资源加载器

    @Getter
    private final Set<ApplicationEventLoad> applicationEventLoads = new LinkedHashSet<>();    // WebApplicationContext初始化完成触发函数

    @Getter
    private final Set<ApplicationEventClient> applicationEventClients = new LinkedHashSet<>();

    protected SpringApplication application;

    protected Class<?> applicationClass;

    protected Environment environment;

    @Getter
    private Set<Class<? extends BaseInterceptor>> interceptorClass;

    @Setter
    protected Consumer<Map<String, Object>> commentHandle;  // application注解后续处理函数

    /**
     * @Description : 根据classpath中的文件类型打印相关banner
     * @Param : [sources]
     * @Return : 
     * @Author : gsq
     * @Date : 16:46
     * @note : ⚠️ 默认添加了cn.gsq.common路径下载所有Bean环境 !
    **/
    public GalaxyApplicationBuilder(Class<?>... sources) {
        super(sources);
        this.application = application();
        this.applicationClass = this.application.getMainApplicationClass();
        // 预制banner函数，获取Environment实例
        banner((environment, sourceClass, out) -> {
            // 最早获取配置信息
            GalaxyApplicationBuilder.this.environment = environment;
            String msg = environment.getProperty(CommonPropertiesFinal.BANNER_MSG,
                    "   ████████        ██       ██             ██       ██     ██   ██    ██\n" +
                            "  ██░░░░░░██      ████     ░██            ████     ░░██   ██   ░░██  ██ \n" +
                            " ██      ░░      ██░░██    ░██           ██░░██     ░░██ ██     ░░████  \n" +
                            "░██             ██  ░░██   ░██          ██  ░░██     ░░███       ░░██   \n" +
                            "░██    █████   ██████████  ░██         ██████████     ██░██       ░██   \n" +
                            "░░██  ░░░░██  ░██░░░░░░██  ░██        ░██░░░░░░██    ██ ░░██      ░██   \n" +
                            " ░░████████   ░██     ░██  ░████████  ░██     ░██   ██   ░░██     ░██   \n" +
                            "  ░░░░░░░░    ░░      ░░   ░░░░░░░░   ░░      ░░   ░░     ░░      ░░    ");
            //带路径的可能是banner文件或banner图片
            if (msg.contains("classpath")) {
                String suffixName = msg.substring(msg.indexOf(".")+1);
                for (String s : CommonPropertiesFinal.IMAGE_EXTENSION) {
                    if(s.equals(suffixName)){
                        Banner imageBanner = getImageBanner(msg);
                        if (imageBanner != null) {
                            imageBanner.printBanner(environment, sourceClass, out);
                            return;
                        }
                    }
                }
                Banner textBanner = getTextBanner(msg);
                if (textBanner != null) {
                    textBanner.printBanner(environment, sourceClass, out);
                    return;
                }
            }
            out.println(msg);
        });
        // 使用GalaxyApplicationBuilder就视为开启了辅助功能
        addLoadPackage("cn.gsq.common");
        // 根据规则初始化资源
        Set<Class<?>> templates = ClassUtil.scanPackageBySuper(CommonPropertiesFinal.SCAN_ROOT_PACKAGE, AbstractInformationLoader.class);
        for (Class<?> template : templates) {
            AbstractInformationLoader loader = (AbstractInformationLoader) ReflectUtil.newInstance(template);
            if (!loader.isEnable()) {
                continue;
            }
            List<String> beans = loader.springBeansSupply();
            List<String> args = loader.envArgsSupply();
            List<String> methods = loader.initMethodsSupply();
            List<String> events = loader.eventHandleSupply();
            if (CollUtil.isNotEmpty(beans)) {   // 加载beans包路径
                beans.forEach(this::addLoadPackage);
            }
            if (CollUtil.isNotEmpty(args)) {    // 加载env参数包路径
                args.forEach(this::addLoadProperties);
            }
            if (CollUtil.isNotEmpty(methods)) { // 加载初始化函数包路径
                this.addPreClassPaths(ArrayUtil.toArray(methods, String.class));
            }
            if (CollUtil.isNotEmpty(events)) { // 加载初始化函数包路径
                this.addEventHandlePaths(ArrayUtil.toArray(events, String.class));
            }
            loaders.add(loader);
        }
        APPLICATION_BUILDER.add(this);
    }

    /**
     * @Description : 预加载配置
     * @Param : [packageName]
     * @Return : cn.gsq.common.GalaxyApplicationBuilder
     * @Author : gsq
     * @Date : 17:56
     * @note : An art cell !
    **/
    public GalaxyApplicationBuilder addLoadProperties(String packageName) {
        Set<Class<?>> list = ClassUtil.scanPackageByAnnotation(packageName, AutoPropertiesClass.class);
        for (Class cls : list) {
            if (cacheLoadProperties.contains(cls)) {
                continue;
            }
            Method[] methods = cls.getDeclaredMethods();
            for (Method method : methods) {
                AutoPropertiesMethod autoPropertiesMethod = method.getAnnotation(AutoPropertiesMethod.class);
                if (autoPropertiesMethod == null) continue;
                method.setAccessible(true);
                ParameterizedType parameterizedType = (ParameterizedType) method.getGenericReturnType();
                Type type = parameterizedType.getRawType();
                Class retCls = (Class) type;
                int modifiers = method.getModifiers();
                Type[] parameters = method.getParameterTypes();
                if (parameters.length <= 0 && Map.class == retCls && Modifier.isStatic(modifiers)) {
                    try {
                        Map<String, Object> map = (Map<String, Object>) method.invoke(null);
                        if (map != null) {
                            super.properties(map);
                        }
                    } catch (Exception e) {
                        DefaultSystemLog.getLog().error("配置预加载类{}的{}函数错误:", cls.getName(), method.getName(), e);
                    }
                } else {
                    DefaultSystemLog.getLog().error("配置加载类{}的{}函数不符合规范：无参数、返回值Map、静态!", cls.getName(), method.getName());
                }
            }
            cacheLoadProperties.add(cls);
        }
        return this;
    }

    /**
     * @Description : 加载路径下面的spring boot环境
     * @Param : [packageName]
     * @Return : cn.gsq.common.GalaxyApplicationBuilder
     * @Author : gsq
     * @Date : 16:17
     * @note : An art cell !
    **/
    public GalaxyApplicationBuilder addLoadPackage(String packageName) {
        if (StrUtil.isEmpty(packageName)) {
            throw new IllegalArgumentException("预加载class路径不能为空");
        }
        Object proxy;
        String fliedName;
        ComponentScan componentScan = applicationClass.getAnnotation(ComponentScan.class);
        if (componentScan == null) {
            SpringBootApplication springBootApplication = applicationClass.getAnnotation(SpringBootApplication.class);
            if (springBootApplication == null) {
                throw new IllegalArgumentException("请添加注解： " + SpringBootApplication.class);
            } else {
                proxy = springBootApplication;
                fliedName = "scanBasePackages";
            }
        } else {
            proxy = componentScan;
            fliedName = "value";
        }
        try {
            InvocationHandler invocationHandler = Proxy.getInvocationHandler(proxy);
            Field value = invocationHandler.getClass().getDeclaredField("memberValues");
            value.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> memberValues = (Map<String, Object>) value.get(invocationHandler);
            String[] values = (String[]) memberValues.get(fliedName);
            if(ArrayUtil.isEmpty(values)) {
                log.warn("\"@SpringBootApplication\"中缺少\"scanBasePackages\"属性, 默认扫描路径将被覆盖 ... ");
            }
            String[] newValues = new String[]{packageName};
            newValues = StringUtils.mergeStringArrays(values, newValues);
            memberValues.put(fliedName, newValues);
            // 注解后续处理
            if(ObjectUtil.isNotEmpty(this.commentHandle)) {
                this.commentHandle.accept(memberValues);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.error("加载包路径{}失败：{}", packageName, e.getMessage(), e);
        }
        return this;
    }

    /**
     * @Description : 添加 ApplicationReadyEvent 事件处理函数所在的包路径集合
     * @Param : [paths]
     * @Return : cn.gsq.common.GalaxyApplicationBuilder
     * @Author : gsq
     * @Date : 16:30
     * @note : An art cell !
    **/
    public GalaxyApplicationBuilder addPreClassPaths(String... paths) {
        for (String path : paths) {
            PRE_CLASS_PATHS.add(path);
        }
        return this;
    }

    /**
     * @Description : 添加通用事件处理函数所在的包路径集合
     * @Param : [paths]
     * @Return : cn.gsq.common.GalaxyApplicationBuilder
     * @Author : gsq
     * @Date : 16:42
     * @note : An art cell !
    **/
    public GalaxyApplicationBuilder addEventHandlePaths(String... paths) {
        for (String path : paths) {
            EVENT_HANDLE_PATHS.add(path);
        }
        return this;
    }

    /**
     * @Description : 添加拦截器
     * @Param : [cls]
     * @Return : cn.gsq.common.GalaxyApplicationBuilder
     * @Author : gsq
     * @Date : 16:17
     * @note : An art cell !
    **/
    public GalaxyApplicationBuilder addInterceptor(Class<? extends BaseInterceptor> cls) {
        Objects.requireNonNull(cls);
        if (interceptorClass == null) {
            this.interceptorClass = new LinkedHashSet<>();
        }
        this.interceptorClass.add(cls);
        return this;
    }

    /**
     * @Description : 添加 WebApplicationContext 初始化完成触发函数
     * @Param : [applicationEventLoad]
     * @Return : cn.gsq.common.GalaxyApplicationBuilder
     * @Author : gsq
     * @Date : 11:10
     * @note : An art cell !
    **/
    public GalaxyApplicationBuilder addApplicationEventLoad(ApplicationEventLoad applicationEventLoad) {
        Objects.requireNonNull(applicationEventLoad);
        this.applicationEventLoads.add(applicationEventLoad);
        return this;
    }

    /**
     * @Description : 添加事件监听器
     * @Param : [applicationEventClient]
     * @Return : cn.gsq.common.GalaxyApplicationBuilder
     * @Author : gsq
     * @Date : 11:30
     * @note : An art cell !
    **/
    public GalaxyApplicationBuilder addApplicationEventClient(ApplicationEventClient applicationEventClient) {
        Objects.requireNonNull(applicationEventClient);
        this.applicationEventClients.add(applicationEventClient);
        return this;
    }

    /**
     * @Description : 在GalaxyApplicationBuilder获取元素
     * @Param : [function]
     * @Return : R
     * @Author : gsq
     * @Date : 16:38
     * @note : An art cell !
    **/
    public static <R> R getActiveApplication(Function<GalaxyApplicationBuilder, R> function) {
        if (CollUtil.isEmpty(APPLICATION_BUILDER)) {
            return null;
        }
        GalaxyApplicationBuilder applicationBuilder = GalaxyApplicationBuilder.APPLICATION_BUILDER
                .get(GalaxyApplicationBuilder.APPLICATION_BUILDER.size() - 1);
        return function.apply(applicationBuilder);
    }

    /**
     * @Description : 获取spring boot上下文
     * @Param : []
     * @Return : org.springframework.core.env.Environment
     * @Author : gsq
     * @Date : 16:52
     * @note : An art cell ! 
    **/
    public static Environment getEnvironment() {
        if (CollUtil.isEmpty(APPLICATION_BUILDER)) {
            return SpringUtil.getBean(Environment.class);
        }
        return getActiveApplication(applicationBuilder -> applicationBuilder.environment);
    }

    /**
     * @Description : 添加全局参数
     * @Param : [key, value]
     * @Return : void
     * @Author : gsq
     * @Date : 17:16
     * @note : An art cell !
    **/
    public static void put(String key, Object value) {
        PRESET_PARAMETER.put(key, value);
    }

    /**
     * @Description : 获取全局参数
     * @Param : [key]
     * @Return : java.lang.Object
     * @Author : gsq
     * @Date : 17:17
     * @note : An art cell !
    **/
    public static Object get(String key) {
        return PRESET_PARAMETER.get(key);
    }

    /**
     * @Description : 获取图片banner解析器
     * @Param : [environment, location]
     * @Return : org.springframework.boot.Banner
     * @Author : gsq
     * @Date : 16:40
     * @note : An art cell !
    **/
    private Banner getImageBanner(String location) {
        Resource resource = new DefaultResourceLoader(ClassUtils.getDefaultClassLoader()).getResource(location);
        return resource.exists() ? new ImageBanner(resource) : null;
    }

    /**
     * @Description : 获取文件banner解析器
     * @Param : [environment, location]
     * @Return : org.springframework.boot.Banner
     * @Author : gsq
     * @Date : 16:40
     * @note : An art cell !
    **/
    private Banner getTextBanner(String location) {
        Resource resource = new DefaultResourceLoader(ClassUtils.getDefaultClassLoader()).getResource(location);
        return resource.exists() ? new ResourceBanner(resource) : null;
    }

}
