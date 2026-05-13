package cn.gsq.common;

import cn.gsq.common.config.GalaxySpringUtil;
import cn.gsq.common.config.event.GalaxyGeneralEvent;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Project : galaxy
 * Class : cn.gsq.common.EventHandleSelector
 *
 * @author : gsq
 * @date : 2024-05-08 16:58
 * @note : It's not technology, it's art !
 **/
@Slf4j
public class EventHandleSelector {

    private static volatile boolean init = false;   // 是否已经预加载过

    private static final Object INIT_LOCK = new Object();   // 加载/重置串行化锁

    private static final Set<String> PACKAGE_NAME_LIST = ConcurrentHashMap.newKeySet();   // 加载过的包路径集合

    private static final ConcurrentHashMap<Class<?>, Set<Kit>> EVENT_METHODS_MAP = new ConcurrentHashMap<>();  // 事件和处理函数的对应集合

    /**
     * @Description : 全事件处理函数
     * @Param : [event]
     * @Return : void
     * @Author : gsq
     * @Date : 09:59
     * @note : ⚠️ 该函数全事件触发 !
    **/
    public static void handleEvent(ApplicationEvent event) {
        // 启动期事件可能早于显式 ensureInit() 抵达，这里兜底；正常路径下命中 init==true 快速返回
        ensureInit();
        // 过滤掉不关注的事件
        Class<?> eventClass = event.getClass();
        Set<Kit> kits = EVENT_METHODS_MAP.get(eventClass);
        if (CollUtil.isEmpty(kits)) {
            return;
        }
        // 执行事件处理函数
        for (Kit kit : kits) {
            kit.Handle(event);
        }
    }

    /**
     * @Description : 加载所有已注册的事件处理包路径
     * @Param : []
     * @Return : void
     * @Author : gsq
     * @note : ⚠️ 启动期由 GalaxySpringUtil 显式调用一次；运行期 handleEvent 也会兜底调用，重复调用安全 !
    **/
    public static void ensureInit() {
        if (init) {
            return;
        }
        synchronized (INIT_LOCK) {
            if (init) {
                return;
            }
            for (String path : GalaxyApplicationBuilder.EVENT_HANDLE_PATHS) {
                load(path);
            }
            init = true;
        }
    }

    /**
     * @Description : 重置事件处理器状态（清空已加载的 handler 引用与包路径记录）
     * @Param : []
     * @Return : void
     * @Author : gsq
     * @note : ⚠️ 由 GalaxySpringUtil 在 ContextClosedEvent 时调用，避免 static 状态跨容器生命周期残留 !
    **/
    public static void reset() {
        synchronized (INIT_LOCK) {
            PACKAGE_NAME_LIST.clear();
            EVENT_METHODS_MAP.clear();
            init = false;
        }
    }

    /**
     * @Description : 加载事件处理器
     * @Param : [packageName]
     * @Return : void
     * @Author : gsq
     * @Date : 18:00
     * @note : ⚠️ 处理器将注册到spring beans容器中；单个 handler 类加载失败不影响其它类 !
    **/
    private static void load(String packageName) {
        // Set.add 返回 false 表示已存在，天然去重
        if (StrUtil.isBlank(packageName) || !PACKAGE_NAME_LIST.add(packageName)) {
            return;
        }
        Set<Class<?>> templates = ClassUtil.scanPackageByAnnotation(packageName, EventHandleClass.class);
        if (CollUtil.isEmpty(templates)) {
            return;
        }
        for (Class<?> template : templates) {
            try {
                // 注册到bean容器中（lowerFirst 命名，与 Spring 默认 @Component 约定一致）
                Object singleton = GalaxySpringUtil.registerSingletonBean(template);
                // 过滤含有@EventHandleMethod的函数
                Method[] methods = ArrayUtil.filter(
                        template.getDeclaredMethods(),
                        method -> method.getAnnotation(EventHandleMethod.class) != null
                );
                for (Method method : methods) {
                    try {
                        Parameter[] params = method.getParameters();
                        Class<?> type = params[0].getType();
                        EVENT_METHODS_MAP
                                .computeIfAbsent(type, k -> ConcurrentHashMap.newKeySet())
                                .add(new Kit(singleton, method));
                    } catch (Exception e) {
                        log.error("事件函数{}加载失败：{}", method.getName(), e.getMessage(), e);
                    }
                }
            } catch (Exception e) {
                // 单个 handler 类失败兜底：bean 重名、构造器异常、handler 内部反射异常等
                log.error("事件处理类{}加载失败：{}", template.getName(), e.getMessage(), e);
            }
        }
    }

    private static final class Kit {

        private final Object source;    // 执行主体（已存在于bean环境中）

        private final Method method;    // 执行函数

        /**
         * @Description : 构造器
         * @Param : [source, method]
         * @Return :
         * @Author : gsq
         * @Date : 10:28
         * @note : An art cell !
        **/
        private Kit(Object source, Method method) {
            Parameter[] params = method.getParameters();
            // 参数只有一个且为"ApplicationEvent"子类
            if (params.length == 1 && ApplicationEvent.class.isAssignableFrom(params[0].getType())) {
                this.source = source;
                this.method = method;
                this.method.setAccessible(true);
            } else {
                throw new RuntimeException("处理函数不符合规范...");
            }
        }
        
        /**
         * @Description : 事件处理
         * @Param : [event]
         * @Return : void
         * @Author : gsq
         * @Date : 12:43
         * @note : An art cell ! 
        **/
        private void Handle(ApplicationEvent event) {
            try {
                if (GalaxyGeneralEvent.class.isAssignableFrom(event.getClass())) {
                    // "GalaxyGeneralEvent"事件根据module推送（不写则全部推送）
                    EventHandleMethod annotation = this.method.getAnnotation(EventHandleMethod.class);
                    String module = annotation.module();
                    if (StrUtil.isBlank(module) || ((GalaxyGeneralEvent) event).getModule().equals(module)) {
                        this.method.invoke(this.source, event);
                    }
                } else {
                    // 非"GalaxyGeneralEvent"事件直接推送
                    this.method.invoke(this.source, event);
                }
            } catch (InvocationTargetException | IllegalAccessException e) {
                log.error("事件处理器执行失败：{}", e.getMessage(), e);
            }
        }

    }

}
